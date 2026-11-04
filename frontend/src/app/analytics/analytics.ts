import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnInit, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Chart } from 'chart.js';
import { BcosmosApi } from '../core/api/bcosmos-api';
import {
  AnalyticsFilter,
  AnalyticsFinding,
  AnalyticsOverview,
  AnalyticsRepositoryRef,
  DatasetRepository,
  DatasetSplit,
  Hotspot,
  Paged,
  PipelineStat,
  RuleStat,
  Severity,
  SeverityCount,
  TimeToFixBucket,
  TrendPoint,
  formatDensity,
  formatInstant,
  observedMeanDistance,
} from '../core/api/models';
import { problemMessage } from '../core/api/problem';
import {
  CHART_COLORS,
  ChartSeries,
  HotspotComponent,
  findingLocation,
  hotspotComponents,
  registerCharts,
  ruleChartSeries,
  severityChartSeries,
  statLabel,
  timeToFixChartSeries,
  trendChartSeries,
} from './analytics-charts';

/**
 * The Analytics screen (spec v2 sections 6 and 9): the corpus charts built from the Spark gold tables.
 *
 * Three rules shape the design, because a corpus chart is easy to make look authoritative and hard to make honest:
 *
 * 1. **Every number shows its snapshot.** The dataset version and the generation time sit in the header, and the
 *    filters that produced the panels are printed above them.
 * 2. **Every chart drills down to records.** Clicking a rule bar, a severity arc, a trend point or a histogram bar
 *    opens the same records panel with that dimension as a filter; the panel is a table, so a chart is never the only
 *    way to read the data.
 * 3. **Nothing is invented while it is missing.** With no gold load the screen explains how to produce one; a null
 *    density reads "not measured"; the hotspot table states that its complexity component is a proxy and that
 *    dependency impact is not measured yet; the lower-bound time-to-fix bar is labelled and kept out of the average.
 */
@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './analytics.html',
  styleUrl: './analytics.scss',
})
export class Analytics implements OnInit {
  private readonly api = inject(BcosmosApi);

  protected readonly overview = signal<AnalyticsOverview | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly selectedRepositoryId = signal<number | null>(null);
  protected readonly selectedRuleId = signal<string | null>(null);

  protected readonly ruleStats = signal<RuleStat[]>([]);
  protected readonly trends = signal<TrendPoint[]>([]);
  protected readonly timeToFix = signal<TimeToFixBucket[]>([]);
  protected readonly hotspots = signal<Hotspot[]>([]);
  protected readonly aggregatesLoading = signal(false);

  protected readonly splits = signal<DatasetSplit[]>([]);
  protected readonly splitDetail = signal<{ split: string; items: DatasetRepository[] } | null>(null);

  protected readonly drill = signal<DrillDown | null>(null);
  protected readonly drillItems = signal<AnalyticsFinding[]>([]);
  protected readonly drillPageSize = 50;
  protected readonly drillPage = signal<number>(0);
  protected readonly drillTotal = signal(0);
  protected readonly drillHasNext = signal(false);
  protected readonly drillLoading = signal(false);
  protected readonly drillError = signal<string | null>(null);

  protected readonly expandedHotspot = signal<string | null>(null);

  private readonly charts = new Map<string, { destroy(): void }>();
  private readonly ruleCanvas = viewChild<ElementRef<HTMLCanvasElement>>('ruleCanvas');
  private readonly severityCanvas = viewChild<ElementRef<HTMLCanvasElement>>('severityCanvas');
  private readonly trendCanvas = viewChild<ElementRef<HTMLCanvasElement>>('trendCanvas');
  private readonly timeToFixCanvas = viewChild<ElementRef<HTMLCanvasElement>>('timeToFixCanvas');

  protected readonly formatInstant = formatInstant;
  protected readonly formatDensity = formatDensity;
  protected readonly statLabel = statLabel;
  protected readonly findingLocation = findingLocation;
  protected readonly hotspotComponents = hotspotComponents;
  /** `Array.every` needs a predicate, and a template cannot declare a lambda inline. */
  protected readonly isZero = (value: number | null): boolean => value === 0;
  /** The last day of the inclusive week bucket a trend point starts. */
  protected readonly weekEnd = (bucketStart: string): string => addDays(bucketStart, 6);


