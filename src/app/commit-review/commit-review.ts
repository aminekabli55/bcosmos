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
import { BcosmosApi } from '../core/api/bcosmos-api';
import {
  CommitItem,
  CommitReviewResponse,
  LlmModel,
  Repository,
  formatInstant,
} from '../core/api/models';
import { problemMessage } from '../core/api/problem';

@Component({
  selector: 'app-commit-review',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './commit-review.html',
  styleUrls: ['./commit-review.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommitReview implements OnInit {
  private readonly api = inject(BcosmosApi);

  readonly repositories = signal<Repository[]>([]);
  readonly selectedRepoId = signal<number | null>(null);
  readonly commits = signal<CommitItem[]>([]);
  readonly models = signal<LlmModel[]>([]);

  readonly fromCommit = signal<string>('');
  readonly toCommit = signal<string>('');
  readonly selectedModelId = signal<number | null>(null);

  readonly review = signal<CommitReviewResponse | null>(null);
  readonly loading = signal(false);
  readonly loadingCommits = signal(false);
  readonly error = signal<string | null>(null);

  readonly formatInstant = formatInstant;

  readonly enabledModels = computed(() =>
    this.models().filter((m) => m.enabled && m.capability === 'CHAT')
  );

  readonly riskSeverityClass: Record<string, string> = {
    CRITICAL: 'sev-critical',
    HIGH: 'sev-high',
    MEDIUM: 'sev-medium',
    LOW: 'sev-low',
  };

  ngOnInit(): void {
    this.loadRepositories();
    this.loadModels();
  }

  loadRepositories(): void {
    this.api.listRepositories().subscribe({
      next: (page) => {
        this.repositories.set(page.items);
        if (page.items.length > 0) {
          this.selectedRepoId.set(page.items[0].id);
          this.loadCommits(page.items[0].id);
        }
      },
      error: (err: unknown) => {
        this.error.set(problemMessage(err));
      },
    });
  }

  loadModels(): void {
    this.api.listModels(0, 50).subscribe({
      next: (page) => this.models.set(page.items),
      error: () => {},
    });
  }

  onRepoChange(repoIdStr: string): void {
    const repoId = Number(repoIdStr);
    this.selectedRepoId.set(repoId);
    this.review.set(null);
    this.fromCommit.set('');
    this.toCommit.set('');
    this.loadCommits(repoId);
  }

  loadCommits(repoId: number): void {
    this.loadingCommits.set(true);
    this.api.listCommits(repoId, 50).subscribe({
      next: (res) => {
        this.commits.set(res.commits);
        this.loadingCommits.set(false);
        // Default: from = parent of latest, to = latest
        const commits = res.commits;
        if (commits.length >= 2) {
          this.toCommit.set(commits[0].hash);
          this.fromCommit.set(commits[1].hash);
        } else if (commits.length === 1) {
          this.toCommit.set(commits[0].hash);
          this.fromCommit.set(commits[0].parentHashes[0] ?? '');
        }
      },
      error: (err: unknown) => {
        this.loadingCommits.set(false);
        this.error.set(problemMessage(err));
      },
    });
  }

  runReview(): void {
    const repoId = this.selectedRepoId();
    const from = this.fromCommit().trim();
    const to = this.toCommit().trim();
    if (!repoId || !from || !to) return;

    this.loading.set(true);
    this.error.set(null);
    this.review.set(null);

    this.api
      .createCommitReview(repoId, { fromCommit: from, toCommit: to, modelId: this.selectedModelId() })
      .subscribe({
        next: (res) => {
          this.review.set(res);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          this.error.set(problemMessage(err));
        },
      });
  }

  loadExistingReview(reviewId: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getCommitReview(reviewId).subscribe({
      next: (res) => {
        this.review.set(res);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(problemMessage(err));
      },
    });
  }

  selectFromCommit(hash: string): void {
    this.fromCommit.set(hash);
  }

  selectToCommit(hash: string): void {
    this.toCommit.set(hash);
  }

  get canRunReview(): boolean {
    return !!(this.selectedRepoId() && this.fromCommit().trim() && this.toCommit().trim());
  }

  riskOrderValue(sev: string): number {
    return { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 }[sev] ?? 4;
  }

  sortedRisks() {
    const r = this.review();
    if (!r) return [];
    return [...r.riskSignals].sort(
      (a, b) => this.riskOrderValue(a.severity) - this.riskOrderValue(b.severity)
    );
  }
}
