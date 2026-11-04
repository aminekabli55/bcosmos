import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AnalysisRun, FileRecord, Finding, Paged } from '../core/api/models';
import { Quality } from './quality';

function page<T>(items: T[]): Paged<T> {
  return { items, page: 0, size: 25, totalItems: items.length, totalPages: 1, hasNext: false };
}

function analysisRun(overrides: Partial<AnalysisRun> = {}): AnalysisRun {
  return {
    id: 7,
    repositoryId: 1,
    status: 'SUCCEEDED',
    stage: 'COMPLETED',
    commitHash: null,
    parserVersion: '3.26.4',
    analyzerVersion: '7.0.0',
    rulesetHash: 'abc123',
    schemaVersion: 1,
    filesDiscovered: 6,
    filesParsed: 5,
    filesFailed: 1,
    findingsCount: 2,
    symbolsCount: 9,
    errorCode: null,
    errorSummary: null,
    jobStatus: 'SUCCEEDED',
    jobErrorSummary: null,
    createdAt: '2026-10-12T10:00:00Z',
    startedAt: '2026-10-12T10:00:01Z',
    finishedAt: '2026-10-12T10:00:05Z',
    ...overrides,
  };
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

function failure(): FileRecord {
  return {
    id: 3,
    filePath: 'src/main/java/com/example/broken/SyntaxError.java',
    language: 'java',
    parseStatus: 'FAILED',
    parseError: 'unexpected token at line 7',
    lineCount: 12,
  };
}

describe('Quality', () => {
  let fixture: ComponentFixture<Quality>;
  let http: HttpTestingController;

  function configure(analysisId: string | null): void {
    TestBed.configureTestingModule({
      imports: [Quality],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap(analysisId === null ? {} : { analysisId })) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  }

  function create(): HTMLElement {
    fixture = TestBed.createComponent(Quality);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function flushFindings(items: Finding[] = []): void {
    http.expectOne((request) => request.url === '/api/analyses/7/findings').flush(page(items));
  }

  function flushFailures(items: FileRecord[] = []): void {
    http.expectOne((request) => request.url === '/api/analyses/7/files').flush(page(items));
  }

  it('asks for a run when the URL has no analysis id', async () => {
    configure(null);
    const root = create();
    await fixture.whenStable();

    expect(root.textContent).toContain('No analysis run selected');
    expect(http.match(() => true).length).toBe(0);
  });

  it('shows the run counters, its findings and the files that failed to parse', async () => {
    configure('7');
    const root = create();
    http.expectOne('/api/analyses/7').flush(analysisRun());
    flushFindings([finding()]);
    flushFailures([failure()]);
    await fixture.whenStable();

    expect(root.textContent).toContain('Analysis run #7');
    expect(root.textContent).toContain('SUCCEEDED');
    expect(root.textContent).toContain('COMPLETED');
    expect(root.textContent).toContain('EmptyCatchBlock');
    expect(root.textContent).toContain('Avoid empty catch blocks');
    expect(root.textContent).toContain('SyntaxError.java');
    expect(root.textContent).toContain('unexpected token at line 7');
  });

  it('shows the stored snippet as the evidence for a selected finding', async () => {
    configure('7');
    const root = create();
    http.expectOne('/api/analyses/7').flush(analysisRun());
    flushFindings([finding()]);
    flushFailures();
    await fixture.whenStable();

    const row = root.querySelector<HTMLTableRowElement>('tbody tr')!;
    row.click();
    await fixture.whenStable();

    const preview = root.querySelector('.preview')!;
    expect(preview.textContent).toContain('fp-11');
    expect(preview.textContent).toContain('src/main/java/com/example/ingest/IngestRunner.java:12');
    const numbers = [...preview.querySelectorAll('.source__number')].map((cell) => cell.textContent?.trim());
    expect(numbers).toEqual(['12', '13', '14', '15']);
    expect(preview.querySelector('.source__line--first')?.textContent).toContain('try {');
  });

  it('asks the backend for the filtered page when a severity is chosen', async () => {
    configure('7');
    const root = create();
    http.expectOne('/api/analyses/7').flush(analysisRun());
    flushFindings();
    flushFailures();

    const select = root.querySelector<HTMLSelectElement>('#filter-severity')!;
    select.value = 'CRITICAL';
    select.dispatchEvent(new Event('change'));

    const filtered = http.expectOne((request) => request.url === '/api/analyses/7/findings');
    expect(filtered.request.params.get('severity')).toBe('CRITICAL');
    expect(filtered.request.params.get('page')).toBe('0');
    filtered.flush(page([]));
    await fixture.whenStable();

    expect(root.textContent).toContain('No findings match these filters');
  });

  it('shows the problem detail when the findings request fails', async () => {
    configure('7');
    const root = create();
    http.expectOne('/api/analyses/7').flush(analysisRun());
    http
      .expectOne((request) => request.url === '/api/analyses/7/findings')
      .flush({ title: 'Analysis run not found' }, { status: 404, statusText: 'Not Found' });
    flushFailures();
    await fixture.whenStable();

    expect(root.textContent).toContain('Analysis run not found');
  });

  it('polls a run that is still moving and reloads the evidence when it finishes', () => {
    vi.useFakeTimers();
    try {
      configure('7');
      create();
      http.expectOne('/api/analyses/7').flush(analysisRun({ status: 'RUNNING', stage: 'ANALYSIS', finishedAt: null }));
      flushFindings();
      flushFailures();

      vi.advanceTimersByTime(1000);
      const polled = http.expectOne('/api/analyses/7');
      polled.flush(analysisRun({ status: 'RUNNING', stage: 'PERSISTENCE', findingsCount: 3 }));
      fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).textContent).toContain('PERSISTENCE');

      vi.advanceTimersByTime(1000);
      http.expectOne('/api/analyses/7').flush(analysisRun());
      // Findings and parse failures were empty while the run was moving; both are reloaded once it is done.
      flushFindings([finding()]);
      flushFailures([failure()]);
      fixture.detectChanges();

      vi.advanceTimersByTime(5000);
      expect(http.match((request) => request.url === '/api/analyses/7').length).toBe(0);

      const root = fixture.nativeElement as HTMLElement;
      expect(root.textContent).toContain('SUCCEEDED');
      expect(root.textContent).toContain('EmptyCatchBlock');
    } finally {
      vi.useRealTimers();
    }
  });
});
