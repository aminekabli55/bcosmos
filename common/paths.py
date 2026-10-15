"""The medallion layout on disk (spec v2 section 2.3).

Bronze is raw events, silver is deduplicated and validated, gold is the aggregates and the fix-triple dataset. Every
path is built from one root so a job can be pointed at a different warehouse (`BCOSMOS_SPARK_WAREHOUSE`, mounted as
`/opt/bcosmos-data` by `infrastructure/docker-compose.yml`) without touching the layout.
"""

from __future__ import annotations

import os
import re
from pathlib import Path

#: Default warehouse root: the volume the compose file mounts into every Spark container.
DEFAULT_WAREHOUSE = "/opt/bcosmos-data"

#: The layers, in pipeline order. Silver and gold are derived; only bronze is append-only.
LAYERS: tuple[str, ...] = ("bronze", "silver", "gold")

#: A dataset version is what the dataset card and the leaderboard name, so it may not contain a path separator or a
#: space: `load_gold` derives it from the ruleset hash and the run date.
DATASET_VERSION_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$")


def warehouse_root() -> str:
    """The configured warehouse root, without a trailing separator."""

    return os.environ.get("BCOSMOS_SPARK_WAREHOUSE", DEFAULT_WAREHOUSE).rstrip("/")


def layer_path(layer: str, root: str | None = None) -> str:
    """`<root>/<layer>` for one of `LAYERS`."""

    if layer not in LAYERS:
        raise ValueError(f"unknown layer {layer!r}; expected one of {LAYERS}")
    return f"{(root or warehouse_root()).rstrip('/')}/{layer}"


def bronze_path(root: str | None = None) -> str:
    """Raw findings events, partitioned by `(repository_id, event_date)`."""

    return f"{layer_path('bronze', root)}/findings"


def silver_path(root: str | None = None) -> str:
    """Deduplicated, validated findings, partitioned by repository."""

    return f"{layer_path('silver', root)}/findings"


def gold_path(name: str, root: str | None = None) -> str:
    """One gold aggregate, e.g. `gold/rule_distribution`."""

    return f"{layer_path('gold', root)}/{_safe_name(name)}"


def dataset_path(name: str, dataset_version: str, root: str | None = None) -> str:
    """A versioned dataset, e.g. `gold/fix_triples/dataset_version=v20261014-ab12cd34`.

    The version is a directory name, so it is validated rather than trusted: it ends up in the dataset card and on
    every row of the gold tables the backend reads.
    """

    return f"{gold_path(name, root)}/dataset_version={check_dataset_version(dataset_version)}"


def check_dataset_version(dataset_version: str) -> str:
    """Returns `dataset_version` if it is usable as a directory and a column value, raises otherwise."""

    if not DATASET_VERSION_PATTERN.match(dataset_version):
        raise ValueError(f"dataset version {dataset_version!r} must match {DATASET_VERSION_PATTERN.pattern}")
    return dataset_version


def checkpoint_path(stream: str, root: str | None = None) -> str:
    """Checkpoint location of a structured-streaming query; one per query, on a real filesystem."""

    return f"{(root or warehouse_root()).rstrip('/')}/checkpoints/{_safe_name(stream)}"


def as_local_file_path(path: str) -> str:
    """Adds the `file://` scheme when the path has none, which Spark needs to treat it as local.

    A path that already carries a scheme (`file:`, `s3a:`, ...) is returned unchanged.
    """

    if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", path):
        return path
    return Path(path).as_uri() if Path(path).is_absolute() and os.name == "nt" else f"file://{path}"


def _safe_name(name: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9._-]", "_", name)
    if not cleaned or cleaned.startswith("."):
        raise ValueError(f"cannot use {name!r} as a directory name")
    return cleaned