  /** The repository names the chart labels and the tables share. */
  protected readonly repositoryNames = computed(() => {
    const names = new Map<number, string>();
    for (const repository of this.overview()?.repositoryRefs ?? []) {
      names.set(repository.repositoryId, repository.name);
    }
    return names;
  });

  protected readonly severitySeries = computed<ChartSeries<SeverityCount>>(() =>
    severityChartSeries(this.overview()?.severityMix ?? []),
  );

  protected readonly ruleSeries = computed<ChartSeries<RuleStat>>(() =>
    ruleChartSeries(this.ruleStats(), this.repositoryNames()),
  );

  protected readonly trendSeries = computed<ChartSeries<TrendPoint>>(() => trendChartSeries(this.trends()));

  protected readonly timeToFixSeries = computed<ChartSeries<TimeToFixBucket | null>>(() =>
    timeToFixChartSeries(this.timeToFix(), this.repositoryNames()),
  );

  /** The lower-bound bars are drawn in a different colour: they are not a measured distance. */
  protected readonly timeToFixColors = computed(() =>
    this.timeToFixSeries().meta.map((row) => (row === null ? CHART_COLORS.lowerBound : CHART_COLORS.accentSoft)),
  );

  /**
   * The mean of the observed introductions only. The lower-bound rows would drag it towards zero, so they are
   * excluded here and reported separately as `lowerBoundPairs`.
   */
  protected readonly meanDistance = computed(() => observedMeanDistance(this.timeToFix()));

  protected readonly lowerBoundPairs = computed(() =>
    this.timeToFix()
      .filter((bucket) => bucket.commitsUntilResolved === null)
      .reduce((sum, bucket) => sum + bucket.resolvedPairs, 0),
  );

  /**
   * The mean rendered as text rather than as a nullable number, so a genuine mean of zero is not mistaken for "not
   * measured" by a truthiness check in the template.
   */
  protected readonly meanDistanceText = computed(() => {
    const mean = this.meanDistance();
    return mean === null ? 'not measured' : `${mean.toFixed(2)} commits`;
  });

  protected readonly kpis = computed(() => {
    const current = this.overview();
    if (!current) {
      return [];
    }
    return [
      { label: 'Repositories', value: current.repositories.toLocaleString(), hint: 'with rows in this snapshot' },
      { label: 'Findings', value: current.findings.toLocaleString(), hint: 'across every analysed revision' },
      { label: 'Rules', value: current.rules.toLocaleString(), hint: 'distinct rules that fired' },
      {
        label: 'Measured lines',
        value: current.measuredLines.toLocaleString(),
        hint: `${(current.measuredLines / 1000).toFixed(1)} KLOC of distinct file contents`,
      },
      { label: 'Parse failures', value: current.parseFailures.toLocaleString(), hint: 'files that did not parse' },
      { label: 'Fix triples', value: current.triples.toLocaleString(), hint: 'versioned dataset rows' },
    ];
  });

  protected readonly pipelineStats = computed<PipelineStat[]>(() =>
    [...(this.overview()?.pipelineStats ?? [])].sort((left, right) =>
      statLabel(left.key).localeCompare(statLabel(right.key)),
    ),
  );

  /** The rule choices of the time-to-fix filter, most findings first. */
  protected readonly ruleChoices = computed(() => {
    const seen = new Map<string, number>();
    for (const row of this.ruleStats()) {
      seen.set(row.ruleId, (seen.get(row.ruleId) ?? 0) + row.findings);
    }
    for (const row of this.overview()?.topRules ?? []) {
      seen.set(row.ruleId, seen.get(row.ruleId) ?? row.findings);
    }
    return [...seen.entries()]
      .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
      .map(([ruleId, findings]) => ({ ruleId, findings }));
  });

  protected readonly selectedRepositoryName = computed(() => {
    const repositoryId = this.selectedRepositoryId();
    return repositoryId === null ? 'all repositories' : (this.repositoryNames().get(repositoryId) ?? `#${repositoryId}`);
  });

