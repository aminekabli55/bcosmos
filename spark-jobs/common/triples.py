"""Before/after pairing, the fix-triple dataset, and the commit-distance metrics (spec v2 section 6.2).

The event stream already contains everything a triple needs, given one join that is easy to miss. For a commit `C`
with parent `P` the worker publishes two events for a changed file:

* `(commitHash=C, parentHash=P, side=AFTER)` — the file as it is *in* the commit,
* `(commitHash=P, parentHash=null, side=BEFORE)` — the same file as it was *before* it.

So the two sides are paired by `after.parentHash == before.commitHash`, **not** by a shared commit hash: a BEFORE
event names the revision its content came from. `pair_candidates` performs that join once, and every metric in this
module is built on it.

The pair key includes `analyzer_version` and `ruleset_hash`, so a re-analysis with a different analyzer or rule set
never pairs against an older one — those are not comparable revisions of the same measurement.

Two things this module deliberately does *not* pretend to know:

* **Code content.** The event carries the flagged snippet, not the file. A triple therefore pairs the snippet of the
  fixed finding's symbol before the change with the same symbol's snippet after it (empty when the symbol is gone),
  and `snippet_changed` says whether anything is visible in those snippets. Publishing whole files would put source
  code on the broker and into Parquet, which the licence rules of spec v2 section 7.5 argue against.
* **Commit timestamps.** Ordering along the chain comes from the `(child, parent)` edges themselves, via
  `first_parent_depth`, so no clock is trusted and a corpus mined long after the commits were written measures the
  same distances.
"""

from __future__ import annotations

import difflib
from collections import defaultdict

from pyspark.sql import Column, DataFrame, functions as F
from pyspark.sql.types import (
    ArrayType,
    BooleanType,
    IntegerType,
    StringType,
    StructField,
    StructType,
)

#: The pair key: one analysed revision of one file, measured with one analyzer and one rule set.
PAIR_KEY: tuple[str, ...] = (
    "repository_id",
    "file_path",
    "commit_hash",
    "parent_hash",
    "analyzer_version",
    "ruleset_hash",
)

#: What the Python extractor returns per surviving triple; the pair key is added by `extract_triples`.
_TRIPLE_PAYLOAD_SCHEMA = StructType(
    [
        StructField("rule_id", StringType()),
        StructField("fingerprint", StringType()),
        StructField("before_start_line", IntegerType()),
        StructField("before_snippet", StringType()),
        StructField("after_snippet", StringType()),
        StructField("changed_lines", IntegerType()),
        StructField("snippet_changed", BooleanType()),
        StructField("diff", StringType()),
    ]
)

#: The columns of the dataset written to `gold/fix_triples/<dataset_version>`.
TRIPLE_COLUMNS: tuple[str, ...] = (
    "repository_id",
    "file_path",
    "commit_hash",
    "parent_hash",
    "rule_id",
    "fingerprint",
    "before_start_line",
    "before_snippet",
    "after_snippet",
    "changed_lines",
    "snippet_changed",
    "diff",
    "analyzer_version",
    "ruleset_hash",
    "commits_until_resolved",
    "introduction_observed",
)


def snippet_diff(before: str | None, after: str | None) -> tuple[str, int]:
    """Unified diff of two snippets, and how many lines it changes.

    Pure Python, so the rule is testable without a cluster and the dataset card's numbers can be recomputed by hand.
    `after=None` means the symbol is gone from the later revision: every line is a removal.
    """

    before_lines = (before or "").splitlines()
    after_lines = (after or "").splitlines()
    diff_lines = list(difflib.unified_diff(before_lines, after_lines, fromfile="before", tofile="after", lineterm=""))
    changed = sum(1 for line in diff_lines if line[:1] in "+-" and not line.startswith(("+++", "---")))
    return "\n".join(diff_lines), changed


def first_parent_edges(silver: DataFrame) -> DataFrame:
    """`(repository_id, child_hash, parent_hash)` from the AFTER events that name a parent.

    A repository's first commit has no parent, and the worker writes `parentHash = null` for it: those commits are
    roots of the observed forest, not missing data.
    """

    return (
        silver.filter((F.col("side") == "AFTER") & F.col("parent_hash").isNotNull())
        .select(
            F.col("repository_id"),
            F.col("commit_hash").alias("child_hash"),
            F.col("parent_hash"),
        )
        .distinct()
    )


