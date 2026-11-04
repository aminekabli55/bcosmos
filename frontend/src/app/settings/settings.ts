import { Component, computed, inject, signal } from '@angular/core';
import { BcosmosApi } from '../core/api/bcosmos-api';
import {
  AiSettings,
  CredentialSource,
  LlmModel,
  LlmModelCapability,
  LlmModelKind,
  ModelEdit,
  CreateModelRequest,
  PROVIDER_PROTOCOLS,
  Provider,
  ProviderProtocol,
  UpdateProviderRequest,
  formatInstant,
  formatPrice,
} from '../core/api/models';
import { problemMessage } from '../core/api/problem';

/** The editable fields of one registry row, kept as text so an empty box means "leave it". */
interface ModelDraft {
  contextWindow: string;
  costPer1kIn: string;
  costPer1kOut: string;
  notes: string;
}

/** Everything the "add provider" form collects; `code` is the stable identity and cannot be edited later. */
interface ProviderDraft {
  code: string;
  label: string;
  protocol: ProviderProtocol;
  baseUrl: string;
  apiKeyEnv: string;
  apiKeyRequired: boolean;
  apiKey: string;
}

/**
 * An edit of one provider: the same boxes, pre-filled, with `code` deliberately absent because it is the stable
 * identity.
 */
interface ProviderEditDraft {
  label: string;
  protocol: ProviderProtocol;
  baseUrl: string;
  apiKeyEnv: string;
  apiKeyRequired: boolean;
  /** `null` leaves the stored key alone; a non-empty value replaces it; an empty one clears it. */
  apiKey: string | null;
}

const EMPTY_PROVIDER_DRAFT: ProviderDraft = {
  code: '',
  label: '',
  protocol: 'OPENAI_COMPATIBLE',
  baseUrl: '',
  apiKeyEnv: '',
  apiKeyRequired: true,
  apiKey: '',
};

/**
 * The "add model" form. The id is typed exactly as the vendor spells it (`deepseek-chat`, `claude-sonnet-4-5`);
 * everything the backend can default is optional here.
 */
interface ModelCreateDraft {
  /** The provider row, as a select value. */
  providerId: string;
  modelName: string;
  capability: LlmModelCapability;
  kind: LlmModelKind;
  embeddingDimension: string;
  contextWindow: string;
  costPer1kIn: string;
  costPer1kOut: string;
  notes: string;
}

const EMPTY_MODEL_DRAFT: ModelCreateDraft = {
  providerId: '',
  modelName: '',
  capability: 'CHAT',
  kind: 'HOSTED',
  embeddingDimension: '',
  contextWindow: '',
  costPer1kIn: '',
  costPer1kOut: '',
  notes: '',
};