  constructor() {
    registerCharts();
    // The canvases only exist once the data arrived and Angular rendered the sections, so the redraw is driven by an
    // effect that reads both the data and the canvas refs. Redrawing from a subscription callback would run before
    // change detection and find no canvas.
    effect(() => {
      const overview = this.overview();
      this.ruleStats();
      this.trends();
      this.timeToFix();
      this.ruleCanvas();
      this.severityCanvas();
      this.trendCanvas();
      this.timeToFixCanvas();
      untracked(() => this.renderCharts(overview));
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getAnalyticsOverview().subscribe({
      next: (overview) => {
        this.overview.set(overview);
        this.loading.set(false);
        if (overview.available) {
          this.loadAggregates();
          this.api.listDatasetSplits().subscribe({
            next: (page) => this.splits.set(page.items.length > 0 ? page.items : overview.splits),
          });
        }
      },
      error: (error) => {
        this.error.set(problemMessage(error));
        this.loading.set(false);
      },
    });
  }

  /** Loads the four aggregate panels for the current filter. */
  loadAggregates(): void {
    const repositoryId = this.selectedRepositoryId();
    this.aggregatesLoading.set(true);
    let pending = 4;
    const done = () => {
      pending -= 1;
      if (pending === 0) {
        this.aggregatesLoading.set(false);
      }
    };

    this.api.listRuleStats({ repositoryId, size: 50 }).subscribe({
      next: (page) => {
        this.ruleStats.set(page.items);
        done();
      },
      error: () => done(),
    });
    this.api.listTrends({ repositoryId, size: 100 }).subscribe({
      next: (page) => {
        this.trends.set(page.items);
        done();
      },
      error: () => done(),
    });
    this.api.listTimeToFix({ repositoryId, ruleId: this.selectedRuleId(), size: 50 }).subscribe({
      next: (page) => {
        this.timeToFix.set(page.items);
        done();
      },
      error: () => done(),
    });
    this.api.listHotspots({ repositoryId, size: 10 }).subscribe({
      next: (page) => {
        this.hotspots.set(page.items);
        done();
      },
      error: () => done(),
    });
  }

  selectRepository(value: string): void {
    this.selectedRepositoryId.set(value === '' ? null : Number(value));
    this.selectedRuleId.set(null);
    this.splitDetail.set(null);
    this.expandedHotspot.set(null);
    this.loadAggregates();
  }

  selectRule(value: string): void {
    this.selectedRuleId.set(value === '' ? null : value);
    this.api.listTimeToFix({ repositoryId: this.selectedRepositoryId(), ruleId: this.selectedRuleId(), size: 50 }).subscribe({
      next: (page) => this.timeToFix.set(page.items),
    });
  }

  clearFilters(): void {
    this.selectedRepositoryId.set(null);
    this.selectedRuleId.set(null);
    this.splitDetail.set(null);
    this.loadAggregates();
  }

  toggleHotspot(filePath: string): void {
    this.expandedHotspot.set(this.expandedHotspot() === filePath ? null : filePath);
  }

  /** A hotspot is identified by its repository and path, which is also what the table tracks it by. */
  protected hotspotKey(hotspot: Hotspot): string {
    return `${hotspot.repositoryId}:${hotspot.filePath}`;
  }

  /** The repository filter option, with the licence when the snapshot recorded one. */
  protected repositoryOption(repository: AnalyticsRepositoryRef): string {
    return repository.licenseSpdx ? `${repository.name} · ${repository.licenseSpdx}` : repository.name;
  }

  showSplit(split: string): void {
    if (this.splitDetail()?.split === split) {
      this.splitDetail.set(null);
      return;
    }
    this.api.listDatasetRepositories(split).subscribe({
      next: (page) => this.splitDetail.set({ split, items: page.items }),
    });
  }

  // ── drill-down ────────────────────────────────────────────────────────────

  openDrill(label: string, filter: AnalyticsFilter): void {
    this.drill.set({ label, filter });
    this.loadDrill(0);
  }

  loadDrill(page: number): void {
    const drill = this.drill();
    if (!drill) {
      return;
    }
    this.drillLoading.set(true);
    this.drillError.set(null);
    this.api.listAnalyticsFindings({ ...drill.filter, page, size: this.drillPageSize }).subscribe({
      next: (result: Paged<AnalyticsFinding>) => {
        this.drillItems.set(result.items);
        this.drillPage.set(result.page);
        this.drillTotal.set(result.totalItems);
        this.drillHasNext.set(result.hasNext);
        this.drillLoading.set(false);
      },
      error: (error) => {
        this.drillError.set(problemMessage(error));
        this.drillLoading.set(false);
      },
    });
  }

  closeDrill(): void {
    this.drill.set(null);
    this.drillItems.set([]);
  }

  // ── charts ────────────────────────────────────────────────────────────────

  /** True when the canvases can be drawn at all: jsdom has no 2D context, and the tables still carry the numbers. */
  protected chartsSupported(): boolean {
    const canvas = this.ruleCanvas()?.nativeElement;
    return !!canvas && typeof canvas.getContext === 'function' && !!canvas.getContext('2d');
  }

  private renderCharts(overview: AnalyticsOverview | null): void {
    if (!overview?.available || !this.chartsSupported()) {
      this.destroyAll();
      return;
    }
    this.drawBar('rules', this.ruleCanvas(), this.ruleSeries(), 'Findings', null, (index) => {
      const row = this.ruleSeries().meta[index];
      if (row) {
        this.openDrill(`${row.ruleId} · ${this.repositoryNames().get(row.repositoryId) ?? row.repositoryId}`, {
          repositoryId: row.repositoryId,
          ruleId: row.ruleId,
        });
      }
    });
    this.drawDoughnut(this.severityCanvas(), this.severitySeries());
    // The trend is drawn for one repository at a time: a corpus-wide line would sum repositories of very different
    // sizes into a shape that reads like a quality trend and is not one. The weeks stay in the table below.
    if (this.selectedRepositoryId() === null) {
      this.destroy('trend');
    } else {
      this.drawLine(this.trendCanvas(), this.trendSeries());
    }
    this.drawBar('timeToFix', this.timeToFixCanvas(), this.timeToFixSeries(), 'Resolved findings',
      this.timeToFixColors(), (index) => {
        const label = this.timeToFixSeries().labels[index];
        const row = this.timeToFixSeries().meta[index];
        this.openDrill(`Commits until resolved: ${label}`, {
          repositoryId: row ? row.repositoryId : this.selectedRepositoryId(),
          ruleId: row ? row.ruleId : this.selectedRuleId(),
        });
      });
  }

  private drawBar<T>(
    key: string,
    canvas: ElementRef<HTMLCanvasElement> | undefined,
    series: ChartSeries<T>,
    label: string,
    colors: string[] | null,
    onClick: (index: number) => void,
  ): void {
    if (!canvas || series.labels.length === 0) {
      this.destroy(key);
      return;
    }
    this.destroy(key);
    const chart = new Chart(canvas.nativeElement, {
      type: 'bar',
      data: {
        labels: series.labels,
        datasets: [
          {
            label,
            data: series.values,
            backgroundColor: colors ?? CHART_COLORS.accentSoft,
            borderColor: CHART_COLORS.accent,
            borderWidth: colors ? 0 : 1,
            borderRadius: 4,
          },
        ],
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        onClick: (_event, elements) => {
          if (elements.length > 0) {
            onClick(elements[0].index);
          }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              afterLabel: (context) => this.barTooltip(series, context.dataIndex),
            },
          },
        },
        scales: {
          x: { beginAtZero: true, grid: { color: CHART_COLORS.grid }, ticks: { color: CHART_COLORS.text } },
          y: { grid: { display: false }, ticks: { color: CHART_COLORS.text } },
        },
      },
    });
    this.charts.set(key, chart);
  }

