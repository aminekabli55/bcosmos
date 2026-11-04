import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { BcosmosApi } from '../core/api/bcosmos-api';
import {
  CorpusIngestResponse,
  CorpusStatusResponse,
  formatInstant,
} from '../core/api/models';
import { problemMessage } from '../core/api/problem';

/**
 * The Corpus Status & Ingestion screen (spec v2 section 8, Phase 4).
 *
 * <p>Enables operators to trigger asynchronous mining of allowlisted public repositories
 * and inspect commit discovery progress stored in Postgres.</p>
 */
@Component({
  selector: 'app-corpus',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './corpus.html',
  styleUrl: './corpus.scss',
})
export class Corpus implements OnInit {
  private readonly api = inject(BcosmosApi);

  protected readonly urlsText = signal('');
  protected readonly commitWindow = signal(50);
  protected readonly ingesting = signal(false);
  protected readonly ingestResult = signal<CorpusIngestResponse | null>(null);
  protected readonly ingestError = signal<string | null>(null);

  protected readonly status = signal<CorpusStatusResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly formatInstant = formatInstant;

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getCorpusStatus().subscribe({
      next: (res) => {
        this.status.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(problemMessage(err));
        this.loading.set(false);
      },
    });
  }

  submitIngest(): void {
    const raw = this.urlsText().trim();
    if (!raw) {
      return;
    }

    const urls = raw
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line.length > 0);

    if (urls.length === 0) {
      return;
    }

    this.ingesting.set(true);
    this.ingestError.set(null);
    this.ingestResult.set(null);

    this.api.ingestCorpus({ urls, commitWindow: this.commitWindow() }).subscribe({
      next: (res) => {
        this.ingestResult.set(res);
        this.ingesting.set(false);
        this.urlsText.set('');
        this.refresh();
      },
      error: (err) => {
        this.ingestError.set(problemMessage(err));
        this.ingesting.set(false);
      },
    });
  }

  clearResult(): void {
    this.ingestResult.set(null);
    this.ingestError.set(null);
  }
}
