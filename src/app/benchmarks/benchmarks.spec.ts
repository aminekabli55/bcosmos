import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { Benchmarks } from './benchmarks';
import { BenchmarkRun } from '../core/api/models';

const SAMPLE_RUNS: BenchmarkRun[] = [
  {
    id: 1,
    benchmarkId: 'bench-001',
    datasetVersion: 'v0.1',
    conditionName: 'baseline-gemini',
    modelName: 'gemini-1.5-pro',
    promptVersion: 'suggest-v3',
    retrievalStrategy: 'hnsw-top10',
    sampleSize: 50,
    schemaValidRate: 0.96,
    diffAppliesRate: 0.88,
    parsesRate: 0.90,
    targetResolvedRate: 0.72,
    noNewFindingsRate: 0.82,
    medianDiffSize: 12,
    avgLatencyMs: 4200,
    totalTokens: 180000,
    estimatedCostUsd: 0.54,
    metricsJson: '{"bleu": 0.31}',
    notes: 'Baseline run',
    createdAt: '2026-10-01T10:00:00Z',
  },
  {
    id: 2,
    benchmarkId: 'bench-002',
    datasetVersion: 'v0.1',
    conditionName: 'finetuned-v1',
    modelName: 'gemini-1.5-flash',
    promptVersion: 'suggest-v3',
    retrievalStrategy: 'hnsw-top10',
    sampleSize: 50,
    schemaValidRate: 0.98,
    diffAppliesRate: 0.80,
    parsesRate: 0.84,
    targetResolvedRate: 0.64,
    noNewFindingsRate: 0.78,
    medianDiffSize: 15,
    avgLatencyMs: 2100,
    totalTokens: 130000,
    estimatedCostUsd: 0.09,
    metricsJson: null,
    notes: null,
    createdAt: '2026-10-02T10:00:00Z',
  },
];

describe('Benchmarks component', () => {
  let httpController: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Benchmarks],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpController.verify();
  });

  function createFixture() {
    const fixture = TestBed.createComponent(Benchmarks);
    fixture.detectChanges();
    // Flush the initial GET /api/benchmarks
    httpController.expectOne('/api/benchmarks').flush(SAMPLE_RUNS);
    fixture.detectChanges();
    return fixture;
  }

  it('renders the page title', () => {
    const fixture = createFixture();
    const h1 = fixture.debugElement.query(By.css('.bench-title'));
    expect(h1.nativeElement.textContent).toContain('Benchmark Leaderboard');
  });

  it('displays one row per run', () => {
    const fixture = createFixture();
    const rows = fixture.debugElement.queryAll(By.css('.bench-row'));
    expect(rows.length).toBe(2);
  });

  it('shows rank 1 for the highest targetResolvedRate', () => {
    const fixture = createFixture();
    const rows = fixture.debugElement.queryAll(By.css('.bench-row'));
    const firstRank = rows[0].query(By.css('.col-rank'));
    expect(firstRank.nativeElement.textContent.trim()).toBe('1');
    // Run 1 has 0.72 > Run 2's 0.64 so it should be rank 1
    const firstModel = rows[0].query(By.css('.model-label'));
    expect(firstModel.nativeElement.textContent).toContain('gemini-1.5-pro');
  });

  it('expands a row on expand button click', async () => {
    const fixture = createFixture();
    const expandBtn = fixture.debugElement.query(By.css('.expand-btn'));
    expandBtn.nativeElement.click();
    fixture.detectChanges();
    const detail = fixture.debugElement.query(By.css('.bench-detail'));
    expect(detail).toBeTruthy();
  });

  it('collapses the row on a second click', async () => {
    const fixture = createFixture();
    const expandBtn = fixture.debugElement.query(By.css('.expand-btn'));
    expandBtn.nativeElement.click();
    fixture.detectChanges();
    expandBtn.nativeElement.click();
    fixture.detectChanges();
    const detail = fixture.debugElement.query(By.css('.bench-detail'));
    expect(detail).toBeNull();
  });

  it('shows empty state when no runs are returned', () => {
    const fixture = TestBed.createComponent(Benchmarks);
    fixture.detectChanges();
    httpController.expectOne('/api/benchmarks').flush([]);
    fixture.detectChanges();
    const empty = fixture.debugElement.query(By.css('.bench-empty'));
    expect(empty).toBeTruthy();
  });

  it('shows error state on HTTP failure', () => {
    const fixture = TestBed.createComponent(Benchmarks);
    fixture.detectChanges();
    httpController
      .expectOne('/api/benchmarks')
      .flush({ message: 'Internal server error' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    const error = fixture.debugElement.query(By.css('.bench-error'));
    expect(error).toBeTruthy();
  });

  it('populates the dataset-version select with unique versions', () => {
    const fixture = createFixture();
    const options = fixture.debugElement.queryAll(By.css('.bench-select option'));
    // "All dataset versions" + "v0.1"
    expect(options.length).toBe(2);
    expect(options[1].nativeElement.textContent.trim()).toBe('v0.1');
  });
});
