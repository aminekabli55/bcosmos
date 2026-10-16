import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AiSettings, LlmModel, Paged, Provider } from '../core/api/models';
import { Settings } from './settings';

function page<T>(items: T[]): Paged<T> {
  return { items, page: 0, size: 50, totalItems: items.length, totalPages: 1, hasNext: false };
}

function model(overrides: Partial<LlmModel> = {}): LlmModel {
  return {
    id: 1,
    providerId: 10,
    provider: 'gemini',
    providerLabel: 'Google Gemini',
    providerProtocol: 'GEMINI',
    modelName: 'gemini-2.5-flash',
    kind: 'HOSTED',
    capability: 'CHAT',
    embeddingDimension: null,
    enabled: true,
    defaultModel: true,
    contextWindow: 128000,
    costPer1kIn: 0.0003,
    costPer1kOut: 0.0025,
    notes: 'default row',
    updatedAt: '2026-10-13T10:00:00Z',
    credentialSource: 'STORED',
    credentialHint: '1234',
    credentialEnvironmentVariable: 'GEMINI_API_KEY',
    ...overrides,
  };
}

function provider(overrides: Partial<Provider> = {}): Provider {
  return {
    id: 10,
    code: 'gemini',
    label: 'Google Gemini',
    protocol: 'GEMINI',
    baseUrl: 'https://generativelanguage.googleapis.com',
    apiKeyRequired: true,
    credentialSource: 'STORED',
    credentialHint: '1234',
    credentialEnvironmentVariable: 'GEMINI_API_KEY',
    modelCount: 1,
    updatedAt: '2026-10-13T10:00:00Z',
    ...overrides,
  };
}

function aiSettings(overrides: Partial<AiSettings> = {}): AiSettings {
  return { enabled: true, propertyDefault: true, overridden: false, updatedAt: null, ...overrides };
}

