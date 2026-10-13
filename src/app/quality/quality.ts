import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BcosmosApi } from '../core/api/bcosmos-api';
import {
  AnalysisRun,
  Finding,
  FileRecord,
  SEVERITIES,
  Severity,
  formatInstant,
  isTerminalStatus,
} from '../core/api/models';
import { problemMessage } from '../core/api/problem';

const POLL_INTERVAL_MS = 1000;
const PAGE_SIZES = [10, 25, 50, 100];

@Component({
  selector: 'app-quality',
  imports: [RouterLink],
  templateUrl: './quality.html',
  styleUrl: './quality.scss',
})
export class Quality {
  private readonly api = inject(BcosmosApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private pollHandle: ReturnType<typeof setInterval> | null = null;

  protected readonly analysisId = signal<number | null>(null);
  protected readonly run = signal<AnalysisRun | null>(null);
  protected readonly runLoading = signal(false);
  protected readonly runError = signal<string | null>(null);

  protected readonly findings = signal<Finding[]>([]);
  protected readonly findingsLoading = signal(false);
  protected readonly findingsError = signal<string | null>(null);
  protected readonly totalItems = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(25);

  protected readonly severity = signal<Severity | ''>('');
  protected readonly rule = signal('');
  protected readonly file = signal('');

  protected readonly failures = signal<FileRecord[]>([]);
  protected readonly selected = signal<Finding | null>(null);

  protected readonly severities = SEVERITIES;
  protected readonly pageSizes = PAGE_SIZES;
  protected readonly formatInstant = formatInstant;
  protected readonly isTerminalStatus = isTerminalStatus;

  /** Rule ids of the findings on screen, offered as suggestions because the filter matches them exactly. */
  protected readonly ruleSuggestions = computed(() =>
    [...new Set(this.findings().map((finding) => finding.ruleId))].sort(),
  );

  /** The preview always shows the snippet stored with the finding, so it matches the analysed revision. */
  protected readonly preview = computed(() => {
    const finding = this.selected();
    if (!finding?.snippet) {
      return null;
    }
    return finding.snippet
      .split('\n')
      .map((text, offset) => ({ number: finding.startLine + offset, text: text.replace(/\t/g, '    ') }));
  });

  constructor() {
    this.route.paramMap.subscribe((params) => {
      const raw = params.get('analysisId');
      const parsed = raw === null ? Number.NaN : Number(raw);
      if (Number.isNaN(parsed)) {
        this.reset();
        return;
      }
      this.analysisId.set(parsed);
      this.loadRun(parsed);
      this.loadFindings(parsed);
      this.loadFailures(parsed);
    });
    this.destroyRef.onDestroy(() => this.stopPolling());
  }

  protected onSeverityChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.severity.set(value === '' ? '' : (value as Severity));
    this.applyFilters();
  }

  protected onRuleInput(event: Event): void {
    this.rule.set((event.target as HTMLInputElement).value);
  }

  protected onFileInput(event: Event): void {
    this.file.set((event.target as HTMLInputElement).value);
  }

  protected onSizeChange(event: Event): void {
    this.size.set(Number((event.target as HTMLSelectElement).value));
    this.page.set(0);
    this.reloadFindings();
  }

  protected applyFilters(): void {
    this.page.set(0);
    this.reloadFindings();
  }

  protected clearFilters(): void {
    this.severity.set('');
    this.rule.set('');
    this.file.set('');
    this.applyFilters();
  }

  protected select(finding: Finding): void {
    this.selected.set(finding);
  }

  protected previousPage(): void {
    if (this.page() > 0) {
      this.page.update((page) => page - 1);
      this.reloadFindings();
    }
  }

  protected nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.page.update((page) => page + 1);
      this.reloadFindings();
    }
  }

  protected reloadRun(): void {
    const id = this.analysisId();
    if (id !== null) {
      this.loadRun(id);
    }
  }

  private reset(): void {
    this.analysisId.set(null);
    this.run.set(null);
    this.findings.set([]);
    this.failures.set([]);
    this.selected.set(null);
    this.stopPolling();
  }

  private loadRun(id: number): void {
    this.runLoading.set(true);
    this.runError.set(null);
    this.api.getAnalysis(id).subscribe({
      next: (run) => {
        this.run.set(run);
        this.runLoading.set(false);
        if (isTerminalStatus(run.status)) {
          this.stopPolling();
        } else {
          this.startPolling(id);
        }
      },
      error: (error: unknown) => {
        this.runLoading.set(false);
        this.runError.set(problemMessage(error));
        this.stopPolling();
      },
    });
  }

  private reloadFindings(): void {
    const id = this.analysisId();
    if (id !== null) {
      this.loadFindings(id);
    }
  }

  private loadFindings(id: number): void {
    const severity = this.severity();
    this.findingsLoading.set(true);
    this.api
      .listFindings(id, {
        severity: severity === '' ? null : severity,
        rule: this.rule().trim() || null,
        file: this.file().trim() || null,
        page: this.page(),
        size: this.size(),
      })
      .subscribe({
        next: (page) => {
          this.findings.set(page.items);
          this.totalItems.set(page.totalItems);
          this.totalPages.set(page.totalPages);
          this.findingsLoading.set(false);
          this.findingsError.set(null);
        },
        error: (error: unknown) => {
          this.findingsLoading.set(false);
          this.findingsError.set(problemMessage(error));
        },
      });
  }

  private loadFailures(id: number): void {
    this.api.listFiles(id, 'FAILED').subscribe({
      next: (page) => this.failures.set(page.items),
      error: () => this.failures.set([]),
    });
  }

  /** Follows a run that is still moving: status and counters change on the server, not here. */
  private startPolling(id: number): void {
    if (this.pollHandle !== null) {
      return;
    }
    this.pollHandle = setInterval(() => {
      this.api.getAnalysis(id).subscribe({
        next: (run) => {
          this.run.set(run);
          if (isTerminalStatus(run.status)) {
            this.stopPolling();
            this.reloadFindings();
            this.loadFailures(id);
          }
        },
        error: () => this.stopPolling(),
      });
    }, POLL_INTERVAL_MS);
  }

  private stopPolling(): void {
    if (this.pollHandle !== null) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }
}
