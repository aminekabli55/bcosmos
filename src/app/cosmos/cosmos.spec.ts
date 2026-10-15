import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { Cosmos } from './cosmos';
import { GraphResponse, HotspotListResponse, Paged, Repository, AnalysisRun, SymbolImpactResponse } from '../core/api/models';

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
      id: 10,
      repositoryId: 1,
      status: 'SUCCEEDED',
      stage: 'COMPLETED',
      commitHash: 'a1b2c3d4e5f6',
      parserVersion: '1.0',
      analyzerVersion: '1.0',
      rulesetHash: 'hash123',
      schemaVersion: 1,
      filesDiscovered: 5,
      filesParsed: 5,
      filesFailed: 0,
      findingsCount: 3,
      symbolsCount: 10,
      errorCode: null,
      errorSummary: null,
      jobStatus: 'SUCCEEDED',
      jobErrorSummary: null,
      createdAt: '2026-10-10T12:05:00Z',
      startedAt: '2026-10-10T12:05:01Z',
      finishedAt: '2026-10-10T12:05:10Z',
    },
  ],
  page: 0,
  size: 30,
  totalItems: 1,
  totalPages: 1,
  hasNext: false,
};

const SAMPLE_GRAPH: GraphResponse = {
  analysisId: 10,
  nodes: [
    {
      id: '101',
      name: 'StringUtils',
      qualifiedName: 'org.apache.commons.lang3.StringUtils',
      kind: 'CLASS',
      filePath: 'src/main/java/StringUtils.java',
      lineCount: 350,
      findingsCount: 2,
      hotspotScore: 45.5,
      isExternal: false,
      packageName: 'org.apache.commons.lang3',
    },
    {
      id: '102',
      name: 'CharSequenceUtils',
      qualifiedName: 'org.apache.commons.lang3.CharSequenceUtils',
      kind: 'CLASS',
      filePath: 'src/main/java/CharSequenceUtils.java',
      lineCount: 120,
      findingsCount: 0,
      hotspotScore: 12.0,
      isExternal: false,
      packageName: 'org.apache.commons.lang3',
    },
  ],
  edges: [
    {
      id: 'e1',
      source: '101',
      target: '102',
      type: 'CALLS',
      status: 'RESOLVED',
      confidence: 1.0,
      evidenceFile: 'src/main/java/StringUtils.java',
      evidenceLine: 42,
    },
  ],
  truncated: false,
  totalNodes: 2,
  totalEdges: 1,
  unresolvedEdgesCount: 0,
  resolutionRate: 1.0,
};

const SAMPLE_HOTSPOTS: HotspotListResponse = {
  analysisId: 10,
  items: [
    {
      symbolId: 101,
      name: 'StringUtils',
      qualifiedName: 'org.apache.commons.lang3.StringUtils',
      filePath: 'src/main/java/StringUtils.java',
      lineCount: 350,
      findingsCount: 2,
      highSeverityCount: 1,
      inDegree: 5,
      outDegree: 3,
      score: 45.5,
    },
  ],
  heuristicFormula: 'score = (findings * 10) + (highSeverity * 20) + (inDegree * 5) + (lineCount * 0.05)',
};

const SAMPLE_IMPACT: SymbolImpactResponse = {
  symbolId: 101,
  qualifiedName: 'org.apache.commons.lang3.StringUtils',
  filePath: 'src/main/java/StringUtils.java',
  directDependentsCount: 1,
  transitiveDependentsCount: 2,
  directDependents: [
    {
      id: '103',
      name: 'ArrayUtils',
      qualifiedName: 'org.apache.commons.lang3.ArrayUtils',
      kind: 'CLASS',
      filePath: 'src/main/java/ArrayUtils.java',
      lineCount: 200,
      findingsCount: 0,
      hotspotScore: 8.0,
      isExternal: false,
      packageName: 'org.apache.commons.lang3',
    },
  ],
  transitiveDependents: [],
  affectedFindingsCount: 4,
  riskRating: 'HIGH',
};