describe('Settings', () => {
  let fixture: ComponentFixture<Settings>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Settings],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function create(): HTMLElement {
    fixture = TestBed.createComponent(Settings);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function flushAi(settings: AiSettings = aiSettings()): void {
    http.expectOne((request) => request.url === '/api/settings/ai').flush(settings);
    fixture.detectChanges();
  }

  function flushProviders(items: Provider[] = [provider()]): void {
    http.expectOne((request) => request.url === '/api/providers').flush(page(items));
    fixture.detectChanges();
  }

  function flushModels(items: LlmModel[] = [model()]): void {
    http.expectOne((request) => request.url === '/api/models').flush(page(items));
    fixture.detectChanges();
  }

  /** The three loads the constructor makes, in the order it makes them. */
  function flushAll(): HTMLElement {
    const root = create();
    flushAi();
    flushProviders();
    flushModels();
    return root;
  }

  function button(root: HTMLElement, text: string, scope?: HTMLElement): HTMLButtonElement {
    const match = [...(scope ?? root).querySelectorAll('button')].find(
      (candidate) => candidate.textContent?.trim() === text,
    );
    if (!match) {
      throw new Error(`no button labelled "${text}"`);
    }
    return match as HTMLButtonElement;
  }

  function toggle(root: HTMLElement, selector: string): HTMLInputElement {
    return root.querySelector<HTMLInputElement>(selector)!;
  }

  /** The model registry is the third card on the screen; the provider card is the second. */
  function card(root: HTMLElement, index: number): HTMLElement {
    return root.querySelectorAll('section')[index] as HTMLElement;
  }

  function fill(selector: string, value: string): void {
    const input = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  /** A `<select>` only fires `change`, never `input`. */
  function select(selector: string, value: string): void {
    const box = fixture.nativeElement.querySelector(selector) as HTMLSelectElement;
    box.value = value;
    box.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('shows the master switch state and the registry', () => {
    const root = flushAll();

    expect(root.textContent).toContain('AI layer on');
    expect(root.textContent).toContain('gemini-2.5-flash');
    expect(root.textContent).toContain('Google Gemini');
    expect(root.textContent).toContain('default row');
    expect(root.textContent).toContain('key ••••1234');
    expect(toggle(root, '.ai-panel .switch__input').checked).toBe(true);
  });

  it('says when the deployment property decides the switch', () => {
    const root = create();
    flushAi();
    flushProviders();
    flushModels();

    expect(root.textContent).toContain('Following the deployment default');
    expect(root.querySelector('.ai-panel__controls button')).toBeNull();
  });

  it('stores the switch when it is turned off', () => {
    const root = flushAll();

    const master = toggle(root, '.ai-panel .switch__input');
    master.checked = false;
    master.dispatchEvent(new Event('change'));

    const request = http.expectOne('/api/settings/ai');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ enabled: false });
    request.flush(aiSettings({ enabled: false, overridden: true, updatedAt: '2026-10-14T09:00:00Z' }));
    fixture.detectChanges();

    expect(root.textContent).toContain('AI layer off');
    expect(root.textContent).toContain('stored in the database');
    expect(root.textContent).toContain('Follow deployment default');
  });

  it('clears the override so the deployment default decides again', () => {
    const root = create();
    flushAi(aiSettings({ enabled: false, overridden: true, updatedAt: '2026-10-14T09:00:00Z' }));
    flushProviders();
    flushModels();

    button(root, 'Follow deployment default').click();

    const request = http.expectOne('/api/settings/ai');
    expect(request.request.body).toEqual({ enabled: null });
    request.flush(aiSettings());
    fixture.detectChanges();

    expect(root.textContent).toContain('AI layer on');
    expect(root.textContent).toContain('Following the deployment default');
  });

  it('registers a provider, sending the pasted key once and only ever showing a hint', () => {
    const root = flushAll();

    button(root, 'Add provider').click();
    fixture.detectChanges();
    fill('#provider-code', 'deepseek');
    fill('#provider-label', 'DeepSeek');
    fill('#provider-url', 'https://api.deepseek.com/v1');
    fill('#provider-env', 'DEEPSEEK_API_KEY');
    fill('#provider-key', 'sk-super-secret');

    button(root, 'Add provider').click();

    const request = http.expectOne('/api/providers');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      code: 'deepseek',
      label: 'DeepSeek',
      protocol: 'OPENAI_COMPATIBLE',
      baseUrl: 'https://api.deepseek.com/v1',
      apiKeyEnv: 'DEEPSEEK_API_KEY',
      apiKeyRequired: true,
      apiKey: 'sk-super-secret',
    });
    request.flush(provider({ id: 11, code: 'deepseek', label: 'DeepSeek', credentialHint: 'cret' }));

    flushProviders([
      provider(),
      provider({ id: 11, code: 'deepseek', label: 'DeepSeek', credentialHint: 'cret', modelCount: 0 }),
    ]);

    expect(root.textContent).toContain('DeepSeek');
    expect(root.textContent).toContain('key ••••cret');
    expect(root.textContent).not.toContain('sk-super-secret');
  });

  it('refuses a base URL that is not a URL without calling the API', () => {
    const root = flushAll();

    button(root, 'Add provider').click();
    fixture.detectChanges();
    fill('#provider-code', 'local');
    fill('#provider-label', 'Local');
    fill('#provider-url', 'localhost:8000');

    button(root, 'Add provider').click();
    fixture.detectChanges();

    expect(root.textContent).toContain('must start with http:// or https://');
    http.expectNone((request) => request.url === '/api/providers' && request.method === 'POST');
  });

  it('edits a provider without sending the key box when it was left empty', () => {
    const root = flushAll();

    button(root, 'Edit').click();
    fixture.detectChanges();
    const label = root.querySelector<HTMLInputElement>('#edit-label-10')!;
    label.value = 'Google Gemini (free tier)';
    label.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    button(root, 'Save').click();

    const request = http.expectOne('/api/providers/10');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      label: 'Google Gemini (free tier)',
      protocol: 'GEMINI',
      baseUrl: 'https://generativelanguage.googleapis.com',
      apiKeyEnv: 'GEMINI_API_KEY',
      apiKeyRequired: true,
      apiKey: null,
    });
    request.flush(provider({ label: 'Google Gemini (free tier)' }));
    flushProviders([provider({ label: 'Google Gemini (free tier)' })]);
    flushModels();

    expect(root.textContent).toContain('Google Gemini (free tier)');
  });

  it('deletes a provider only after the confirmation', () => {
    const root = create();
    flushAi();
    flushProviders([provider({ modelCount: 0 })]);
    flushModels([model({ enabled: false, defaultModel: false })]);

    button(root, 'Delete').click();
    fixture.detectChanges();

    // First press only asks; nothing has been sent yet.
    expect(root.textContent).toContain('Delete gemini?');
    http.expectNone((request) => request.url === '/api/providers/10');

    button(root, 'Cancel').click();
    fixture.detectChanges();
    button(root, 'Delete').click();
    fixture.detectChanges();

    // Find the confirm button inside the row (it is the only remaining "Delete gemini?" affordance's sibling).
    const confirm = [...root.querySelectorAll('button')].find(
      (candidate) => candidate.textContent?.trim() === 'Delete',
    ) as HTMLButtonElement;
    confirm.click();

    const request = http.expectOne('/api/providers/10');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
    flushProviders([]);
    flushModels();

    expect(root.textContent).toContain('No providers registered');
  });

  it('shows why a provider could not be deleted', () => {
    const root = flushAll();

    button(root, 'Delete').click();
    fixture.detectChanges();
    [...root.querySelectorAll('button')]
      .find((candidate) => candidate.textContent?.trim() === 'Delete')!
      .click();

    http
      .expectOne('/api/providers/10')
      .flush({ title: 'Provider in use', detail: 'deepseek still has 3 models' }, {
        status: 409,
        statusText: 'Conflict',
      });
    fixture.detectChanges();

    expect(root.textContent).toContain('deepseek still has 3 models');
  });

  it('warns in the confirmation while models still point at the provider', () => {
    const root = create();
    flushAi();
    flushProviders([provider({ modelCount: 3 })]);
    flushModels();

    button(root, 'Delete').click();
    fixture.detectChanges();

    expect(root.textContent).toContain('Still used by 3 model(s)');
    // The provider is never actually sent a delete while models point at it.
    http.expectNone((request) => request.url === '/api/providers/10');
  });

  it('enables a model through the registry', () => {
    const root = create();
    flushAi();
    flushProviders();
    flushModels([model({ enabled: false, defaultModel: false })]);

    const enabled = toggle(root, '.model-enabled');
    expect(enabled.checked).toBe(false);
    enabled.checked = true;
    enabled.dispatchEvent(new Event('change'));

    const request = http.expectOne('/api/models/1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ enabled: true });
    request.flush(model({ enabled: true, defaultModel: false }));
    fixture.detectChanges();

    expect(toggle(root, '.model-enabled').checked).toBe(true);
  });

  it('promotes a model to default and reloads the list', () => {
    const root = create();
    flushAi();
    flushProviders();
    flushModels([model({ defaultModel: false })]);

    button(root, 'Make default').click();

    const request = http.expectOne('/api/models/1');
    expect(request.request.body).toEqual({ defaultModel: true });
    request.flush(model({ defaultModel: true }));

    flushModels([model({ defaultModel: true })]);
    expect(root.textContent).toContain('default');
  });

  it('saves corrected prices and notes', () => {
    const root = create();
    flushAi();
    flushProviders();
    flushModels([model({ id: 2, providerId: 11, provider: 'openai', providerLabel: 'OpenAI', providerProtocol: 'OPENAI_COMPATIBLE', modelName: 'gpt-4o-mini', defaultModel: false })]);

    button(root, 'Edit', card(root, 2)).click();
    fixture.detectChanges();

    const context = root.querySelector<HTMLInputElement>('#context-2')!;
    context.value = '256000';
    context.dispatchEvent(new Event('input'));
    const costIn = root.querySelector<HTMLInputElement>('#cost-in-2')!;
    costIn.value = '0.00015';
    costIn.dispatchEvent(new Event('input'));
    const costOut = root.querySelector<HTMLInputElement>('#cost-out-2')!;
    costOut.value = '0.0006';
    costOut.dispatchEvent(new Event('input'));
    const notes = root.querySelector<HTMLInputElement>('#notes-2')!;
    notes.value = 'cheaper long-context model';
    notes.dispatchEvent(new Event('input'));

    button(root, 'Save').click();

    const request = http.expectOne('/api/models/2');
    expect(request.request.body).toEqual({
      contextWindow: 256000,
      costPer1kIn: 0.00015,
      costPer1kOut: 0.0006,
      notes: 'cheaper long-context model',
    });
    request.flush(model({ id: 2, modelName: 'gpt-4o-mini' }));
    fixture.detectChanges();

    expect(root.querySelector('#context-2')).toBeNull();
  });

  it('refuses a nonsense context window without calling the API', () => {
    const root = create();
    flushAi();
    flushProviders();
    flushModels([model({ id: 2, modelName: 'gpt-4o-mini' })]);

    button(root, 'Edit', card(root, 2)).click();
    fixture.detectChanges();
    const context = root.querySelector<HTMLInputElement>('#context-2')!;
    context.value = '0';
    context.dispatchEvent(new Event('input'));

    button(root, 'Save').click();
    fixture.detectChanges();

    expect(root.textContent).toContain('positive whole number');
    http.expectNone((request) => request.url === '/api/models/2');
  });

  it('puts the model switch back when the backend refuses the change', () => {
    const root = create();
    flushAi();
    flushProviders();
    flushModels([model({ enabled: false, defaultModel: false })]);

    const input = toggle(root, '.model-enabled');
    input.checked = true;
    input.dispatchEvent(new Event('change'));

    http.expectOne('/api/models/1').flush(
      {
        title: 'Model not usable',
        detail: 'no API key for provider deepseek: paste one in Settings or set DEEPSEEK_API_KEY',
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    // The binding is only written when its value changes, so the control is restored by hand.
    expect(input.checked).toBe(false);
    expect(root.textContent).toContain('no API key for provider deepseek');
  });

  it('puts the AI switch back when the backend refuses the change', () => {
    const root = flushAll();

    const master = toggle(root, '.ai-panel .switch__input');
    master.checked = false;
    master.dispatchEvent(new Event('change'));

    http
      .expectOne('/api/settings/ai')
      .flush({ title: 'Unexpected error', correlationId: 'c-4' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(master.checked).toBe(true);
    expect(root.textContent).toContain('Unexpected error');
    expect(root.textContent).toContain('c-4');
  });

  it('shows the message the backend sent when the registry fails to load', () => {
    const root = create();
    flushAi();
    flushProviders();
    http
      .expectOne((request) => request.url === '/api/models')
      .flush({ title: 'Unexpected error', detail: 'nothing to see', correlationId: 'c-9' }, {
        status: 500,
        statusText: 'Server Error',
      });
    fixture.detectChanges();

    expect(root.textContent).toContain('Unexpected error');
    expect(root.textContent).toContain('c-9');
  });

  it('shows where each credential comes from and never a key itself', () => {
    const root = create();
    flushAi();
    flushProviders([
      provider(),
      provider({
        id: 12,
        code: 'deepseek',
        label: 'DeepSeek',
        protocol: 'OPENAI_COMPATIBLE',
        baseUrl: 'https://api.deepseek.com/v1',
        credentialSource: 'MISSING',
        credentialHint: null,
        credentialEnvironmentVariable: 'DEEPSEEK_API_KEY',
      }),
      provider({
        id: 13,
        code: 'ollama',
        label: 'Ollama',
        protocol: 'OPENAI_COMPATIBLE',
        baseUrl: 'http://localhost:11434/v1',
        apiKeyRequired: false,
        credentialSource: 'NOT_REQUIRED',
        credentialHint: null,
        credentialEnvironmentVariable: null,
      }),
    ]);
    flushModels();

    expect(root.textContent).toContain('key ••••1234');
    expect(root.textContent).toContain('missing (DEEPSEEK_API_KEY)');
    expect(root.textContent).toContain('no key needed');
    expect(root.textContent).not.toMatch(/sk-[A-Za-z]{6}/);
  });

  it('registers a model with the id the vendor spells it, on a chosen provider', () => {
    const root = flushAll();

    button(root, 'Add model').click();
    fixture.detectChanges();
    fill('#new-model-name', 'deepseek-chat');

    button(root, 'Add model').click();

    const request = http.expectOne('/api/models');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      providerId: 10,
      modelName: 'deepseek-chat',
      capability: 'CHAT',
      kind: 'HOSTED',
      // An empty box means "let the backend use its default", not zero.
      embeddingDimension: null,
      contextWindow: null,
      costPer1kIn: null,
      costPer1kOut: null,
      notes: null,
    });
    request.flush(model({ id: 7, providerId: 10, modelName: 'deepseek-chat', enabled: false, defaultModel: false }));

    flushModels([model({ id: 7, providerId: 10, modelName: 'deepseek-chat', enabled: false, defaultModel: false })]);
    flushProviders();

    expect(root.textContent).toContain('deepseek-chat');
  });

  it('asks for the vector width before registering an embedding model', () => {
    const root = flushAll();

    button(root, 'Add model').click();
    fixture.detectChanges();
    fill('#new-model-name', 'text-embedding-3-small');
    select('#new-model-capability', 'EMBEDDING');
    expect(root.querySelector('#new-model-dim')).not.toBeNull();

    button(root, 'Add model').click();
    fixture.detectChanges();

    expect(root.textContent).toContain('vector width');
    http.expectNone((request) => request.url === '/api/models' && request.method === 'POST');
  });

  it('refuses an empty model id without calling the API', () => {
    const root = flushAll();

    button(root, 'Add model').click();
    fixture.detectChanges();
    button(root, 'Add model').click();
    fixture.detectChanges();

    expect(root.textContent).toContain('model id is required');
    http.expectNone((request) => request.url === '/api/models' && request.method === 'POST');
  });

  it('deletes a non-default model only after the confirmation', () => {
    const root = create();
    flushAi();
    flushProviders();
    flushModels([model({ id: 7, modelName: 'gpt-4o-mini', defaultModel: false })]);
    const registry = card(root, 2);

    button(root, 'Delete', registry).click();
    fixture.detectChanges();
    expect(root.textContent).toContain('Delete gpt-4o-mini?');
    http.expectNone((request) => request.url === '/api/models/7');

    button(root, 'Delete', registry).click();

    const request = http.expectOne('/api/models/7');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
    flushModels([]);
    flushProviders();

    expect(root.textContent).toContain('No models registered');
  });

  it('does not offer to delete the default model', () => {
    const root = flushAll();
    const registry = card(root, 2);

    // The default chat row is the one thing the API always needs; the button is not even rendered for it.
    expect([...registry.querySelectorAll('button')].map((candidate) => candidate.textContent?.trim())).not.toContain(
      'Delete',
    );
  });
});
