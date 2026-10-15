import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { History } from './history';
import { AnalysisRun, Paged, Repository, SnapshotComparisonResponse } from '../core/api/models';

const SAMPLE_REPOS: Paged<Repository> = {
  items: [
    {
      id: 1,
      name: 'commons-lang',
      sourceType: 'LOCAL',
      primaryLanguage: 'java',
      status: 'ANALYSED',
      createdAt: '2026-10-10T12:00:00Z',
    },
  ],
  page: 0,
  size: 50,
  totalItems: 1,
  totalPages: 1,
  hasNext: false,
};

const SAMPLE_RUNS: Paged<AnalysisRun> = {
  items: [
    {
      id: 2,
      repositoryId: 1,
      status: 'SUCCEEDED',
      stage: 'COMPLETED',
      commitHash: 'target-hash-222',
      parserVersion: '1.0',
      analyzerVersion: '1.0',
      rulesetHash: 'hash-rules-2',
      schemaVersion: 1,
      filesDiscovered: 5,
      filesParsed: 5,
      filesFailed: 0,
      findingsCount: 4,
      symbolsCount: 12,
      errorCode: null,
      errorSummary: null,
      jobStatus: 'SUCCEEDED',
      jobErrorSummary: null,
      createdAt: '2026-10-12T12:00:00Z',
      startedAt: '2026-10-12T12:00:01Z',
      finishedAt: '2026-10-12T12:00:10Z',
    },
    {
      id: 1,
      repositoryId: 1,
      status: 'SUCCEEDED',
      stage: 'COMPLETED',
      commitHash: 'base-hash-111',
      parserVersion: '1.0',
      analyzerVersion: '1.0',
      rulesetHash: 'hash-rules-1',
      schemaVersion: 1,
      filesDiscovered: 5,
      filesParsed: 5,
      filesFailed: 0,
      findingsCount: 5,
      symbolsCount: 12,
      errorCode: null,
      errorSummary: null,
      jobStatus: 'SUCCEEDED',
      jobErrorSummary: null,
      createdAt: '2026-10-10T12:00:00Z',
      startedAt: '2026-10-10T12:00:01Z',
      finishedAt: '2026-10-10T12:00:10Z',
    },
  ],
  page: 0,
  size: 50,
  totalItems: 2,
  totalPages: 1,
  hasNext: false,
};

const SAMPLE_COMPARISON: SnapshotComparisonResponse = {
  fromRun: {
    id: 1,
    repositoryId: 1,
    commitHash: 'base-hash-111',
    status: 'SUCCEEDED',
    filesDiscovered: 5,
    filesParsed: 5,
    filesFailed: 0,
    startedAt: '2026-10-10T12:00:01Z',
    finishedAt: '2026-10-10T12:00:10Z',
  },
  toRun: {
    id: 2,
    repositoryId: 1,
    commitHash: 'target-hash-222',
    status: 'SUCCEEDED',
    filesDiscovered: 5,
    filesParsed: 5,
    filesFailed: 0,
    startedAt: '2026-10-12T12:00:01Z',
    finishedAt: '2026-10-12T12:00:10Z',
  },
  newCount: 1,
  resolvedCount: 2,
  persistentCount: 3,
  uncertainCount: 1,
  transitions: [
    {
      id: 101,
      fromFindingId: null,
      toFindingId: 201,
      fingerprint: 'fp-new-1',
      ruleId: 'UnusedPrivateMethod',
      filePath: 'src/main/java/Utils.java',
      type: 'NEW',
      confidence: 1.0,
      reason: 'Finding was absent in baseline snapshot; newly introduced in target snapshot.',
    },
    {
      id: 102,
      fromFindingId: 105,
      toFindingId: null,
      fingerprint: 'fp-res-1',
      ruleId: 'AvoidCatchingGenericException',
      filePath: 'src/main/java/Parser.java',
      type: 'RESOLVED',
      confidence: 1.0,
      reason: 'Rule evaluated cleanly on parsed file and did not fire; issue verified as resolved.',
    },
    {
      id: 103,
      fromFindingId: 106,
      toFindingId: 206,
      fingerprint: 'fp-pers-1',
      ruleId: 'EmptyCatchBlock',
      filePath: 'src/main/java/Engine.java',
      type: 'PERSISTENT',
      confidence: 1.0,
      reason: 'Rule fired in both snapshots with identical fingerprint; finding remains open.',
    },
    {
      id: 104,
      fromFindingId: 107,
      toFindingId: null,
      fingerprint: 'fp-unc-1',
      ruleId: 'EmptyCatchBlock',
      filePath: 'src/main/java/Broken.java',
      type: 'UNCERTAIN',
      confidence: 0.5,
      reason: 'Target file failed to parse; cannot safely confirm if finding was resolved (spec section 4.2).',
    },
  ],
};

