import { Chart, ArcElement, BarController, BarElement, CategoryScale, DoughnutController, Filler, Legend, LineController, LineElement, LinearScale, PointElement, Tooltip } from 'chart.js';
import { AnalyticsFinding, Hotspot, RuleStat, Severity, SeverityCount, TimeToFixBucket, TrendPoint } from '../core/api/models';

/**
 * Chart data preparation, kept apart from rendering.
 *
 * Everything in this file is a pure function of the API rows, so the numbers a chart shows are unit-testable without
 * a canvas — and jsdom has no canvas, so a test that had to render would silently assert nothing.
 *
 * `registerCharts()` is the only side effect: Chart.js v4 needs each controller, element and scale registered, and
 * registering them here keeps the component free of that bookkeeping. `chart.js/auto` would do it implicitly at the
 * cost of bundling every chart type the library has.
 */
export function registerCharts(): void {
  Chart.register(
    BarController,
    BarElement,
    LineController,
    LineElement,
    PointElement,
    Filler,
    DoughnutController,
    ArcElement,
    CategoryScale,
    LinearScale,
    Tooltip,
    Legend,
  );
}

/** The severity order of spec v2 section 4.3, most severe first. */
export const SEVERITY_ORDER: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];

/** Theme colours, read once so the charts use the same palette as the rest of the UI. */
export const CHART_COLORS = {
  accent: '#4f46e5',
  accentSoft: 'rgba(79, 70, 229, 0.55)',
  /**
   * The colour of the time-to-fix bar that holds the lower bounds. It is deliberately not a shade of the accent: a
   * bar that is not a measured distance must not look like the measured bars next to it.
   */
  lowerBound: 'rgba(102, 112, 133, 0.45)',
  grid: '#e3e7ef',
  text: '#667085',
  severity: {
    CRITICAL: '#b42318',
    HIGH: '#c8501f',
    MEDIUM: '#96690a',
    LOW: '#15803d',
    INFO: '#475467',
  } as Record<Severity, string>,
};

/**
 * A chart's rows plus the metadata a click needs to turn a bar back into a filter.
 *
 * A value may be `null`, and that is the point: a week whose repository reported no line count has no density, and
 * plotting it as `0` would draw a good-quality week that was never measured. Chart.js leaves a gap for a null point,
 * and the table next to the chart prints "not measured".
 */
export interface ChartSeries<T> {
  labels: string[];
  values: (number | null)[];
  meta: T[];
}

/** The label of a rule bar: the rule, and the repository when more than one is on the chart. */
export function ruleLabel(row: RuleStat, repositoryNames: Map<number, string>, showRepository: boolean): string {
  return showRepository ? `${row.ruleId} · ${repositoryNames.get(row.repositoryId) ?? row.repositoryId}` : row.ruleId;
}

/** Top rules by findings, each with its per-KLOC density for the tooltip. */
export function ruleChartSeries(
  rows: RuleStat[],
  repositoryNames: Map<number, string> = new Map(),
  limit = 12,
): ChartSeries<RuleStat> {
  const sorted = [...rows].sort((left, right) => right.findings - left.findings || left.ruleId.localeCompare(right.ruleId));
  const top = sorted.slice(0, limit);
  const showRepository = new Set(rows.map((row) => row.repositoryId)).size > 1;
  return {
    labels: top.map((row) => ruleLabel(row, repositoryNames, showRepository)),
    values: top.map((row) => row.findings),
    meta: top,
  };
}

/** The findings-per-severity ring, always in severity order and including the empty severities. */
export function severityChartSeries(rows: SeverityCount[]): ChartSeries<SeverityCount> {
  const bySeverity = new Map(rows.map((row) => [row.severity, row]));
  const ordered = SEVERITY_ORDER.map((severity) => bySeverity.get(severity) ?? { severity, findings: 0 });
  return {
    labels: ordered.map((row) => row.severity),
    values: ordered.map((row) => row.findings),
    meta: ordered,
  };
}

/**
 * The quality trend: findings per KLOC over the UTC weeks of the analysis window.
 *
 * The value is the density, not the raw count, because the number of files analysed changes from week to week; the
 * raw count and the parse failures travel in the tooltip and in the drill-down. A null density stays null so the
 * line breaks there instead of showing a week nobody measured.
 */