describe('Cosmos component (Phase 8, spec section 8 & 9)', () => {
  let httpController: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Cosmos],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpController.verify();
  });

  function setupFixture() {
    const fixture = TestBed.createComponent(Cosmos);
    fixture.detectChanges();

    // 1. Initial listRepositories()
    const reqRepos = httpController.expectOne((req) => req.url === '/api/repositories');
    reqRepos.flush(SAMPLE_REPOS);
    fixture.detectChanges();

    // 2. listAnalyses()
    const reqRuns = httpController.expectOne((req) => req.url.startsWith('/api/repositories/1/analyses'));
    reqRuns.flush(SAMPLE_RUNS);
    fixture.detectChanges();

    // 3. getGraph() and getHotspots()
    const reqGraph = httpController.expectOne((req) => req.url === '/api/analyses/10/graph');
    reqGraph.flush(SAMPLE_GRAPH);

    const reqHotspots = httpController.expectOne((req) => req.url === '/api/analyses/10/hotspots');
    reqHotspots.flush(SAMPLE_HOTSPOTS);
    fixture.detectChanges();

    return fixture;
  }

  it('renders the Code Cosmos title and selectors', () => {
    const fixture = setupFixture();
    const title = fixture.debugElement.query(By.css('.page-title'));
    expect(title.nativeElement.textContent).toContain('Code Cosmos');

    const select = fixture.debugElement.query(By.css('#repo-select'));
    expect(select).toBeTruthy();
  });

  it('displays architectural hotspots with exact formula', () => {
    const fixture = setupFixture();
    const formula = fixture.debugElement.query(By.css('.formula-code'));
    expect(formula.nativeElement.textContent).toContain('score = (findings * 10)');

    const hotspotItems = fixture.debugElement.queryAll(By.css('.hotspot-item'));
    expect(hotspotItems.length).toBe(1);
    expect(hotspotItems[0].nativeElement.textContent).toContain('StringUtils');
    expect(hotspotItems[0].nativeElement.textContent).toContain('45.5');
  });

  it('switches between interactive canvas and accessible table view', () => {
    const fixture = setupFixture();
    const buttons = fixture.debugElement.queryAll(By.css('.toggle-btn'));
    expect(buttons.length).toBe(2);

    // Click accessible table button
    buttons[1].nativeElement.click();
    fixture.detectChanges();

    const table = fixture.debugElement.query(By.css('.symbols-table'));
    expect(table).toBeTruthy();

    const rows = fixture.debugElement.queryAll(By.css('.symbols-table tbody tr'));
    expect(rows.length).toBe(2);
    expect(rows[0].nativeElement.textContent).toContain('StringUtils');
    expect(rows[1].nativeElement.textContent).toContain('CharSequenceUtils');
  });

  it('filters nodes by search query in table view', () => {
    const fixture = setupFixture();
    fixture.componentInstance.setActiveView('table');
    fixture.detectChanges();

    fixture.componentInstance.onSearchChange('char');
    fixture.detectChanges();

    const rows = fixture.debugElement.queryAll(By.css('.symbols-table tbody tr'));
    expect(rows.length).toBe(1);
    expect(rows[0].nativeElement.textContent).toContain('CharSequenceUtils');
  });

  it('selects a symbol node and displays details in side inspector', () => {
    const fixture = setupFixture();
    const node = SAMPLE_GRAPH.nodes[0];
    fixture.componentInstance.selectNode(node);
    fixture.detectChanges();

    const inspector = fixture.debugElement.query(By.css('.symbol-inspector'));
    expect(inspector).toBeTruthy();
    expect(inspector.nativeElement.textContent).toContain('StringUtils');
    expect(inspector.nativeElement.textContent).toContain('org.apache.commons.lang3.StringUtils');
    expect(inspector.nativeElement.textContent).toContain('350 lines');
  });

  it('calculates blast radius / impact analysis and displays risk rating badge', () => {
    const fixture = setupFixture();
    const node = SAMPLE_GRAPH.nodes[0];
    fixture.componentInstance.selectNode(node);
    fixture.detectChanges();

    // Click Calculate Blast Radius button
    const blastBtn = fixture.debugElement.query(By.css('.btn-blast-radius'));
    blastBtn.nativeElement.click();
    fixture.detectChanges();

    const reqImpact = httpController.expectOne((req) => req.url === '/api/symbols/101/impact');
    expect(reqImpact.request.params.get('maxDepth')).toBe('3');
    reqImpact.flush(SAMPLE_IMPACT);
    fixture.detectChanges();

    const riskBadge = fixture.debugElement.query(By.css('.impact-risk-badge'));
    expect(riskBadge).toBeTruthy();
    expect(riskBadge.nativeElement.textContent).toContain('HIGH');

    const callers = fixture.debugElement.query(By.css('.dependent-list'));
    expect(callers.nativeElement.textContent).toContain('ArrayUtils');
  });
});