describe('History component (Phase 8, spec section 4.2 & 8)', () => {
  let httpController: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [History],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpController.verify();
  });

  function setupFixture() {
    const fixture = TestBed.createComponent(History);
    fixture.detectChanges();

    // 1. Initial listRepositories()
    const reqRepos = httpController.expectOne((req) => req.url === '/api/repositories');
    reqRepos.flush(SAMPLE_REPOS);
    fixture.detectChanges();

    // 2. listAnalyses()
    const reqRuns = httpController.expectOne((req) => req.url.startsWith('/api/repositories/1/analyses'));
    reqRuns.flush(SAMPLE_RUNS);
    fixture.detectChanges();

    // 3. compareSnapshots() triggered by auto-select of runs
    const reqCompare = httpController.expectOne((req) => req.url === '/api/analyses/compare');
    expect(reqCompare.request.params.get('from')).toBe('1');
    expect(reqCompare.request.params.get('to')).toBe('2');
    reqCompare.flush(SAMPLE_COMPARISON);
    fixture.detectChanges();

    return fixture;
  }

  it('renders page header and snapshot selector bar', () => {
    const fixture = setupFixture();
    const title = fixture.debugElement.query(By.css('.page-title'));
    expect(title.nativeElement.textContent).toContain('Snapshot History');

    const fromSelect = fixture.debugElement.query(By.css('#from-run'));
    expect(fromSelect).toBeTruthy();
  });

  it('displays summary stat cards with exact transition counts', () => {
    const fixture = setupFixture();
    const newCard = fixture.debugElement.query(By.css('.stat-card--new .stat-value'));
    expect(newCard.nativeElement.textContent.trim()).toBe('1');

    const resCard = fixture.debugElement.query(By.css('.stat-card--resolved .stat-value'));
    expect(resCard.nativeElement.textContent.trim()).toBe('2');

    const persCard = fixture.debugElement.query(By.css('.stat-card--persistent .stat-value'));
    expect(persCard.nativeElement.textContent.trim()).toBe('3');

    const uncCard = fixture.debugElement.query(By.css('.stat-card--uncertain .stat-value'));
    expect(uncCard.nativeElement.textContent.trim()).toBe('1');
  });

  it('filters transitions by tab', () => {
    const fixture = setupFixture();
    // Default ALL tab: 4 rows
    let rows = fixture.debugElement.queryAll(By.css('.transitions-table tbody tr'));
    expect(rows.length).toBe(4);

    // Switch to UNCERTAIN tab
    const uncTab = fixture.debugElement.query(By.css('.tab-btn--uncertain'));
    uncTab.nativeElement.click();
    fixture.detectChanges();

    rows = fixture.debugElement.queryAll(By.css('.transitions-table tbody tr'));
    expect(rows.length).toBe(1);
    expect(rows[0].nativeElement.textContent).toContain('Broken.java');
    expect(rows[0].nativeElement.textContent).toContain('Target file failed to parse');
  });

  it('filters transitions by search input', () => {
    const fixture = setupFixture();
    fixture.componentInstance.searchQuery.set('GenericException');
    fixture.detectChanges();

    const rows = fixture.debugElement.queryAll(By.css('.transitions-table tbody tr'));
    expect(rows.length).toBe(1);
    expect(rows[0].nativeElement.textContent).toContain('AvoidCatchingGenericException');
    expect(rows[0].nativeElement.textContent).toContain('RESOLVED');
  });
});
