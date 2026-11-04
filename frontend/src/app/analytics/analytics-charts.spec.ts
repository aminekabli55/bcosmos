import { Hotspot, RuleStat, SeverityCount, TimeToFixBucket, TrendPoint } from '../core/api/models';
import {
  CHART_COLORS,
  hotspotComponents,
  ruleChartSeries,
  severityChartSeries,
  statLabel,
  timeToFixChartSeries,
  trendChartSeries,
} from './analytics-charts';

function ruleStat(overrides: Partial<RuleStat> = {}): RuleStat {
  return {
    repositoryId: 1,
    ruleId: 'EmptyCatchBlock',
    severity: 'MEDIUM',
    findings: 10,
    filesAffected: 4,
    measuredLines: 1200,
    findingsPerKloc: 8.3,
    parseFailures: 0,
    rank: 1,
    ...overrides,
  };
}

function trend(overrides: Partial<TrendPoint> = {}): TrendPoint {
  return {
    repositoryId: 1,
    bucketStart: '2026-01-05',
    findings: 12,
    filesAnalysed: 20,
    measuredLines: 2000,
    densityPerKloc: 6,
    parseFailures: 0,
    ...overrides,
  };
}

function bucket(overrides: Partial<TimeToFixBucket> = {}): TimeToFixBucket {
  return {
    repositoryId: 1,
    ruleId: 'EmptyCatchBlock',
    commitsUntilResolved: 2,
    resolvedPairs: 3,
    observedIntroductions: 3,
    ...overrides,
  };
}

function hotspot(overrides: Partial<Hotspot> = {}): Hotspot {
  return {
    repositoryId: 1,
    filePath: 'src/main/java/com/example/Ingest.java',
    score: 0.71,
    rankInRepository: 1,
    churn: 5,
    churnScore: 0.9,
    churnContribution: 0.315,
    complexityProxy: 3,
    complexityScore: 0.6,
    complexityContribution: 0.15,
    openFindings: 4,
    openFindingsScore: 0.8,
    openFindingsContribution: 0.32,
    dependencyMeasured: false,
    weightSumMeasured: 1,
    latestCommitHash: 'abcdef1234567890',
    weights: 'churn=0.35,complexity=0.25,dependency=0.0,open_findings=0.4',
    ...overrides,
  };
}

describe('analytics chart data', () => {
  it('ranks rule bars by findings and labels the repository only when several are on the chart', () => {
    const rows = [
      ruleStat({ ruleId: 'UnusedPrivateMethod', findings: 2 }),
      ruleStat({ ruleId: 'EmptyCatchBlock', findings: 9 }),
      ruleStat({ ruleId: 'EmptyCatchBlock', findings: 1, repositoryId: 2 }),
    ];

    const single = ruleChartSeries([rows[0], rows[1]], new Map([[1, 'fixture']]));
    expect(single.labels).toEqual(['EmptyCatchBlock', 'UnusedPrivateMethod']);
    expect(single.values).toEqual([9, 2]);
    // One repository on the chart: the bars stay short and readable, because the row is unambiguous.
    expect(single.meta.every((row) => row.repositoryId === 1)).toBe(true);

    const mixed = ruleChartSeries(rows, new Map([[1, 'fixture'], [2, 'other']]));
    expect(mixed.labels).toEqual(['EmptyCatchBlock · fixture', 'UnusedPrivateMethod · fixture', 'EmptyCatchBlock · other']);
  });

  it('keeps every severity in order, including the ones with no findings', () => {
    const counts: SeverityCount[] = [
      { severity: 'HIGH', findings: 4 },
      { severity: 'CRITICAL', findings: 1 },
    ];

    const series = severityChartSeries(counts);

    expect(series.labels).toEqual(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO']);
    expect(series.values).toEqual([1, 4, 0, 0, 0]);
  });

  it('plots the density of each week in time order and keeps an unmeasured week as a gap, not as a zero', () => {
    const series = trendChartSeries([
      trend({ bucketStart: '2026-02-02', densityPerKloc: 3 }),
      trend({ bucketStart: '2026-01-05', densityPerKloc: null }),
    ]);

    expect(series.labels).toEqual(['2026-01-05', '2026-02-02']);
    // A week without a line count has no density. Zero would draw a perfect week that was never measured.
    expect(series.values).toEqual([null, 3]);
    expect(series.meta[0].densityPerKloc).toBeNull();
  });

  it('separates the lower-bound rows from the measured commits-until-resolved bars', () => {
    const series = timeToFixChartSeries([
      bucket({ commitsUntilResolved: 3, resolvedPairs: 2 }),
      bucket({ commitsUntilResolved: 1, resolvedPairs: 5 }),
      bucket({ commitsUntilResolved: null, resolvedPairs: 7 }),
    ]);

    expect(series.labels).toEqual(['1 commit', '3 commits', 'already present at the window root']);
    expect(series.values).toEqual([5, 2, 7]);
    expect(series.meta[2]?.commitsUntilResolved).toBeNull();
  });

  it('marks the dependency component as unmeasured and every other component with its raw value', () => {
    const components = hotspotComponents(hotspot({ churn: 1 }));

    expect(components.map((component) => component.key)).toEqual(['churn', 'complexity', 'open_findings', 'dependency']);
    expect(components[0].raw).toBe('1 commit');
    expect(components[1].note).toBe('proxy: distinct rules in the file');
    expect(components[3].measured).toBe(false);
    expect(components[3].score).toBeNull();
    expect(components[3].raw).toBe('not measured');
  });

  it('names a pipeline statistic in words and falls back to the key with underscores removed', () => {
    expect(statLabel('silver_rows')).toBe('Rows in silver');
    expect(statLabel('rejected_unsupported_schema')).toBe('Rejected: unsupported schema');
    expect(statLabel('something_new')).toBe('something new');
  });

  it('uses a distinct colour for the lower-bound bar and the severity palette for the ring', () => {
    expect(CHART_COLORS.lowerBound).not.toBe(CHART_COLORS.accentSoft);
    expect(CHART_COLORS.severity.CRITICAL).toBe('#b42318');
  });
});