def first_parent_depth(edges: DataFrame, max_iterations: int = 200) -> DataFrame:
    """`(repository_id, commit_hash, depth)`: distance to the root of the observed chain.

    A root — a commit whose parent is not in the corpus, including a repository's first commit — has depth 0. The
    computation is an iterative fixpoint: each round propagates `depth(parent) + 1` to its children until nothing
    changes. Git graphs are acyclic, so it terminates; a cyclic parent relation (corrupt input) keeps raising depths,
    and the iteration cap turns that into a loud failure rather than a wrong number.
    """

    commits = (
        edges.select(F.col("repository_id"), F.col("child_hash").alias("commit_hash"))
        .unionByName(edges.select(F.col("repository_id"), F.col("parent_hash").alias("commit_hash")))
        .distinct()
    )
    depth = commits.withColumn("depth", F.lit(0))

    for _ in range(max_iterations):
        known = depth.select(
            F.col("repository_id").alias("known_repository_id"),
            F.col("commit_hash").alias("known_commit_hash"),
            F.col("depth").alias("known_depth"),
        )
        propagated = (
            edges.join(
                known,
                on=(edges["repository_id"] == known["known_repository_id"])
                & (edges["parent_hash"] == known["known_commit_hash"]),
                how="inner",
            )
            .select(
                F.col("repository_id"),
                F.col("child_hash").alias("commit_hash"),
                (F.col("known_depth") + F.lit(1)).alias("depth"),
            )
        )
        merged = (
            depth.unionByName(propagated).groupBy("repository_id", "commit_hash").agg(F.max("depth").alias("depth"))
        )
        if merged.exceptAll(depth).isEmpty():
            return merged
        depth = merged

    raise RuntimeError(f"first-parent depth did not converge in {max_iterations} iterations: the parent graph is cyclic")


def depth_lookup(depths: DataFrame) -> DataFrame:
    """`first_parent_depth` renamed so it can be joined by name on `depth_repository_id`/`depth_commit_hash`."""

    return depths.select(
        F.col("repository_id").alias("depth_repository_id"),
        F.col("commit_hash").alias("depth_commit_hash"),
        F.col("depth").alias("resolved_depth"),
    )


def pair_candidates(silver: DataFrame) -> DataFrame:
    """One row per pair key with the findings of both revisions.

    A pair is only produced when both revisions parsed: a finding that vanished because the file stopped parsing was
    not fixed (spec v2 section 4.2).
    """

    after = (
        silver.filter((F.col("side") == "AFTER") & F.col("parent_hash").isNotNull())
        .select("repository_id", "file_path", "commit_hash", "parent_hash", "analyzer_version", "ruleset_hash",
                _finding_struct().alias("finding"))
    )
    after_pairs = after.groupBy(*PAIR_KEY).agg(F.collect_list("finding").alias("after_findings"))

    # A BEFORE event names the revision it read (`commitHash = parent`); it is labelled with the pairs that
    # reference it. Both sides descend from `silver`, so both are aliased: a join between two frames that carry the
    # same column identities is refused by Spark ("ambiguous self join") instead of being resolved by guessing.
    before = (
        silver.filter(F.col("side") == "BEFORE")
        .select("repository_id", "file_path", "analyzer_version", "ruleset_hash",
                F.col("commit_hash").alias("revision"), _finding_struct().alias("finding"))
    )
    pair_keys = after_pairs.select(*PAIR_KEY)
    before_labelled = (
        pair_keys.alias("pair")
        .join(
            before.alias("before"),
            on=[
                F.col(f"pair.{name}") == F.col(f"before.{name}")
                for name in ("repository_id", "file_path", "analyzer_version", "ruleset_hash")
            ]
            + [F.col("before.revision") == F.col("pair.parent_hash")],
            how="inner",
        )
        .select(
            F.col("before.repository_id").alias("repository_id"),
            F.col("before.file_path").alias("file_path"),
            F.col("pair.commit_hash").alias("commit_hash"),
            F.col("pair.parent_hash").alias("parent_hash"),
            F.col("before.analyzer_version").alias("analyzer_version"),
            F.col("before.ruleset_hash").alias("ruleset_hash"),
            F.col("before.finding").alias("finding"),
        )
    )
    before_pairs = before_labelled.groupBy(*PAIR_KEY).agg(F.collect_list("finding").alias("before_findings"))

    # Aliased, and every key selected explicitly: `before_pairs` descends from `after_pairs` (the BEFORE rows are
    # labelled with the pairs that reference them), and Spark refuses a join whose two sides carry the same column
    # identities ("ambiguous self join") rather than guessing which copy a name means.
    return (
        after_pairs.alias("after")
        .join(
            before_pairs.alias("before"),
            on=[F.col(f"after.{name}") == F.col(f"before.{name}") for name in PAIR_KEY],
            how="inner",
        )
        .select(*[F.col(f"after.{name}").alias(name) for name in PAIR_KEY], "after.after_findings",
                "before.before_findings")
    )


