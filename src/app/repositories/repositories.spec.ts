import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AnalysisRun, Paged, Repository } from '../core/api/models';
import { Repositories } from './repositories';

const repository: Repository = {
  id: 1,
  name: 'java-fixture',
  sourceType: 'LOCAL',
  primaryLanguage: 'java',
  status: 'REGISTERED',
  createdAt: '2026-10-12T10:00:00Z',
};

function page<T>(items: T[]): Paged<T> {
  return { items, page: 0, size: 50, totalItems: items.length, totalPages: 1, hasNext: false };
}

function analysisRun(overrides: Partial<AnalysisRun> = {}): AnalysisRun {
  return {
    id: 7,
    repositoryId: 1,
    status: 'RUNNING',
    stage: 'PARSING',
    commitHash: null,
    parserVersion: '3.26.4',
    analyzerVersion: '7.0.0',
    rulesetHash: 'abc123',
    schemaVersion: 1,
    filesDiscovered: 4,
    filesParsed: 3,
    filesFailed: 1,
    findingsCount: 2,
    symbolsCount: 5,
    errorCode: null,
    errorSummary: null,
    jobStatus: 'RUNNING',
    jobErrorSummary: null,
    createdAt: '2026-10-12T10:00:00Z',
    startedAt: '2026-10-12T10:00:01Z',
    finishedAt: null,
    ...overrides,
  };
}

describe('Repositories', () => {
  let fixture: ComponentFixture<Repositories>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Repositories],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function create(): HTMLElement {
    fixture = TestBed.createComponent(Repositories);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function flushRepositories(items: Repository[] = []): void {
    http.expectOne((request) => request.url === '/api/repositories').flush(page(items));
    fixture.detectChanges();
  }

  function button(root: HTMLElement, text: string): HTMLButtonElement {
    const match = [...root.querySelectorAll('button')].find((candidate) => candidate.textContent?.trim() === text);
    if (!match) {
      throw new Error(`no button labelled "${text}"`);
    }
    return match as HTMLButtonElement;
  }

  it('shows a loading state, then an empty state', async () => {
    const root = create();
    expect(root.textContent).toContain('Loading repositories…');

    flushRepositories();
    await fixture.whenStable();
    expect(root.textContent).toContain('No repositories yet');
  });

  it('lists registered repositories', async () => {
    const root = create();
    flushRepositories([repository]);
    await fixture.whenStable();

    const rows = root.querySelectorAll('tbody tr');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('java-fixture');
    expect(rows[0].textContent).toContain('REGISTERED');
  });

  it('shows the message the backend sent when listing fails', async () => {
    const root = create();
    http.expectOne((request) => request.url === '/api/repositories').flush(
      { title: 'Unexpected error', detail: 'nothing to see', correlationId: 'c-1' },
      { status: 500, statusText: 'Server Error' },
    );
    await fixture.whenStable();

    expect(root.textContent).toContain('Unexpected error');
    expect(root.textContent).toContain('c-1');
  });

  it('registers a repository, refreshes the list and opens its runs', async () => {
    const root = create();
    flushRepositories();

    const path = root.querySelector<HTMLInputElement>('#repository-path')!;
    path.value = '/repos/java-fixture';
    path.dispatchEvent(new Event('input'));
    root.querySelector('form')!.dispatchEvent(new Event('submit'));

    const register = http.expectOne('/api/repositories');
    expect(register.request.method).toBe('POST');
    expect(register.request.body).toEqual({ localPath: '/repos/java-fixture', name: null });
    register.flush(repository);

    flushRepositories([repository]);
    http.expectOne((request) => request.url === '/api/repositories/1/analyses').flush(page([]));
    await fixture.whenStable();

    expect(root.textContent).toContain('Registered java-fixture.');
    expect(root.textContent).toContain('No analysis yet');
  });

  it('reports a rejected registration instead of pretending it worked', async () => {
    const root = create();
    flushRepositories();

    const path = root.querySelector<HTMLInputElement>('#repository-path')!;
    path.value = '/etc/passwd';
    path.dispatchEvent(new Event('input'));
    root.querySelector('form')!.dispatchEvent(new Event('submit'));

    http.expectOne('/api/repositories').flush(
      { title: 'Invalid repository path', detail: 'the path is outside the allowed roots' },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(root.textContent).toContain('Invalid repository path');
    expect(root.textContent).toContain('outside the allowed roots');
  });

  it('follows a run until it finishes, then stops polling', () => {
    vi.useFakeTimers();
    try {
      const root = create();
      flushRepositories([repository]);

      button(root, 'Analyse').click();
      const started = http.expectOne((request) => request.url === '/api/repositories/1/analyses');
      expect(started.request.method).toBe('POST');
      started.flush(analysisRun());
      fixture.detectChanges();

      http.expectOne((request) => request.url === '/api/repositories/1/analyses').flush(page([analysisRun()]));
      fixture.detectChanges();

      vi.advanceTimersByTime(1000);
      http
        .expectOne((request) => request.url === '/api/repositories/1/analyses')
        .flush(page([analysisRun({ status: 'SUCCEEDED', stage: 'COMPLETED' })]));
      fixture.detectChanges();
      expect(root.textContent).toContain('SUCCEEDED');

      vi.advanceTimersByTime(5000);
      expect(http.match((request) => request.url === '/api/repositories/1/analyses').length).toBe(0);
    } finally {
      vi.useRealTimers();
    }
  });
});
