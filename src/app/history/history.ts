import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BcosmosApi } from '../core/api/bcosmos-api';
import {
  AnalysisRun,
  FindingTransitionItem,
  FindingTransitionType,
  Repository,
  SnapshotComparisonResponse,
  formatInstant,
} from '../core/api/models';
import { problemMessage } from '../core/api/problem';

type FilterTab = 'ALL' | FindingTransitionType;

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './history.html',
  styleUrls: ['./history.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class History implements OnInit {
  private readonly api = inject(BcosmosApi);

  readonly repositories = signal<Repository[]>([]);
  readonly selectedRepoId = signal<number | null>(null);

  readonly analyses = signal<AnalysisRun[]>([]);
  readonly fromRunId = signal<number | null>(null);
  readonly toRunId = signal<number | null>(null);

  readonly comparison = signal<SnapshotComparisonResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly selectedTab = signal<FilterTab>('ALL');
  readonly searchQuery = signal<string>('');

  readonly formatInstant = formatInstant;

  readonly filteredTransitions = computed(() => {
    const comp = this.comparison();
    if (!comp) return [];
    let items = comp.transitions;

    const tab = this.selectedTab();
    if (tab !== 'ALL') {
      items = items.filter((t) => t.type === tab);
    }

    const query = this.searchQuery().trim().toLowerCase();
    if (query) {
      items = items.filter(
        (t) =>
          t.ruleId.toLowerCase().includes(query) ||
          t.filePath.toLowerCase().includes(query) ||
          t.reason.toLowerCase().includes(query)
      );
    }
    return items;
  });

  ngOnInit(): void {
    this.loadRepositories();
  }

  loadRepositories(): void {
    this.api.listRepositories().subscribe({
      next: (page) => {
        this.repositories.set(page.items);
        if (page.items.length > 0) {
          const first = page.items[0];
          this.selectedRepoId.set(first.id);
          this.loadAnalysesForRepo(first.id);
        }
      },
      error: (err: unknown) => {
        this.error.set(problemMessage(err));
      },
    });
  }

  onRepoChange(repoIdStr: string): void {
    const repoId = Number(repoIdStr);
    this.selectedRepoId.set(repoId);
    this.comparison.set(null);
    this.loadAnalysesForRepo(repoId);
  }

  loadAnalysesForRepo(repoId: number): void {
    this.api.listAnalyses(repoId, 0, 50).subscribe({
      next: (page) => {
        const completed = page.items.filter((r) => r.status === 'SUCCEEDED');
        this.analyses.set(completed);
        if (completed.length >= 2) {
          // Default: from = previous run (index 1), to = latest run (index 0)
          this.fromRunId.set(completed[1].id);
          this.toRunId.set(completed[0].id);
          this.compare();
        } else if (completed.length === 1) {
          this.fromRunId.set(completed[0].id);
          this.toRunId.set(completed[0].id);
          this.comparison.set(null);
        } else {
          this.fromRunId.set(null);
          this.toRunId.set(null);
          this.comparison.set(null);
        }
      },
      error: (err: unknown) => {
        this.error.set(problemMessage(err));
      },
    });
  }

  compare(): void {
    const fromId = this.fromRunId();
    const toId = this.toRunId();
    if (!fromId || !toId) return;

    this.loading.set(true);
    this.error.set(null);

    this.api.compareSnapshots(fromId, toId).subscribe({
      next: (res) => {
        this.comparison.set(res);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(problemMessage(err));
      },
    });
  }

  setTab(tab: FilterTab): void {
    this.selectedTab.set(tab);
  }

  formatConfidence(conf: number): string {
    return `${Math.round(conf * 100)}%`;
  }
}