def extract_triples(pairs: DataFrame, max_changed_lines: int = 30) -> DataFrame:
    """Applies the spec v2 section 6.2 rules to each pair and explodes the surviving triples."""

    extractor = F.udf(
        lambda after_findings, before_findings: _extract_pair(after_findings, before_findings, max_changed_lines),
        ArrayType(_TRIPLE_PAYLOAD_SCHEMA),
    )

    return (
        pairs.withColumn(
            "triple",
            F.explode_outer(extractor(F.col("after_findings"), F.col("before_findings"))),
        )
        .filter(F.col("triple").isNotNull())
        .select(
            *PAIR_KEY,
            F.col("triple.rule_id").alias("rule_id"),
            F.col("triple.fingerprint").alias("fingerprint"),
            F.col("triple.before_start_line").alias("before_start_line"),
            F.col("triple.before_snippet").alias("before_snippet"),
            F.col("triple.after_snippet").alias("after_snippet"),
            F.col("triple.changed_lines").alias("changed_lines"),
            F.col("triple.snippet_changed").alias("snippet_changed"),
            F.col("triple.diff").alias("diff"),
        )
    )


def fix_triples(silver: DataFrame, max_changed_lines: int = 30) -> DataFrame:
    """The dataset rows: each triple with the commit distance that resolved it.

    `commits_until_resolved` is the first-parent distance between the commit where the fingerprint first appears in
    the corpus and the commit where it disappears. `introduction_observed` is false when that first appearance sits at
    the root of the observed window, i.e. when the finding may have existed before the window started and the distance
    is a lower bound. The dataset card reports both counts; nothing here silently drops those rows.
    """

    triples = extract_triples(pair_candidates(silver), max_changed_lines=max_changed_lines)
    depths = first_parent_depth(first_parent_edges(silver))

    resolved = (
        triples.select("repository_id", "commit_hash")
        .distinct()
        .join(
            depth_lookup(depths),
            on=(F.col("repository_id") == F.col("depth_repository_id"))
            & (F.col("commit_hash") == F.col("depth_commit_hash")),
            how="left",
        )
        .select("repository_id", "commit_hash", "resolved_depth")
    )

    introductions = _introduction_depth(silver, depths)
    resolved_for_join = resolved.select(
        F.col("repository_id").alias("resolved_repository_id"),
        F.col("commit_hash").alias("resolved_commit_hash"),
        "resolved_depth",
    )

    return (
        triples.alias("triple")
        .join(
            introductions.alias("introduction"),
            on=[
                F.col("triple.repository_id") == F.col("introduction.repository_id"),
                F.col("triple.file_path") == F.col("introduction.file_path"),
                F.col("triple.rule_id") == F.col("introduction.rule_id"),
                F.col("triple.fingerprint") == F.col("introduction.fingerprint"),
            ],
            how="left",
        )
        .select("triple.*", F.col("introduction.first_seen_depth"))
        .join(
            resolved_for_join,
            on=[
                F.col("repository_id") == F.col("resolved_repository_id"),
                F.col("commit_hash") == F.col("resolved_commit_hash"),
            ],
            how="left",
        )
        .select(
            "repository_id",
            "file_path",
            "commit_hash",
            "parent_hash",
            "rule_id",
            "fingerprint",
            "before_start_line",
            "before_snippet",
            "after_snippet",
            "changed_lines",
            "snippet_changed",
            "diff",
            "analyzer_version",
            "ruleset_hash",
            "first_seen_depth",
            "resolved_depth",
        )
        .withColumn(
            "commits_until_resolved",
            F.when(
                F.col("first_seen_depth").isNotNull() & F.col("resolved_depth").isNotNull(),
                F.greatest(F.lit(0), F.col("resolved_depth") - F.col("first_seen_depth")),
            ),
        )
        .withColumn("introduction_observed", F.coalesce(F.col("first_seen_depth") > F.lit(0), F.lit(False)))
        .select(*TRIPLE_COLUMNS)
    )


def time_to_fix_distribution(silver: DataFrame, max_changed_lines: int = 30) -> DataFrame:
    """`(repository_id, rule_id, commits_until_resolved, resolved_pairs)`: the distribution of section 6.1.

    This is the per-rule histogram of `commits_until_resolved` over the triples. The `unresolved` row is a real row
    with a null distance: it counts the resolved findings whose introduction lies outside the mined window, which is
    the honest counterpart of "the finding was there forever".
    """

    triples = fix_triples(silver, max_changed_lines=max_changed_lines)
    return (
        triples.groupBy("repository_id", "rule_id", "commits_until_resolved")
        .agg(
            F.count(F.lit(1)).alias("resolved_pairs"),
            F.sum(F.when(F.col("introduction_observed"), F.lit(1)).otherwise(F.lit(0))).alias("observed_introductions"),
        )
        .select("repository_id", "rule_id", "commits_until_resolved", "resolved_pairs", "observed_introductions")
    )


