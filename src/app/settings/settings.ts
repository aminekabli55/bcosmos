import { Component, computed, inject, signal } from '@angular/core';
import { BcosmosApi } from '../core/api/bcosmos-api';
import { AiSettings, LlmModel, LlmProvider, ModelEdit, formatInstant, formatPrice } from '../core/api/models';
import { problemMessage } from '../core/api/problem';

/** The editable fields of one registry row, kept as text so an empty box means "leave it". */
interface ModelDraft {
  contextWindow: string;
  costPer1kIn: string;
  costPer1kOut: string;
  notes: string;
}

/** What the screen can say about one provider's credential, gathered from the registry rows. */
interface ProviderCredential {
  provider: LlmProvider;
  environmentVariable: string | null;
  configured: boolean;
  models: number;
}

/**
 * The Settings screen (spec v2 section 9): the AI master switch and the model registry.
 *
 * <p>Provider keys are never editable here and never requested: they live in the backend environment, and the
 * screen only reports whether one is present (spec section 10).</p>
 */
@Component({
  selector: 'app-settings',
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings {
  private readonly api = inject(BcosmosApi);

  protected readonly ai = signal<AiSettings | null>(null);
  protected readonly aiLoading = signal(true);
  protected readonly aiError = signal<string | null>(null);
  protected readonly aiSaving = signal(false);

  protected readonly models = signal<LlmModel[]>([]);
  protected readonly modelsLoading = signal(true);
  protected readonly modelsError = signal<string | null>(null);
  protected readonly modelBusyId = signal<number | null>(null);

  protected readonly editingId = signal<number | null>(null);
  protected readonly draft = signal<ModelDraft | null>(null);

  protected readonly formatInstant = formatInstant;
  protected readonly formatPrice = formatPrice;

  /** One row per provider: whether a key is present, and which variable to set when it is not. */
  protected readonly credentials = computed<ProviderCredential[]>(() =>
    [...this.models().reduce((providers, model) => {
      const current = providers.get(model.provider);
      providers.set(model.provider, {
        provider: model.provider,
        environmentVariable: model.credentialEnvironmentVariable,
        configured: (current?.configured ?? false) || model.credentialConfigured,
        models: (current?.models ?? 0) + 1,
      });
      return providers;
    }, new Map<LlmProvider, ProviderCredential>()).values()],
  );

  constructor() {
    this.loadAi();
    this.loadModels();
  }

  protected loadAi(): void {
    this.aiLoading.set(true);
    this.aiError.set(null);
    this.api.getAiSettings().subscribe({
      next: (settings) => {
        this.ai.set(settings);
        this.aiLoading.set(false);
      },
      error: (error: unknown) => {
        this.aiError.set(problemMessage(error));
        this.aiLoading.set(false);
      },
    });
  }

  protected loadModels(): void {
    this.modelsLoading.set(true);
    this.modelsError.set(null);
    this.api.listModels().subscribe({
      next: (page) => {
        this.models.set(page.items);
        this.modelsLoading.set(false);
      },
      error: (error: unknown) => {
        this.modelsError.set(problemMessage(error));
        this.modelsLoading.set(false);
      },
    });
  }

  protected onAiToggle(event: Event): void {
    const input = event.target as HTMLInputElement;
    // A `[checked]` binding is only written when its value changes, so a refused update would leave the switch
    // flipped; put it back by hand when the backend says no.
    this.saveAi(input.checked, () => (input.checked = !input.checked));
  }

  protected onModelToggle(model: LlmModel, event: Event): void {
    const input = event.target as HTMLInputElement;
    this.editModel(model, { enabled: input.checked }, () => (input.checked = model.enabled));
  }

  /** Removes the stored override, so the deployment property decides again. */
  protected useDeploymentDefault(): void {
    this.saveAi(null);
  }

  /** Promotes a model; the backend clears the previous default, so the whole list is reloaded. */
  protected makeDefault(model: LlmModel): void {
    this.modelBusyId.set(model.id);
    this.modelsError.set(null);
    this.api.updateModel(model.id, { defaultModel: true }).subscribe({
      next: () => {
        this.modelBusyId.set(null);
        this.loadModels();
      },
      error: (error: unknown) => {
        this.modelBusyId.set(null);
        this.modelsError.set(problemMessage(error));
      },
    });
  }

  protected startEdit(model: LlmModel): void {
    this.editingId.set(model.id);
    this.draft.set({
      contextWindow: String(model.contextWindow),
      costPer1kIn: String(model.costPer1kIn),
      costPer1kOut: String(model.costPer1kOut),
      notes: model.notes ?? '',
    });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.draft.set(null);
  }

  protected onDraftInput(field: keyof ModelDraft, event: Event): void {
    const value = (event.target as HTMLInputElement | HTMLTextAreaElement).value;
    this.draft.update((draft) => (draft ? { ...draft, [field]: value } : draft));
  }

  protected saveEdit(model: LlmModel): void {
    const draft = this.draft();
    if (!draft) {
      return;
    }
    const contextWindow = Number(draft.contextWindow);
    if (!Number.isInteger(contextWindow) || contextWindow <= 0) {
      this.modelsError.set('The context window must be a positive whole number of tokens.');
      return;
    }
    const costPer1kIn = Number(draft.costPer1kIn);
    const costPer1kOut = Number(draft.costPer1kOut);
    if (Number.isNaN(costPer1kIn) || Number.isNaN(costPer1kOut) || costPer1kIn < 0 || costPer1kOut < 0) {
      this.modelsError.set('Prices must be numbers and cannot be negative.');
      return;
    }
    this.editModel(model, {
      contextWindow,
      costPer1kIn,
      costPer1kOut,
      notes: draft.notes.trim(),
    });
  }

  private saveAi(enabled: boolean | null, onFailure?: () => void): void {
    this.aiSaving.set(true);
    this.aiError.set(null);
    this.api.updateAiSettings(enabled).subscribe({
      next: (settings) => {
        this.ai.set(settings);
        this.aiSaving.set(false);
      },
      error: (error: unknown) => {
        this.aiSaving.set(false);
        this.aiError.set(problemMessage(error));
        onFailure?.();
      },
    });
  }

  /** `onFailure` lets a caller undo a control the user flipped, which the bindings would otherwise leave as-is. */
  private editModel(model: LlmModel, edit: ModelEdit, onFailure?: () => void): void {
    this.modelBusyId.set(model.id);
    this.modelsError.set(null);
    this.api.updateModel(model.id, edit).subscribe({
      next: (updated) => {
        this.modelBusyId.set(null);
        this.editingId.set(null);
        this.draft.set(null);
        this.models.update((models) => models.map((row) => (row.id === updated.id ? updated : row)));
      },
      error: (error: unknown) => {
        this.modelBusyId.set(null);
        this.modelsError.set(problemMessage(error));
        onFailure?.();
      },
    });
  }
}
