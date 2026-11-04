import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Paged } from '../core/api/models';
import { Analytics } from './analytics';

function page<T>(items: T[], overrides: Partial<Paged<T>> = {}): Paged<T> {
  return { items, page: 0, size: 50, totalItems: items.length, totalPages: 1, hasNext: false, ...overrides };
}

const OVERVIEW = {
  available: true,
  datasetVersion: 'ds-2026-10-14-001',
  generatedAt: '2026-10-14T09:30:00Z',
  source: 'bcosmos-load-gold',
  drilldownCommits: 200,
  repositories: 3,
  rules: 9,
  findings: 412,
  measuredLines: 184000,
  parseFailures: 5,
  triples: 77,
  severityMix: [
    { severity: 'CRITICAL' as const, findings: 4 },
    { severity: 'HIGH' as const, findings: 30 },
  ],
  topRules: [],
  pipelineStats: [
    { key: 'silver_rows', value: 900 },
    { key: 'rejected_unsupported_schema', value: 12 },
  ],
  repositoryRefs: [
    { repositoryId: 1, name: 'java-fixture', sourceType: 'LOCAL', licenseSpdx: null },
    { repositoryId: 2, name: 'commons-lang', sourceType: 'MINED', licenseSpdx: 'Apache-2.0' },
  ],
  splits: [],
};

const RULE_ROWS = [
  {
    repositoryId: 1,
    ruleId: 'EmptyCatchBlock',
    severity: 'MEDIUM' as const,
    findings: 40,
    filesAffected: 7,
    measuredLines: 12000,
    findingsPerKloc: 3.3,
    parseFailures: 0,
    rank: 1,
  },
  {
    repositoryId: 1,
    ruleId: 'UnusedPrivateMethod',
    severity: 'LOW' as const,
    findings: 12,
    filesAffected: 5,
    measuredLines: 12000,
    findingsPerKloc: null,
    parseFailures: 0,
    rank: 2,
  },
];

const TREND_ROWS = [
  {
    repositoryId: 1,
    bucketStart: '2026-09-28',
    findings: 20,
    filesAnalysed: 12,
    measuredLines: 9000,
    densityPerKloc: 2.2,
    parseFailures: 1,
  },
];

const TIME_TO_FIX_ROWS = [
  { repositoryId: 1, ruleId: 'EmptyCatchBlock', commitsUntilResolved: 1, resolvedPairs: 4, observedIntroductions: 4 },
  { repositoryId: 1, ruleId: 'EmptyCatchBlock', commitsUntilResolved: 3, resolvedPairs: 2, observedIntroductions: 2 },
  { repositoryId: 1, ruleId: 'EmptyCatchBlock', commitsUntilResolved: null, resolvedPairs: 6, observedIntroductions: 0 },
];

const HOTSPOT_ROWS = [
  {
    repositoryId: 1,
    filePath: 'src/main/java/com/example/Ingest.java',
    score: 0.712,
    rankInRepository: 1,
    churn: 5,
    churnScore: 0.9,
    churnContribution: 0.315,
    complexityProxy: 3,
    complexityScore: 0.6,
    complexityContribution: 0.15,
    openFindings: 4,
    openFindingsScore: 0.8,
    openFindingsContribution: 0.32,
    dependencyMeasured: false,
    weightSumMeasured: 1,
    latestCommitHash: 'abcdef1234567890',
    weights: 'churn=0.35,complexity=0.25,dependency=0.0,open_findings=0.4',
  },
];

const FINDING_ROWS = [
  {
    id: 91,
    repositoryId: 1,
    commitHash: 'abcdef1234567890',
    filePath: 'src/main/java/com/example/Ingest.java',
    side: 'AFTER',
    ruleId: 'EmptyCatchBlock',
    severity: 'MEDIUM' as const,
    symbol: 'com.example.Ingest.run',
    fingerprint: 'fp-91',
    startLine: 12,
    endLine: 14,
    lineCount: 240,
    occurredAt: '2026-10-01T08:00:00Z',
    snippet: 'catch (IOException ignored) {}',
  },
];