/**
 * The Settings screen (spec v2 section 9): the AI master switch, the provider registry and the model registry.
 *
 * <p>Providers are rows, so adding DeepSeek, Claude or a local vLLM server is done here rather than by editing
 * configuration and redeploying (ADR 0038). A key pasted into this screen is encrypted before it is stored and is
 * never sent back — the screen only ever sees a source and the last four characters (spec section 10).</p>
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

  protected readonly providers = signal<Provider[]>([]);
  protected readonly providersLoading = signal(true);
  protected readonly providersError = signal<string | null>(null);
  protected readonly providerBusyId = signal<number | null>(null);
  protected readonly creatingProvider = signal(false);
  protected readonly providerDraft = signal<ProviderDraft>({ ...EMPTY_PROVIDER_DRAFT });
  protected readonly editingProviderId = signal<number | null>(null);
  protected readonly providerEditDraft = signal<ProviderEditDraft | null>(null);
  /** Two-step delete: the id whose delete button has been pressed once. */
  protected readonly confirmingProviderId = signal<number | null>(null);

  protected readonly models = signal<LlmModel[]>([]);
  protected readonly modelsLoading = signal(true);
  protected readonly modelsError = signal<string | null>(null);
  protected readonly modelBusyId = signal<number | null>(null);
  protected readonly creatingModel = signal(false);
  protected readonly modelCreateDraft = signal<ModelCreateDraft>({ ...EMPTY_MODEL_DRAFT });
  /** Two-step delete of a registry row: the id whose delete button has been pressed once. */
  protected readonly confirmingModelId = signal<number | null>(null);

  protected readonly editingId = signal<number | null>(null);
  protected readonly draft = signal<ModelDraft | null>(null);

  protected readonly protocols = PROVIDER_PROTOCOLS;
  protected readonly formatInstant = formatInstant;
  protected readonly formatPrice = formatPrice;

  constructor() {
    this.loadAi();
    this.loadProviders();
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

  protected loadProviders(): void {
    this.providersLoading.set(true);
    this.providersError.set(null);
    this.api.listProviders().subscribe({
      next: (page) => {
        this.providers.set(page.items);
        this.providersLoading.set(false);
      },
      error: (error: unknown) => {
        this.providersError.set(problemMessage(error));
        this.providersLoading.set(false);
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

  /** Removes the stored override, so the deployment property decides again. */
  protected useDeploymentDefault(): void {
    this.saveAi(null);
  }

  // ------------------------------------------------------------------------------------------
  // Providers
  // ------------------------------------------------------------------------------------------

  protected startCreateProvider(): void {
    this.providerDraft.set({ ...EMPTY_PROVIDER_DRAFT });
    this.creatingProvider.set(true);
    this.providersError.set(null);
  }

  protected cancelCreateProvider(): void {
    this.creatingProvider.set(false);
  }

  protected onProviderDraftInput(field: keyof ProviderDraft, event: Event): void {
    const target = event.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;
    const value = target instanceof HTMLInputElement && target.type === 'checkbox' ? target.checked : target.value;
    this.providerDraft.update((draft) => ({ ...draft, [field]: value }));
  }

  protected createProvider(): void {
    const draft = this.providerDraft();
    if (!draft) {
      return;
    }
    const problem = this.validateProvider(draft);
    if (problem) {
      this.providersError.set(problem);
      return;
    }
    this.providersError.set(null);
    this.api
      .createProvider({
        code: draft.code.trim(),
        label: draft.label.trim(),
        protocol: draft.protocol,
        baseUrl: draft.baseUrl.trim(),
        apiKeyEnv: draft.apiKeyEnv.trim() || null,
        apiKeyRequired: draft.apiKeyRequired,
        apiKey: draft.apiKey.trim() || null,
      })
      .subscribe({
        next: () => {
          this.creatingProvider.set(false);
          this.loadProviders();
        },
        error: (error: unknown) => {
          this.providersError.set(problemMessage(error));
        },
      });
  }

  protected startEditProvider(provider: Provider): void {
    this.editingProviderId.set(provider.id);
    this.providerEditDraft.set({
      label: provider.label,
      protocol: provider.protocol,
      baseUrl: provider.baseUrl,
      apiKeyEnv: provider.credentialEnvironmentVariable ?? '',
      apiKeyRequired: provider.apiKeyRequired,
      // Nothing to prefill: the stored key is never read back, so the box starts empty and means "leave it".
      apiKey: null,
    });
    this.providersError.set(null);
  }

  protected cancelEditProvider(): void {
    this.editingProviderId.set(null);
    this.providerEditDraft.set(null);
  }

  protected onProviderEditInput(field: keyof ProviderEditDraft, event: Event): void {
    const target = event.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;
    const value = target instanceof HTMLInputElement && target.type === 'checkbox' ? target.checked : target.value;
    this.providerEditDraft.update((draft) => (draft ? { ...draft, [field]: value } : draft));
  }

  protected saveProviderEdit(provider: Provider): void {
    const draft = this.providerEditDraft();
    if (!draft) {
      return;
    }
    const problem = this.validateProvider({
      // The code is not editable, so it is always the existing identity.
      code: provider.code,
      label: draft.label,
      baseUrl: draft.baseUrl,
      apiKeyEnv: draft.apiKeyEnv ?? '',
    });
    if (problem) {
      this.providersError.set(problem);
      return;
    }
    const edit: UpdateProviderRequest = {
      label: draft.label.trim(),
      protocol: draft.protocol,
      baseUrl: draft.baseUrl.trim(),
      apiKeyEnv: draft.apiKeyEnv.trim(),
      apiKeyRequired: draft.apiKeyRequired,
      // Only sent when the user typed something: null means "keep the stored key exactly as it is".
      apiKey: draft.apiKey === null ? null : draft.apiKey.trim(),
    };
    this.providersError.set(null);
    this.providerBusyId.set(provider.id);
    this.api.updateProvider(provider.id, edit).subscribe({
      next: () => {
        this.providerBusyId.set(null);
        this.cancelEditProvider();
        this.loadProviders();
        this.loadModels();
      },
      error: (error: unknown) => {
        this.providerBusyId.set(null);
        this.providersError.set(problemMessage(error));
      },
    });
  }

  protected confirmDeleteProvider(provider: Provider): void {
    this.confirmingProviderId.set(provider.id);
    this.providersError.set(null);
  }

  protected cancelDeleteProvider(): void {
    this.confirmingProviderId.set(null);
  }

  protected deleteProvider(provider: Provider): void {
    this.providerBusyId.set(provider.id);
    this.providersError.set(null);
    this.api.deleteProvider(provider.id).subscribe({
      next: () => {
        this.providerBusyId.set(null);
        this.confirmingProviderId.set(null);
        this.loadProviders();
        this.loadModels();
      },
      error: (error: unknown) => {
        this.providerBusyId.set(null);
        this.confirmingProviderId.set(null);
        this.providersError.set(problemMessage(error));
      },
    });
  }

  private validateModelDraft(draft: ModelCreateDraft): string | null {
    if (!draft.providerId) {
      return 'Choose the provider this model is served by.';
    }
    if (!draft.modelName.trim()) {
      return 'A model id is required — exactly as the provider spells it, for example "deepseek-chat".';
    }
    if (draft.capability === 'EMBEDDING') {
      const dimension = Number(draft.embeddingDimension);
      if (!Number.isInteger(dimension) || dimension <= 0) {
        return 'An embedding model needs its vector width, for example 1536.';
      }
    }
    if (draft.contextWindow.trim() && !(Number.isInteger(Number(draft.contextWindow)) && Number(draft.contextWindow) > 0)) {
      return 'The context window must be a positive whole number of tokens.';
    }
    if (draft.costPer1kIn.trim() && (Number.isNaN(Number(draft.costPer1kIn)) || Number(draft.costPer1kIn) < 0)) {
      return 'Prices must be numbers and cannot be negative.';
    }
    if (draft.costPer1kOut.trim() && (Number.isNaN(Number(draft.costPer1kOut)) || Number(draft.costPer1kOut) < 0)) {
      return 'Prices must be numbers and cannot be negative.';
    }
    return null;
  }

  /**
   * Structure only: a missing key is not a client-side error. A provider may legitimately be registered before
   * its key exists — the key can arrive from the environment on the next deployment — and an empty paste on edit
   * means "clear the stored key", which is exactly what the backend is told to do.
   */
  private validateProvider(draft: { code: string; label: string; baseUrl: string; apiKeyEnv: string }): string | null {
    if (!draft.code.trim()) {
      return 'A code is required — it is the stable identifier, for example "deepseek".';
    }
    if (!draft.label.trim()) {
      return 'A label is required, for example "DeepSeek".';
    }
    const baseUrl = draft.baseUrl.trim();
    if (!baseUrl) {
      return 'A base URL is required, for example "https://api.deepseek.com/v1".';
    }
    if (!baseUrl.startsWith('http://') && !baseUrl.startsWith('https://')) {
      return 'The base URL must start with http:// or https://.';
    }
    if (draft.apiKeyEnv.trim() && !/^[A-Za-z_][A-Za-z0-9_]{0,79}$/.test(draft.apiKeyEnv.trim())) {
      return 'An environment variable name must be a valid identifier, for example DEEPSEEK_API_KEY.';
    }
    return null;
  }

  /** The badge the credential column shows: where the key comes from, never the key itself. */
  protected credentialBadge(source: CredentialSource, hint: string | null, environmentVariable: string | null): {
    cls: string;
    text: string;
  } {
    switch (source) {
      case 'STORED':
        return { cls: 'badge badge--run-SUCCEEDED', text: hint ? `key ••••${hint}` : 'key stored' };
      case 'ENVIRONMENT':
        return {
          cls: 'badge badge--run-SUCCEEDED',
          text: environmentVariable ? `env ${environmentVariable}` : 'environment',
        };
      case 'NOT_REQUIRED':
        return { cls: 'badge badge--plain', text: 'no key needed' };
      case 'MISSING':
        return { cls: 'badge badge--run-PENDING', text: environmentVariable ? `missing (${environmentVariable})` : 'missing key' };
    }
  }

  // ------------------------------------------------------------------------------------------
  // Models
  // ------------------------------------------------------------------------------------------

  protected startCreateModel(): void {
    // The first provider is the likeliest choice, but the select is always there to change it.
    this.modelCreateDraft.set({ ...EMPTY_MODEL_DRAFT, providerId: String(this.providers()[0]?.id ?? '') });
    this.creatingModel.set(true);
    this.modelsError.set(null);
  }

  protected cancelCreateModel(): void {
    this.creatingModel.set(false);
  }

  protected onModelCreateInput(field: keyof ModelCreateDraft, event: Event): void {
    const target = event.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;
    this.modelCreateDraft.update((draft) => ({ ...draft, [field]: target.value }));
  }

  protected createModel(): void {
    const draft = this.modelCreateDraft();
    const problem = this.validateModelDraft(draft);
    if (problem) {
      this.modelsError.set(problem);
      return;
    }
    const request: CreateModelRequest = {
      providerId: Number(draft.providerId),
      modelName: draft.modelName.trim(),
      capability: draft.capability,
      kind: draft.kind,
      embeddingDimension: draft.capability === 'EMBEDDING' ? Number(draft.embeddingDimension) : null,
      // Empty boxes mean "use the default" rather than zero: the backend owns the defaults.
      contextWindow: draft.contextWindow.trim() ? Number(draft.contextWindow) : null,
      costPer1kIn: draft.costPer1kIn.trim() ? Number(draft.costPer1kIn) : null,
      costPer1kOut: draft.costPer1kOut.trim() ? Number(draft.costPer1kOut) : null,
      notes: draft.notes.trim() || null,
    };
    this.modelsError.set(null);
    this.api.createModel(request).subscribe({
      next: () => {
        this.creatingModel.set(false);
        this.loadModels();
        this.loadProviders();
      },
      error: (error: unknown) => {
        this.modelsError.set(problemMessage(error));
      },
    });
  }

  protected confirmDeleteModel(model: LlmModel): void {
    this.confirmingModelId.set(model.id);
    this.modelsError.set(null);
  }

  protected cancelDeleteModel(): void {
    this.confirmingModelId.set(null);
  }

  protected deleteModel(model: LlmModel): void {
    this.modelBusyId.set(model.id);
    this.modelsError.set(null);
    this.api.deleteModel(model.id).subscribe({
      next: () => {
        this.modelBusyId.set(null);
        this.confirmingModelId.set(null);
        this.loadModels();
        this.loadProviders();
      },
      error: (error: unknown) => {
        this.modelBusyId.set(null);
        this.confirmingModelId.set(null);
        this.modelsError.set(problemMessage(error));
      },
    });
  }

  protected onModelToggle(model: LlmModel, event: Event): void {
    const input = event.target as HTMLInputElement;
    this.editModel(model, { enabled: input.checked }, () => (input.checked = model.enabled));
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
