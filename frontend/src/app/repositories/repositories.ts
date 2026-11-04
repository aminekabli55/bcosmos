import { Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BcosmosApi } from '../core/api/bcosmos-api';
import { AnalysisRun, Repository, formatInstant, isTerminalStatus } from '../core/api/models';
import { problemMessage } from '../core/api/problem';
import { getLanguageSupport } from '../core/language-support';

/** A run that is still moving is asked about once a second; the backend does the work in-process (ADR 0001). */
const POLL_INTERVAL_MS = 1000;

@Component({
  selector: 'app-repositories',
  imports: [RouterLink],
  templateUrl: './repositories.html',
  styleUrl: './repositories.scss',
})
export class Repositories {
  private readonly api = inject(BcosmosApi);
  private readonly destroyRef = inject(DestroyRef);
  private pollHandle: ReturnType<typeof setInterval> | null = null;

  protected readonly repositories = signal<Repository[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly name = signal('');
  protected readonly localPath = signal('');
  protected readonly registering = signal(false);
  protected readonly registerError = signal<string | null>(null);
  protected readonly registerNotice = signal<string | null>(null);

  /** The row whose Delete button was pressed: nothing is sent until the confirmation is answered. */
  protected readonly deleteCandidate = signal<Repository | null>(null);
  protected readonly deleting = signal(false);
  protected readonly deleteError = signal<string | null>(null);
  protected readonly deleteNotice = signal<string | null>(null);

  protected readonly selected = signal<Repository | null>(null);
  protected readonly runs = signal<AnalysisRun[]>([]);
  protected readonly runsLoading = signal(false);
  protected readonly runsError = signal<string | null>(null);
  protected readonly starting = signal(false);

  protected readonly formatInstant = formatInstant;
  protected readonly isTerminalStatus = isTerminalStatus;
  protected readonly languageSupport = getLanguageSupport;

  /** First letter of a repository name, for the avatar in the list. */
  protected initial(name: string): string {
    return name.trim().charAt(0).toUpperCase() || '?';
  }

  constructor() {
    this.loadRepositories();
    this.destroyRef.onDestroy(() => this.stopPolling());
  }

  protected loadRepositories(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listRepositories().subscribe({
      next: (page) => {
        this.repositories.set(page.items);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(problemMessage(error));
        this.loading.set(false);
      },
    });
  }

  protected onNameInput(event: Event): void {
    this.name.set((event.target as HTMLInputElement).value);
  }

  protected onPathInput(event: Event): void {
    this.localPath.set((event.target as HTMLInputElement).value);
  }

  protected register(event: Event): void {
    event.preventDefault();
    const localPath = this.localPath().trim();
    if (!localPath) {
      this.registerError.set('A repository path is required.');
      return;
    }
    this.registering.set(true);
    this.registerError.set(null);
    this.registerNotice.set(null);
    const name = this.name().trim();
    this.api.registerRepository({ localPath, name: name ? name : null }).subscribe({
      next: (repository) => {
        this.registering.set(false);
        this.name.set('');
        this.localPath.set('');
        this.registerNotice.set(`Registered ${repository.name}.`);
        this.loadRepositories();
        this.select(repository);
      },
      error: (error: unknown) => {
        this.registering.set(false);
        this.registerError.set(problemMessage(error));
      },
    });
  }

  protected requestDelete(repository: Repository): void {
    this.deleteError.set(null);
    this.deleteNotice.set(null);
    this.deleteCandidate.set(repository);
  }

  protected cancelDelete(): void {
    this.deleteCandidate.set(null);
  }

  /**
   * Removes the registration and everything recorded for it. The folder on disk stays: BCosmos only ever read it.
   * While the request is in flight the confirmation stays open, so a refused delete can still be read.
   */
  protected confirmDelete(repository: Repository): void {
    this.deleting.set(true);
    this.deleteError.set(null);
    this.api.deleteRepository(repository.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteCandidate.set(null);
        this.deleteNotice.set(`Removed ${repository.name}.`);
        this.forgetIfSelected(repository);
        this.loadRepositories();
      },
      error: (error: unknown) => {
        this.deleting.set(false);
        this.deleteError.set(problemMessage(error));
      },
    });
  }

  /** A deleted repository must not keep its run table on screen, and must stop being polled. */
  private forgetIfSelected(repository: Repository): void {
    if (this.selected()?.id !== repository.id) {
      return;
    }
    this.stopPolling();
    this.selected.set(null);
    this.runs.set([]);
    this.runsError.set(null);
  }

  protected select(repository: Repository): void {
    this.selected.set(repository);
    this.stopPolling();
    this.refreshRuns();
  }

  protected startAnalysis(repository: Repository): void {
    this.starting.set(true);
    this.runsError.set(null);
    this.api.startAnalysis(repository.id).subscribe({
      next: () => {
        this.starting.set(false);
        this.select(repository);
      },
      error: (error: unknown) => {
        this.starting.set(false);
        this.runsError.set(problemMessage(error));
      },
    });
  }

  private refreshRuns(): void {
    const repository = this.selected();
    if (!repository) {
      return;
    }
    this.runsLoading.set(true);
    this.api.listAnalyses(repository.id).subscribe({
      next: (page) => {
        this.runs.set(page.items);
        this.runsLoading.set(false);
        this.runsError.set(null);
        this.syncPolling();
      },
      error: (error: unknown) => {
        this.runsLoading.set(false);
        this.runsError.set(problemMessage(error));
        this.stopPolling();
      },
    });
  }

  private syncPolling(): void {
    const active = this.runs().some((run) => !isTerminalStatus(run.status));
    if (active && this.pollHandle === null) {
      this.pollHandle = setInterval(() => this.refreshRuns(), POLL_INTERVAL_MS);
    } else if (!active) {
      this.stopPolling();
    }
  }

  private stopPolling(): void {
    if (this.pollHandle !== null) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }
}
