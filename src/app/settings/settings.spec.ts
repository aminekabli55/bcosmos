import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AiSettings, LlmModel, Paged } from '../core/api/models';
import { Settings } from './settings';

function page<T>(items: T[]): Paged<T> {
  return { items, page: 0, size: 50, totalItems: items.length, totalPages: 1, hasNext: false };
}

function model(overrides: Partial<LlmModel> = {}): LlmModel {
  return {
    id: 1,
    provider: 'GEMINI',
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
    credentialConfigured: true,
    credentialEnvironmentVariable: 'GEMINI_API_KEY',
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

  function flushModels(items: LlmModel[] = [model()]): void {
    http.expectOne((request) => request.url === '/api/models').flush(page(items));
    fixture.detectChanges();
  }

  function button(root: HTMLElement, text: string): HTMLButtonElement {
    const match = [...root.querySelectorAll('button')].find((candidate) => candidate.textContent?.trim() === text);
    if (!match) {
      throw new Error(`no button labelled "${text}"`);
    }
    return match as HTMLButtonElement;
  }

  function toggle(root: HTMLElement, selector: string): HTMLInputElement {
    return root.querySelector<HTMLInputElement>(selector)!;
  }

  it('shows the master switch state and the registry', () => {
    const root = create();
    flushAi(aiSettings());
    flushModels();

    expect(root.textContent).toContain('AI layer on');
    expect(root.textContent).toContain('gemini-2.5-flash');
    expect(root.textContent).toContain('GEMINI');
    expect(root.textContent).toContain('default row');
    expect(root.textContent).toContain('key present');
    expect(toggle(root, '.ai-panel .switch__input').checked).toBe(true);
  });

  it('says when the deployment property decides the switch', () => {
    const root = create();
    flushAi();
    flushModels();

    expect(root.textContent).toContain('Following the deployment default');
    expect(root.querySelector('.ai-panel__controls button')).toBeNull();
  });

  it('stores the switch when it is turned off', () => {
    const root = create();
    flushAi();
    flushModels();

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
    flushModels();

    button(root, 'Follow deployment default').click();

    const request = http.expectOne('/api/settings/ai');
    expect(request.request.body).toEqual({ enabled: null });
    request.flush(aiSettings());
    fixture.detectChanges();

    expect(root.textContent).toContain('AI layer on');
    expect(root.textContent).toContain('Following the deployment default');
  });

  it('enables a model through the registry', () => {
    const root = create();
    flushAi();
    flushModels([model({ id: 2, modelName: 'gpt-4o-mini', enabled: false, defaultModel: false })]);

    const enabled = toggle(root, '.model-enabled');
    expect(enabled.checked).toBe(false);
    enabled.checked = true;
    enabled.dispatchEvent(new Event('change'));

    const request = http.expectOne('/api/models/2');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ enabled: true });
    request.flush(model({ id: 2, modelName: 'gpt-4o-mini', enabled: true, defaultModel: false }));
    fixture.detectChanges();

    expect(toggle(root, '.model-enabled').checked).toBe(true);
  });

  it('promotes a model to default and reloads the list', () => {
    const root = create();
    flushAi();
    flushModels([model({ id: 2, modelName: 'gpt-4o-mini', defaultModel: false })]);

    button(root, 'Make default').click();

    const request = http.expectOne('/api/models/2');
    expect(request.request.body).toEqual({ defaultModel: true });
    request.flush(model({ id: 2, modelName: 'gpt-4o-mini', defaultModel: true }));

    flushModels([
      model({ id: 2, modelName: 'gpt-4o-mini', defaultModel: true }),
      model({ defaultModel: false }),
    ]);
    expect(root.textContent).toContain('default');
  });

  it('saves corrected prices and notes', () => {
    const root = create();
    flushAi();
    flushModels([model({ id: 2, modelName: 'gpt-4o-mini' })]);

    button(root, 'Edit').click();
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
    flushModels([model({ id: 2, modelName: 'gpt-4o-mini' })]);

    button(root, 'Edit').click();
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
    flushModels([model({ id: 2, modelName: 'gpt-4o-mini', enabled: false, defaultModel: false })]);

    const input = toggle(root, '.model-enabled');
    input.checked = true;
    input.dispatchEvent(new Event('change'));

    http.expectOne('/api/models/2').flush(
      {
        title: 'Model not usable',
        detail: 'no API key for provider OPENAI_COMPATIBLE: set OPENAI_API_KEY in the environment',
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    // The binding is only written when its value changes, so the control is restored by hand.
    expect(input.checked).toBe(false);
    expect(root.textContent).toContain('no API key for provider OPENAI_COMPATIBLE');
  });

  it('puts the AI switch back when the backend refuses the change', () => {
    const root = create();
    flushAi();
    flushModels();

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
});
