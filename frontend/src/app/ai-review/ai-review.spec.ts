import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Finding, LlmModel, Paged, Suggestion } from '../core/api/models';
import { AiReview } from './ai-review';

function page<T>(items: T[]): Paged<T> {
  return { items, page: 0, size: 20, totalItems: items.length, totalPages: 1, hasNext: false };
}

function finding(overrides: Partial<Finding> = {}): Finding {
  return {
    id: 11,
    ruleId: 'EmptyCatchBlock',
    severity: 'MEDIUM',
    originalSeverity: '3',
    tool: 'pmd',
    toolVersion: '7.0.0',
    message: 'Avoid empty catch blocks',
    filePath: 'src/main/java/com/example/ingest/IngestRunner.java',
    startLine: 12,
    endLine: 15,
    symbolQualifiedName: 'com.example.ingest.IngestRunner.run',
    snippet: 'try {\n  reader.read();\n} catch (IOException ignored) {\n}',
    fingerprint: 'fp-11',
    triageStatus: 'OPEN',
    ...overrides,
  };
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
    notes: null,
    updatedAt: '2026-10-13T10:00:00Z',
    credentialSource: 'ENVIRONMENT',
    credentialHint: null,
    credentialEnvironmentVariable: 'GEMINI_API_KEY',
    ...overrides,
  };
}

function suggestion(overrides: Partial<Suggestion> = {}): Suggestion {
  return {
    id: 5,
    findingId: 11,
    modelId: 1,
    modelLabel: 'GEMINI/gemini-2.5-flash',
    promptVersion: 'suggest-fix.v1',
    status: 'PROPOSED',
    createdAt: '2026-10-14T09:00:00Z',
    reviewedAt: null,
    latencyMs: 2100,
    tokensIn: 1800,
    tokensOut: 320,
    answer: {
      summary: 'Log the read failure before continuing.',
      problemExplanation: 'The catch block swallows the read failure, so a bad record disappears silently.',
      citations: [
        {
          file: 'src/main/java/com/example/ingest/IngestRunner.java',
          startLine: 12,
          endLine: 15,
          why: 'the empty catch block',
        },
      ],
      proposedDiff:
        '@@ -12,4 +12,5 @@\n try {\n   reader.read();\n } catch (IOException ignored) {\n+  log.warn("skipping unreadable record", ignored);\n }',
      risks: ['logging may be noisy on very large files'],
      testsToRun: ['IngestRunnerTest'],
      uncertainties: [],
    },
    verdict: {
      schemaOk: true,
      evidenceOk: true,
      diffApplies: true,
      parses: true,
      targetFindingGone: true,
      allChecksPassed: true,
      newFindings: [],
      notes: ['compile not verified: the patch was parsed, not built'],
    },
    retrieval: {
      embedModel: 'GEMINI/gemini-embedding-001',
      chunksConsidered: 4,
      estimatedTokens: 620,
      truncated: false,
      ruleDescription: 'Avoid empty catch blocks',
      notes: ['one similar chunk was within the similarity floor'],
      items: [
        {
          kind: 'ENCLOSING',
          file: 'src/main/java/com/example/ingest/IngestRunner.java',
          startLine: 8,
          endLine: 20,
          qualifiedName: 'com.example.ingest.IngestRunner.run',
          text: 'void run() {\n  try {\n    reader.read();',
          similarity: null,
          estimatedTokens: 320,
        },
        {
          kind: 'SIMILAR',
          file: 'src/main/java/com/example/ingest/IngestRecovery.java',
          startLine: 30,
          endLine: 44,
          qualifiedName: 'com.example.ingest.IngestRecovery.retry',
          text: 'void retry() {',
          similarity: 0.83,
          estimatedTokens: 300,
        },
      ],
      index: { chunks: 9, reused: false, symbolsWithoutSource: 0, tokensIn: 0, latencyMs: 120 },
    },
    ...overrides,
  };
}