def _introduction_depth(silver: DataFrame, depths: DataFrame) -> DataFrame:
    """`(repository_id, file_path, rule_id, fingerprint, first_seen_depth)`: the earliest observation of a finding."""

    appearances = (
        silver.filter((F.col("side") == "AFTER") & F.col("has_finding"))
        .select("repository_id", "file_path", "rule_id", "fingerprint", F.col("commit_hash").alias("seen_commit"))
        .distinct()
    )
    return (
        appearances.alias("appearance")
        .join(
            depth_lookup(depths).alias("depth"),
            on=[
                F.col("appearance.repository_id") == F.col("depth.depth_repository_id"),
                F.col("appearance.seen_commit") == F.col("depth.depth_commit_hash"),
            ],
            how="left",
        )
        .groupBy("appearance.repository_id", "appearance.file_path", "appearance.rule_id",
                 "appearance.fingerprint")
        .agg(F.min(F.col("depth.resolved_depth")).alias("first_seen_depth"))
        # Named explicitly: the grouping columns come from a qualified reference, and the consumers join on these
        # four names.
        .select("repository_id", "file_path", "rule_id", "fingerprint", "first_seen_depth")
    )


def _extract_pair(after_findings, before_findings, max_changed_lines: int):
    """The spec v2 section 6.2 rules for one pair; returns tuples matching `_TRIPLE_PAYLOAD_SCHEMA`."""

    after_all = list(after_findings or [])
    before_all = list(before_findings or [])
    if not after_all or not before_all:
        return []

    # Both revisions must have parsed, and the file must really have changed between them. This is checked before
    # the finding rows are filtered, because `parse_status` is the fact of the whole event: a revision that failed to
    # parse publishes one row with no finding at all, and that row still says FAILED.
    if any(row["parse_status"] != "OK" for row in after_all + before_all):
        return []
    if {row["content_hash"] for row in before_all} == {row["content_hash"] for row in after_all}:
        return []

    # "A file with no finding" arrives as one row whose finding columns are null. It is not a finding, so it must not
    # be counted as one here.
    after_rows = [row for row in after_all if row["fingerprint"] is not None]
    before_rows = [row for row in before_all if row["fingerprint"] is not None]
    if not before_rows:
        return []

    after_fingerprints = {row["fingerprint"] for row in after_rows}
    fingerprints_by_rule_after = defaultdict(set)
    fingerprints_by_rule_before = defaultdict(set)
    for row in after_rows:
        fingerprints_by_rule_after[row["rule_id"]].add(row["fingerprint"])
    for row in before_rows:
        fingerprints_by_rule_before[row["rule_id"]].add(row["fingerprint"])

    snippets_after_by_symbol = {}
    snippets_after_by_rule = defaultdict(list)
    for row in after_rows:
        if row["symbol"] is not None:
            snippets_after_by_symbol.setdefault(row["symbol"], row["snippet"])
        snippets_after_by_rule[row["rule_id"]].append(row["snippet"])

    seen = set()
    triples = []
    for row in before_rows:
        fingerprint = row["fingerprint"]
        rule_id = row["rule_id"]
        if fingerprint in after_fingerprints:
            continue  # still present: not a fix
        if fingerprints_by_rule_after[rule_id] - fingerprints_by_rule_before[rule_id]:
            continue  # a new finding of the same rule appeared: the change traded one problem for another
        if (rule_id, fingerprint) in seen:
            continue
        seen.add((rule_id, fingerprint))

        # "after code" is the same symbol's snippet in the later revision; when the symbol is gone the fix removed
        # it, and the diff is a pure deletion. With no symbol and no unique candidate snippet we keep the triple and
        # say so through `snippet_changed = false` rather than inventing an after side.
        after_snippet = snippets_after_by_symbol.get(row["symbol"]) if row["symbol"] is not None else None
        if after_snippet is None:
            candidates = snippets_after_by_rule.get(rule_id, [])
            after_snippet = candidates[0] if len(candidates) == 1 else None

        diff, changed_lines = snippet_diff(row["snippet"], after_snippet)
        if changed_lines > max_changed_lines:
            continue

        triples.append(
            (
                rule_id,
                fingerprint,
                row["start_line"],
                row["snippet"],
                after_snippet,
                changed_lines,
                changed_lines > 0,
                diff,
            )
        )
    return triples


def _finding_struct() -> Column:
    return F.struct(
        F.col("rule_id").alias("rule_id"),
        F.col("severity").alias("severity"),
        F.col("fingerprint").alias("fingerprint"),
        F.col("symbol").alias("symbol"),
        F.col("start_line").alias("start_line"),
        F.col("snippet").alias("snippet"),
        F.col("content_hash").alias("content_hash"),
        F.col("parse_status").alias("parse_status"),
        F.col("line_count").alias("line_count"),
    )