export function trendChartSeries(rows: TrendPoint[]): ChartSeries<TrendPoint> {
  const sorted = [...rows].sort((left, right) => left.bucketStart.localeCompare(right.bucketStart));
  return {
    labels: sorted.map((row) => row.bucketStart),
    values: sorted.map((row) => row.densityPerKloc),
    meta: sorted,
  };
}

/** The commits-until-resolved histogram: one bar per distance, plus the flagged lower-bound bar last. */
export function timeToFixChartSeries(rows: TimeToFixBucket[], repositoryNames: Map<number, string> = new Map()): ChartSeries<TimeToFixBucket | null> {
  const observed = rows.filter((row) => row.commitsUntilResolved !== null);
  const lowerBound = rows.filter((row) => row.commitsUntilResolved === null);
  const showRepository = new Set(rows.map((row) => row.repositoryId)).size > 1;

  const distances = [...new Set(observed.map((row) => row.commitsUntilResolved as number))].sort((a, b) => a - b);
  const labels = distances.map((distance) => `${distance} commit${distance === 1 ? '' : 's'}`);
  const values = distances.map((distance) =>
    observed.filter((row) => row.commitsUntilResolved === distance).reduce((sum, row) => sum + row.resolvedPairs, 0),
  );
  const meta: (TimeToFixBucket | null)[] = distances.map((distance) => {
    const matching = observed.filter((row) => row.commitsUntilResolved === distance);
    return matching.length === 1 ? matching[0] : null;
  });

  const lowerBoundPairs = lowerBound.reduce((sum, row) => sum + row.resolvedPairs, 0);
  if (lowerBoundPairs > 0) {
    labels.push('already present at the window root');
    values.push(lowerBoundPairs);
    meta.push(
      showRepository || lowerBound.length !== 1
        ? null
        : lowerBound[0],
    );
  }

  return { labels, values, meta };
}

/** One component of a hotspot score, with the numbers the UI must always show (spec v2 section 6.3). */
export interface HotspotComponent {
  key: string;
  label: string;
  raw: string;
  score: number | null;
  contribution: number | null;
  measured: boolean;
  note: string | null;
}

/** Decomposes a hotspot score so the table can show where it came from, component by component. */
export function hotspotComponents(hotspot: Hotspot): HotspotComponent[] {
  return [
    {
      key: 'churn',
      label: 'Churn',
      raw: `${hotspot.churn} commit${hotspot.churn === 1 ? '' : 's'}`,
      score: hotspot.churnScore,
      contribution: hotspot.churnContribution,
      measured: true,
      note: null,
    },
    {
      key: 'complexity',
      label: 'Complexity',
      raw: `${hotspot.complexityProxy} rule${hotspot.complexityProxy === 1 ? '' : 's'}`,
      score: hotspot.complexityScore,
      contribution: hotspot.complexityContribution,
      measured: true,
      note: 'proxy: distinct rules in the file',
    },
    {
      key: 'open_findings',
      label: 'Open findings',
      raw: `${hotspot.openFindings} in the latest revision`,
      score: hotspot.openFindingsScore,
      contribution: hotspot.openFindingsContribution,
      measured: true,
      note: null,
    },
    {
      key: 'dependency',
      label: 'Dependency impact',
      raw: 'not measured',
      score: null,
      contribution: null,
      measured: hotspot.dependencyMeasured,
      note: 'weight 0 until the relationship table of Phase 8 exists',
    },
  ];
}

/** The tooltip lines of the drill-down table, so a record is readable without opening the Quality screen. */
export function findingLocation(finding: AnalyticsFinding): string {
  return finding.startLine === finding.endLine
    ? `${finding.filePath}:${finding.startLine}`
    : `${finding.filePath}:${finding.startLine}-${finding.endLine}`;
}

/** A human label for a pipeline statistic key (`rejected_unsupported_schema` → `Rejected: unsupported schema`). */
export function statLabel(key: string): string {
  const labels: Record<string, string> = {
    silver_rows: 'Rows in silver',
    finding_rows: 'Finding rows',
    rejected_rows: 'Rejected rows',
    parse_failures: 'Files that did not parse',
    commits_observed: 'Commits observed',
    files_observed: 'Distinct file contents',
    repositories: 'Repositories',
    triples: 'Fix triples',
    drilldown_commits: 'Drill-down commit cap',
  };
  if (labels[key]) {
    return labels[key];
  }
  if (key.startsWith('rejected_')) {
    return `Rejected: ${key.slice('rejected_'.length).replaceAll('_', ' ')}`;
  }
  return key.replaceAll('_', ' ');
}
