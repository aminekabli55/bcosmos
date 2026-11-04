import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BcosmosApi } from '../core/api/bcosmos-api';
import { BenchmarkRun, formatRate, formatCost, formatInstant } from '../core/api/models';

type SortKey =
  | 'targetResolvedRate'
  | 'noNewFindingsRate'
  | 'diffAppliesRate'
  | 'parsesRate'
  | 'schemaValidRate'
  | 'avgLatencyMs'
  | 'estimatedCostUsd';

@Component({
  selector: 'app-benchmarks',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './benchmarks.html',
  styleUrls: ['./benchmarks.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Benchmarks implements OnInit {
  readonly runs = signal<BenchmarkRun[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly datasetVersionFilter = signal<string>('');
  readonly sortKey = signal<SortKey>('targetResolvedRate');
  readonly sortDesc = signal(true);
  readonly expandedRun = signal<number | null>(null);

  /** Unique dataset versions for the filter drop-down. */
  readonly datasetVersions = computed(() => [
    ...new Set(this.runs().map((r) => r.datasetVersion)),
  ]);

  readonly sortedRuns = computed(() => {
    let rows = this.runs();
    const version = this.datasetVersionFilter();
    if (version) {
      rows = rows.filter((r) => r.datasetVersion === version);
    }
    const key = this.sortKey();
    const desc = this.sortDesc();
    return [...rows].sort((a, b) => {
      const av = a[key] as number;
      const bv = b[key] as number;
      return desc ? bv - av : av - bv;
    });
  });

  /** The best target-resolved rate across visible rows (used for the sparkline bars). */
  readonly maxTargetResolved = computed(() =>
    Math.max(...this.sortedRuns().map((r) => r.targetResolvedRate), 0.001),
  );

  constructor(private readonly bcApi: BcosmosApi) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.bcApi.listBenchmarkRuns(this.datasetVersionFilter() || null).subscribe({
      next: (runs) => {
        this.runs.set(runs);
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.error.set(err?.error?.message ?? 'Failed to load benchmarks.');
        this.loading.set(false);
      },
    });
  }

  onVersionChange(version: string): void {
    this.datasetVersionFilter.set(version);
  }

  setSort(key: SortKey): void {
    if (this.sortKey() === key) {
      this.sortDesc.set(!this.sortDesc());
    } else {
      this.sortKey.set(key);
      this.sortDesc.set(true);
    }
  }

  sortArrow(key: SortKey): string {
    if (this.sortKey() !== key) return '';
    return this.sortDesc() ? ' ▼' : ' ▲';
  }

  toggleExpand(id: number): void {
    this.expandedRun.set(this.expandedRun() === id ? null : id);
  }

  isExpanded(id: number): boolean {
    return this.expandedRun() === id;
  }

  /** Expose helpers to the template. */
  readonly fmtRate = formatRate;
  readonly fmtCost = formatCost;
  readonly fmtInstant = formatInstant;

  barWidth(rate: number): string {
    const max = this.maxTargetResolved();
    return `${((rate / max) * 100).toFixed(1)}%`;
  }

  rateClass(rate: number): string {
    if (rate >= 0.8) return 'rate--good';
    if (rate >= 0.5) return 'rate--medium';
    return 'rate--poor';
  }

  metricEntries(json: string | null): { key: string; value: string }[] {
    if (!json) return [];
    try {
      const parsed = JSON.parse(json) as Record<string, unknown>;
      return Object.entries(parsed).map(([key, value]) => ({
        key,
        value: typeof value === 'number' ? value.toFixed(4) : String(value),
      }));
    } catch {
      return [];
    }
  }
}
