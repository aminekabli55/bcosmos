import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BcosmosApi } from '../core/api/bcosmos-api';
import {
  Finding,
  LlmModel,
  Suggestion,
  SuggestionStatus,
  formatInstant,
  formatRange,
} from '../core/api/models';
import { problemMessage } from '../core/api/problem';

/**
 * The AI Review screen (spec v2 sections 7.3 and 9): evidence, explanation, diff, validation verdict, and the
 * accept / reject / defer decision.
 *
 * <p>Two rules of the backend are visible here on purpose. The verdict is shown next to the answer, never
 * summarised away: a suggestion whose diff does not apply looks different from one that passed every check. And
 * the decision buttons say "accept" about a proposal, not about a change that was made — BCosmos never applies a
 * patch (spec v2 section 1.3).</p>
 */
@Component({
  selector: 'app-ai-review',
  imports: [RouterLink],
  templateUrl: './ai-review.html',
  styleUrl: './ai-review.scss',
})
export class AiReview {
  private readonly api = inject(BcosmosApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly analysisId = signal<number | null>(null);
  protected readonly findingId = signal<number | null>(null);

  protected readonly finding = signal<Finding | null>(null);
  protected readonly findingError = signal<string | null>(null);
  protected readonly loading = signal(true);

  protected readonly suggestions = signal<Suggestion[]>([]);
  protected readonly selected = signal<Suggestion | null>(null);

  protected readonly requesting = signal(false);
  protected readonly requestError = signal<string | null>(null);
  protected readonly reviewing = signal(false);
  protected readonly reviewError = signal<string | null>(null);

  protected readonly models = signal<LlmModel[]>([]);
  protected readonly modelId = signal<number | null>(null);
  protected readonly embeddingModelId = signal<number | null>(null);

  protected readonly statuses: SuggestionStatus[] = ['ACCEPTED', 'REJECTED', 'DEFERRED'];
  protected readonly formatInstant = formatInstant;
  protected readonly formatRange = formatRange;

  /** Only chat models can write a suggestion; offering the others would earn a 400 from the backend. */
  protected readonly chatModels = computed(() =>
    this.models().filter((model) => model.capability === 'CHAT' && model.enabled),
  );

  /** Only embedding models can index the run's code, and only an enabled one can be called. */
  protected readonly embeddingModels = computed(() =>
    this.models().filter((model) => model.capability === 'EMBEDDING' && model.enabled),
  );

  /** The diff, one entry per line, so it can be coloured without any HTML injection. */
  protected readonly diffLines = computed(() => {
    const diff = this.selected()?.answer.proposedDiff;
    if (!diff) {
      return [];
    }
    return diff.split('\n').map((text) => ({
      text,
      kind: text.startsWith('+') && !text.startsWith('+++')
        ? 'added'
        : text.startsWith('-') && !text.startsWith('---')
          ? 'removed'
          : text.startsWith('@@')
            ? 'hunk'
            : 'context',
    }));
  });

  protected readonly checks = computed(() => {
    const verdict = this.selected()?.verdict;
    if (!verdict) {
      return [];
    }
    return [
      { label: 'schema', ok: verdict.schemaOk, hint: 'the answer matched the JSON schema' },
      { label: 'evidence', ok: verdict.evidenceOk, hint: 'every citation exists in this run' },
      { label: 'diff applies', ok: verdict.diffApplies, hint: 'the patch matches the analysed revision' },
      { label: 'parses', ok: verdict.parses, hint: 'the patched file still parses' },
      { label: 'finding gone', ok: verdict.targetFindingGone, hint: 'the rule no longer reports this finding' },
      { label: 'no new findings', ok: verdict.newFindings.length === 0, hint: 'the patch added nothing new' },
    ];
  });

  constructor() {
    this.route.paramMap.subscribe((params) => {
      // `Number(null)` is 0, so a missing segment must be checked before it is parsed.
      this.analysisId.set(AiReview.parseSegment(params.get('analysisId')));
      this.findingId.set(AiReview.parseSegment(params.get('findingId')));
      this.reset();
      if (this.findingId() !== null) {
        this.loadFinding(this.findingId()!);
        this.loadSuggestions(this.findingId()!);
      }
    });
    this.loadModels();
    this.destroyRef.onDestroy(() => this.reset());
  }

  private static parseSegment(raw: string | null): number | null {
    if (raw === null) {
      return null;
    }
    const parsed = Number(raw);
    return Number.isNaN(parsed) ? null : parsed;
  }

  protected onModelChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.modelId.set(value === '' ? null : Number(value));
  }

  protected onEmbeddingModelChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.embeddingModelId.set(value === '' ? null : Number(value));
  }

  /** Asks one model for a suggestion. The first call for a run also builds its embedding index. */
  protected requestSuggestion(): void {
    const findingId = this.findingId();
    if (findingId === null || this.requesting()) {
      return;
    }
    this.requesting.set(true);
    this.requestError.set(null);
    this.api
      .suggestFix(findingId, { modelId: this.modelId(), embeddingModelId: this.embeddingModelId() })
      .subscribe({
        next: (suggestion) => {
          this.requesting.set(false);
          this.suggestions.update((current) => [...current, suggestion]);
          this.selected.set(suggestion);
        },
        error: (error: unknown) => {
          this.requesting.set(false);
          this.requestError.set(problemMessage(error));
        },
      });
  }

  protected show(suggestion: Suggestion): void {
    this.selected.set(suggestion);
    this.reviewError.set(null);
  }

  protected review(status: SuggestionStatus): void {
    const suggestion = this.selected();
    if (!suggestion || this.reviewing()) {
      return;
    }
    this.reviewing.set(true);
    this.reviewError.set(null);
    this.api.reviewSuggestion(suggestion.id, status).subscribe({
      next: (reviewed) => {
        this.reviewing.set(false);
        this.suggestions.update((current) =>
          current.map((item) => (item.id === reviewed.id ? reviewed : item)),
        );
        this.selected.set(reviewed);
        // The decision also moves the finding's triage state, so the finding is reloaded.
        this.loadFinding(reviewed.findingId);
      },
      error: (error: unknown) => {
        this.reviewing.set(false);
        this.reviewError.set(problemMessage(error));
      },
    });
  }

  private reset(): void {
    this.finding.set(null);
    this.findingError.set(null);
    this.suggestions.set([]);
    this.selected.set(null);
    this.requestError.set(null);
    this.reviewError.set(null);
  }

  private loadFinding(findingId: number): void {
    this.loading.set(true);
    this.api.getFinding(findingId).subscribe({
      next: (finding) => {
        this.finding.set(finding);
        this.loading.set(false);
        this.findingError.set(null);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.findingError.set(problemMessage(error));
      },
    });
  }

  private loadSuggestions(findingId: number): void {
    this.api.listSuggestions(findingId).subscribe({
      next: (page) => {
        this.suggestions.set(page.items);
        const newest = page.items.at(-1);
        if (newest) {
          this.selected.set(newest);
        }
      },
      error: () => this.suggestions.set([]),
    });
  }

  private loadModels(): void {
    this.api.listModels().subscribe({
      next: (page) => this.models.set(page.items),
      error: () => this.models.set([]),
    });
  }
}