describe('Analytics', () => {
  let fixture: ComponentFixture<Analytics>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Analytics],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function create(): HTMLElement {
    fixture = TestBed.createComponent(Analytics);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function flushOverview(overview: Record<string, unknown> = OVERVIEW): HTMLElement {
    http.expectOne('/api/analytics/overview').flush(overview);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  /** Flushes the four aggregate panels and the dataset split list the screen asks for after an overview. */
  function flushAggregates(): void {
    http.expectOne((request) => request.url === '/api/analytics/rules').flush(page(RULE_ROWS));
    http.expectOne((request) => request.url === '/api/analytics/trends').flush(page(TREND_ROWS));
    http.expectOne((request) => request.url === '/api/analytics/time-to-fix').flush(page(TIME_TO_FIX_ROWS));
    http.expectOne((request) => request.url === '/api/analytics/hotspots').flush(page(HOTSPOT_ROWS));
    http.expectOne((request) => request.url === '/api/analytics/dataset/splits').flush(
      page([
        {
          split: 'train',
          triples: 60,
          repositories: 2,
          rules: 5,
          snippetChanged: 48,
          introductionsUnobserved: 6,
          maxChangedLines: 40,
        },
        {
          split: 'validation',
          triples: 17,
          repositories: 1,
          rules: 3,
          snippetChanged: 15,
          introductionsUnobserved: 1,
          maxChangedLines: 22,
        },
      ]),
    );
    fixture.detectChanges();
  }

  it('explains how to produce a snapshot when none exists and asks for nothing else', () => {
    const native = create();

    http.expectOne('/api/analytics/overview').flush({
      available: false,
      datasetVersion: null,
      generatedAt: null,
      source: null,
      drilldownCommits: null,
      repositories: 0,
      rules: 0,
      findings: 0,
      measuredLines: 0,
      parseFailures: 0,
      triples: 0,
      severityMix: [],
      topRules: [],
      pipelineStats: [],
      repositoryRefs: [],
      splits: [],
    });
    fixture.detectChanges();

    expect(native.textContent).toContain('No gold snapshot yet');
    expect(native.textContent).toContain('batch.load_gold');
    // Nothing was charted, so nothing else was requested.
    http.verify();
  });

  it('shows the snapshot every figure belongs to and the headline numbers', () => {
    const native = create();
    flushOverview();
    flushAggregates();

    const text = native.textContent ?? '';
    expect(text).toContain('ds-2026-10-14-001');
    expect(text).toContain('bcosmos-load-gold');
    expect(text).toContain('last 200 commits per repository');
    expect(text).toContain('412');
    expect(text).toContain('184,000');
    expect(text).toContain('Parse failures');
    // The provenance line names the filter the panels below were built with.
    expect(native.querySelector('.provenance')?.textContent).toContain('all repositories');
  });

  it('prints a rule row with its density and drills down to the records behind it', () => {
    const native = create();
    flushOverview();
    flushAggregates();

    const rows = [...native.querySelectorAll('table tbody tr')];
    const ruleRow = rows.find((row) => row.textContent?.includes('EmptyCatchBlock'));
    expect(ruleRow?.textContent).toContain('3.3');
    // A rule that reported no line count reads "not measured", never "0.0".
    expect(native.textContent).toContain('not measured');

    const recordsButton = [...(ruleRow?.querySelectorAll('button') ?? [])].find((button) =>
      button.textContent?.includes('Records'),
    );
    recordsButton?.dispatchEvent(new MouseEvent('click'));
    fixture.detectChanges();

    const drill = http.expectOne((request) => request.url === '/api/analytics/findings');
    expect(drill.request.params.get('repositoryId')).toBe('1');
    expect(drill.request.params.get('ruleId')).toBe('EmptyCatchBlock');
    expect(drill.request.params.get('size')).toBe('50');
    drill.flush(page(FINDING_ROWS));
    fixture.detectChanges();

    const drillText = native.querySelector('.drill')?.textContent ?? '';
    expect(drillText).toContain('EmptyCatchBlock');
    expect(drillText).toContain('src/main/java/com/example/Ingest.java:12-14');
    expect(drillText).toContain('1 match the filter');
  });

  it('filters every panel to one repository when the filter changes', () => {
    const native = create();
    flushOverview();
    flushAggregates();

    const select = native.querySelector<HTMLSelectElement>('#analytics-repository');
    select!.value = '2';
    select!.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    for (const url of ['/api/analytics/rules', '/api/analytics/trends', '/api/analytics/time-to-fix', '/api/analytics/hotspots']) {
      const request = http.expectOne((candidate) => candidate.url === url);
      expect(request.request.params.get('repositoryId')).toBe('2');
      request.flush(page([]));
    }
    fixture.detectChanges();

    expect(native.querySelector('.provenance')?.textContent).toContain('commons-lang');
  });

  it('averages only the measured commits-to-fix distances and reports the lower bound separately', () => {
    const native = create();
    flushOverview();
    flushAggregates();

    const stats = [...native.querySelectorAll('.inline-stats div')].map((entry) => entry.textContent ?? '');
    // (4 introductions × 1 commit + 2 × 3 commits) / 6 introductions = 1.67, and the six lower-bound pairs stay out.
    expect(stats.find((entry) => entry.includes('Mean observed distance'))).toContain('1.67 commits');
    expect(stats.find((entry) => entry.includes('Already broken'))).toContain('6 pairs');
    expect(native.textContent).toContain('lower bound');
  });

  it('shows an unmeasured hotspot component as unmeasured instead of as a zero', () => {
    const native = create();
    flushOverview();
    flushAggregates();

    const why = [...native.querySelectorAll('table tbody button')].find((button) =>
      button.textContent?.includes('Why'),
    );
    why?.dispatchEvent(new MouseEvent('click'));
    fixture.detectChanges();

    const components = [...native.querySelectorAll('.component')];
    expect(components.length).toBe(4);
    const dependency = components.find((component) => component.textContent?.includes('Dependency impact'));
    expect(dependency?.classList.contains('component--unmeasured')).toBe(true);
    expect(dependency?.textContent).toContain('not measured');
    expect(dependency?.textContent).toContain('weight 0 until the relationship table');

    const churn = components.find((component) => component.textContent?.includes('Churn'));
    expect(churn?.textContent).toContain('5 commits');
    expect(churn?.textContent).toContain('percentile 90%');
  });

  it('lists the dataset splits and the repositories inside a split on demand', () => {
    const native = create();
    flushOverview();
    flushAggregates();

    expect(native.textContent).toContain('validation');
    const repos = [...native.querySelectorAll('table tbody button')].find((button) =>
      button.textContent?.includes('Repositories'),
    );
    repos?.dispatchEvent(new MouseEvent('click'));
    fixture.detectChanges();

    const request = http.expectOne((candidate) => candidate.url === '/api/analytics/dataset/repositories');
    expect(request.request.params.get('split')).toBe('train');
    request.flush(
      page([
        { split: 'train', repositoryId: 2, name: 'commons-lang', licenseSpdx: 'Apache-2.0', triples: 45 },
      ]),
    );
    fixture.detectChanges();

    const detail = native.querySelector('.split-repositories');
    expect(detail?.textContent).toContain('commons-lang');
    expect(detail?.textContent).toContain('Apache-2.0');
    expect(detail?.textContent).toContain('45 triples');
  });

  it('reports the pipeline statistics of the snapshot in words', () => {
    const native = create();
    flushOverview();
    flushAggregates();

    const stats = native.querySelector('.stat-list')?.textContent ?? '';
    expect(stats).toContain('Rows in silver');
    expect(stats).toContain('900');
    expect(stats).toContain('Rejected: unsupported schema');
    expect(stats).toContain('12');
  });
});