describe('AiReview', () => {
  let fixture: ComponentFixture<AiReview>;
  let http: HttpTestingController;

  function configure(params: Record<string, string>): void {
    TestBed.configureTestingModule({
      imports: [AiReview],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap(params)) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  }

  function create(): HTMLElement {
    fixture = TestBed.createComponent(AiReview);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function flushModels(items: LlmModel[] = [
    model(),
    model({ id: 2, capability: 'EMBEDDING', modelName: 'gemini-embedding-2', embeddingDimension: 1536 }),
  ]): void {
    http.expectOne((request) => request.url === '/api/models').flush(page(items));
    fixture.detectChanges();
  }

  function flushFinding(value: Finding = finding()): void {
    http.expectOne('/api/findings/11').flush(value);
    fixture.detectChanges();
  }

  function flushSuggestions(items: Suggestion[] = []): void {
    http.expectOne((request) => request.url === '/api/findings/11/suggestions').flush(page(items));
    fixture.detectChanges();
  }

  function button(root: HTMLElement, text: string): HTMLButtonElement {
    const match = [...root.querySelectorAll('button')].find((candidate) =>
      candidate.textContent?.trim().startsWith(text),
    );
    if (!match) {
      throw new Error(`no button starting with "${text}"`);
    }
    return match as HTMLButtonElement;
  }

  it('says no finding is selected when the URL has no finding id', async () => {
    configure({});
    const root = create();
    flushModels();
    await fixture.whenStable();

    expect(root.textContent).toContain('No finding selected');
    http.expectNone((request) => request.url === '/api/findings/11');
  });

  it('shows the finding, its verdict and the evidence the model was given', async () => {
    configure({ analysisId: '7', findingId: '11' });
    const root = create();
    flushModels();
    flushFinding();
    flushSuggestions([suggestion()]);
    await fixture.whenStable();

    expect(root.textContent).toContain('EmptyCatchBlock');
    expect(root.textContent).toContain('Avoid empty catch blocks');
    expect(root.textContent).toContain('Log the read failure');
    expect(root.textContent).toContain('reader.read();'); // the stored snippet is the evidence on screen
    expect(root.textContent).toContain('the patch was parsed, not built');
    expect(root.textContent).toContain('enclosing declaration');
    expect(root.textContent).toContain('IngestRecovery.retry');

    const checks = root.querySelectorAll('.check');
    expect(checks.length).toBe(6);
    expect(root.querySelectorAll('.check--failed').length).toBe(0);
    expect(root.textContent).toContain('passed');
  });

  it('shows a failed check as failed and lists the new findings the patch would introduce', async () => {
    configure({ analysisId: '7', findingId: '11' });
    const root = create();
    flushModels();
    flushFinding();
    flushSuggestions([
      suggestion({
        verdict: {
          schemaOk: true,
          evidenceOk: true,
          diffApplies: false,
          parses: false,
          targetFindingGone: false,
          allChecksPassed: false,
          newFindings: [{ ruleId: 'AvoidThrowingRawExceptionTypes', startLine: 14, message: 'raw exception' }],
          notes: ['the diff did not apply, so the patch checks were skipped'],
        },
      }),
    ]);
    await fixture.whenStable();

    // diff applies, parses, finding gone and no new findings: the fourth fails with the introduced finding.
    expect(root.querySelectorAll('.check--failed').length).toBe(4);
    expect(root.textContent).toContain('AvoidThrowingRawExceptionTypes');
    expect(root.textContent).toContain('the diff did not apply');
  });

  it('colours the diff without injecting any HTML the model wrote', async () => {
    configure({ analysisId: '7', findingId: '11' });
    const root = create();
    flushModels();
    flushFinding();
    flushSuggestions([
      suggestion({
        answer: {
          ...suggestion().answer,
          proposedDiff: '-old line\n+<img src=x onerror=alert(1)>',
        },
      }),
    ]);
    await fixture.whenStable();

    const added = [...root.querySelectorAll('.diff__line--added')];
    expect(added.length).toBe(1);
    expect(added[0].textContent).toContain('<img src=x onerror=alert(1)>');
    expect(root.querySelector('.diff img')).toBeNull();
    expect(root.querySelectorAll('.diff__line--removed').length).toBe(1);
  });

  it('preselects a usable model of each kind and asks it', async () => {
    configure({ analysisId: '7', findingId: '11' });
    const root = create();
    flushModels();
    flushFinding();
    flushSuggestions([]);
    await fixture.whenStable();

    expect(root.textContent).toContain('No suggestion yet');
    button(root, 'Suggest a fix').click();
    fixture.detectChanges();

    const request = http.expectOne('/api/findings/11/suggestions');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ modelId: 1, embeddingModelId: 2 });
    request.flush(suggestion());
    fixture.detectChanges();

    expect(root.textContent).toContain('Log the read failure');
    expect(root.textContent).toContain('PROPOSED');
  });

  it('accepts a suggestion and reloads the finding because triage moved', async () => {
    configure({ analysisId: '7', findingId: '11' });
    const root = create();
    flushModels();
    flushFinding();
    flushSuggestions([suggestion()]);
    await fixture.whenStable();

    button(root, 'Accept').click();
    fixture.detectChanges();

    const request = http.expectOne('/api/suggestions/5/review');
    expect(request.request.body).toEqual({ status: 'ACCEPTED' });
    request.flush(suggestion({ status: 'ACCEPTED', reviewedAt: '2026-10-14T09:05:00Z' }));
    http.expectOne('/api/findings/11').flush(finding({ triageStatus: 'ACCEPTED' }));
    fixture.detectChanges();

    expect(root.textContent).toContain('ACCEPTED');
    expect(root.textContent).toContain('Reviewed');
  });

  it('shows the backend reason when the model call is refused', async () => {
    configure({ analysisId: '7', findingId: '11' });
    const root = create();
    flushModels();
    flushFinding();
    flushSuggestions([]);
    await fixture.whenStable();

    button(root, 'Suggest a fix').click();
    fixture.detectChanges();

    http.expectOne('/api/findings/11/suggestions').flush(
      { title: 'AI layer is off', detail: 'enable the AI layer or set bcosmos.ai.enabled=true' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(root.textContent).toContain('AI layer is off');
    expect(root.textContent).toContain('enable the AI layer');
  });

  it('explains when no chat model is enabled', async () => {
    configure({ analysisId: '7', findingId: '11' });
    const root = create();
    flushModels([model({ capability: 'EMBEDDING', modelName: 'gemini-embedding-001' })]);
    flushFinding();
    flushSuggestions([]);
    await fixture.whenStable();

    expect(root.textContent).toContain('No chat model is enabled');
    http.expectNone((request) => request.url === '/api/findings/11/suggestions');
  });
});