  /** A bar's title plus the density behind it: the raw count alone cannot be compared across repositories. */
  private barTooltip(series: ChartSeries<unknown>, index: number): string | string[] {
    const row = series.meta[index];
    if (row && typeof row === 'object' && 'ruleId' in row && 'findingsPerKloc' in row) {
      const rule = row as RuleStat;
      return [`${rule.repositoryId}`, `${formatDensity(rule.findingsPerKloc)} per KLOC · ${rule.filesAffected} files`];
    }
    if (row && typeof row === 'object' && 'ruleId' in row && 'resolvedPairs' in row) {
      const bucket = row as TimeToFixBucket;
      return [`${bucket.repositoryId}`, `${bucket.resolvedPairs} resolved · ${bucket.observedIntroductions} introduced`];
    }
    return [];
  }

  private drawDoughnut(canvas: ElementRef<HTMLCanvasElement> | undefined, series: ChartSeries<SeverityCount>): void {
    if (!canvas || series.values.every((value) => value === 0)) {
      this.destroy('severity');
      return;
    }
    this.destroy('severity');
    const chart = new Chart(canvas.nativeElement, {
      type: 'doughnut',
      data: {
        labels: series.labels,
        datasets: [
          {
            data: series.values,
            backgroundColor: series.labels.map(
              (label) => CHART_COLORS.severity[label as Severity] ?? CHART_COLORS.accent,
            ),
            borderColor: '#ffffff',
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '62%',
        onClick: (_event, elements) => {
          if (elements.length > 0) {
            const severity = series.meta[elements[0].index]?.severity;
            if (severity) {
              this.openDrill(`Severity ${severity}`, { severity, repositoryId: this.selectedRepositoryId() });
            }
          }
        },
        plugins: {
          legend: { position: 'bottom', labels: { color: CHART_COLORS.text, boxWidth: 12, padding: 14 } },
        },
      },
    });
    this.charts.set('severity', chart);
  }

  private drawLine(canvas: ElementRef<HTMLCanvasElement> | undefined, series: ChartSeries<TrendPoint>): void {
    if (!canvas || series.labels.length === 0) {
      this.destroy('trend');
      return;
    }
    this.destroy('trend');
    const chart = new Chart(canvas.nativeElement, {
      type: 'line',
      data: {
        labels: series.labels,
        datasets: [
          {
            label: 'Findings per KLOC',
            data: series.values,
            borderColor: CHART_COLORS.accent,
            backgroundColor: 'rgba(79, 70, 229, 0.12)',
            fill: true,
            // A gap is drawn, not a line through it: a week without a measured density is not a data point.
            spanGaps: false,
            tension: 0.25,
            pointRadius: 4,
            pointHoverRadius: 6,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        onClick: (_event, elements) => {
          if (elements.length > 0) {
            const point = series.meta[elements[0].index];
            if (point) {
              // The bucket is an inclusive UTC week, so the drill-down asks for the same seven days.
              this.openDrill(`Week of ${point.bucketStart}`, {
                repositoryId: point.repositoryId,
                from: point.bucketStart,
                to: addDays(point.bucketStart, 6),
              });
            }
          }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              afterLabel: (context) => {
                const point = series.meta[context.dataIndex];
                return point
                  ? [
                      `${point.findings} findings in ${point.filesAnalysed} files`,
                      `${point.measuredLines} lines · ${point.parseFailures} parse failures`,
                    ]
                  : [];
              },
            },
          },
        },
        scales: {
          x: { grid: { display: false }, ticks: { color: CHART_COLORS.text, maxRotation: 0 } },
          y: { beginAtZero: true, grid: { color: CHART_COLORS.grid }, ticks: { color: CHART_COLORS.text } },
        },
      },
    });
    this.charts.set('trend', chart);
  }

  private destroy(key: string): void {
    this.charts.get(key)?.destroy();
    this.charts.delete(key);
  }

  private destroyAll(): void {
    for (const key of [...this.charts.keys()]) {
      this.destroy(key);
    }
  }
}

interface DrillDown {
  label: string;
  filter: AnalyticsFilter;
}

/** Six days after an ISO date, for the inclusive week bucket of the trend chart. */
function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export type { HotspotComponent };
