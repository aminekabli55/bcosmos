import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AiSettings,
  AnalysisRun,
  AnalyticsFilter,
  AnalyticsFinding,
  AnalyticsOverview,
  BenchmarkRun,
  CreateBenchmarkRunRequest,
  CreateModelRequest,
  CreateProviderRequest,
  DatasetRepository,
  DatasetSplit,
  FileRecord,
  Finding,
  FindingFilter,
  Hotspot,
  LlmModel,
  ModelCompareRequest,
  ModelCompareResponse,
  ModelEdit,
  Paged,
  ParseStatus,
  Provider,
  RegisterRepositoryRequest,
  Repository,
  RuleStat,
  SuggestRequest,
  Suggestion,
  SuggestionStatus,
  TimeToFixBucket,
  TrendPoint,
  UpdateProviderRequest,
  CorpusIngestRequest,
  CorpusIngestResponse,
  CorpusStatusResponse,
  GraphResponse,
  SymbolImpactResponse,
  HotspotListResponse,
  SnapshotComparisonResponse,
  CommitListResponse,
  CommitReviewResponse,
  CreateCommitReviewRequest,
} from './models';

/**
 * The only place the UI talks HTTP.
 *
 * The base path is relative, so the Angular dev server proxies `/api` to the backend
 * (`frontend/proxy.conf.json`). Serving the built bundle from another origin needs that origin configured here.
 */
@Injectable({ providedIn: 'root' })
export class BcosmosApi {
  private readonly http = inject(HttpClient);

  listRepositories(page = 0, size = 50): Observable<Paged<Repository>> {
    return this.http.get<Paged<Repository>>('/api/repositories', { params: this.page(page, size) });
  }

  registerRepository(request: RegisterRepositoryRequest): Observable<Repository> {
    return this.http.post<Repository>('/api/repositories', request);
  }

  /**
   * Forgets a registered repository together with its runs and everything derived from them (ADR 0037). The folder
   * on disk is not touched: what is removed is the registration, not the project.
   */
  deleteRepository(repositoryId: number): Observable<void> {
    return this.http.delete<void>(`/api/repositories/${repositoryId}`);
  }

  startAnalysis(repositoryId: number): Observable<AnalysisRun> {
    return this.http.post<AnalysisRun>(`/api/repositories/${repositoryId}/analyses`, {});
  }

  listAnalyses(repositoryId: number, page = 0, size = 20): Observable<Paged<AnalysisRun>> {
    return this.http.get<Paged<AnalysisRun>>(`/api/repositories/${repositoryId}/analyses`, {
      params: this.page(page, size),
    });
  }

  getAnalysis(analysisId: number): Observable<AnalysisRun> {
    return this.http.get<AnalysisRun>(`/api/analyses/${analysisId}`);
  }

  listFindings(analysisId: number, filter: FindingFilter): Observable<Paged<Finding>> {
    let params = this.page(filter.page, filter.size);
    if (filter.severity) {
      params = params.set('severity', filter.severity);
    }
    if (filter.rule) {
      params = params.set('rule', filter.rule);
    }
    if (filter.file) {
      params = params.set('file', filter.file);
    }
    return this.http.get<Paged<Finding>>(`/api/analyses/${analysisId}/findings`, { params });
  }

  listFiles(analysisId: number, parseStatus?: ParseStatus, page = 0, size = 50): Observable<Paged<FileRecord>> {
    let params = this.page(page, size);
    if (parseStatus) {
      params = params.set('parseStatus', parseStatus);
    }
    return this.http.get<Paged<FileRecord>>(`/api/analyses/${analysisId}/files`, { params });
  }

  /** One finding by id: the AI Review screen is reached by link and cannot assume a loaded list. */
  getFinding(findingId: number): Observable<Finding> {
    return this.http.get<Finding>(`/api/findings/${findingId}`);
  }

  /**
   * Asks one model for a validated fix suggestion (spec v2 section 8). This is the one call in the UI that can take
   * tens of seconds: it embeds the run's code on the first use, retrieves evidence and waits for a model.
   */
  suggestFix(findingId: number, request: SuggestRequest = {}): Observable<Suggestion> {
    return this.http.post<Suggestion>(`/api/findings/${findingId}/suggestions`, request);
  }

  listSuggestions(findingId: number, page = 0, size = 20): Observable<Paged<Suggestion>> {
    return this.http.get<Paged<Suggestion>>(`/api/findings/${findingId}/suggestions`, {
      params: this.page(page, size),
    });
  }

  getSuggestion(suggestionId: number): Observable<Suggestion> {
    return this.http.get<Suggestion>(`/api/suggestions/${suggestionId}`);
  }

  /** Accept, reject or defer a suggestion; the finding's triage state moves with it. */
  reviewSuggestion(suggestionId: number, status: SuggestionStatus): Observable<Suggestion> {
    return this.http.post<Suggestion>(`/api/suggestions/${suggestionId}/review`, { status });
  }

  listModels(page = 0, size = 50): Observable<Paged<LlmModel>> {
    return this.http.get<Paged<LlmModel>>('/api/models', { params: this.page(page, size) });
  }

  /** The provider registry: every endpoint the gateway may call, paginated like every other list. */
  listProviders(page = 0, size = 50): Observable<Paged<Provider>> {
    return this.http.get<Paged<Provider>>('/api/providers', { params: this.page(page, size) });
  }

  /** Registers a vendor: code, protocol, base URL and optionally a key that is encrypted before it is stored. */
  createProvider(request: CreateProviderRequest): Observable<Provider> {
    return this.http.post<Provider>('/api/providers', request);
  }

  /** Edits a provider; a blank `apiKey` clears the stored key and an omitted one leaves it alone. */
  updateProvider(providerId: number, edit: UpdateProviderRequest): Observable<Provider> {
    return this.http.put<Provider>(`/api/providers/${providerId}`, edit);
  }

  /** Removes a provider no model references; refused with 409 while one still points at it. */
  deleteProvider(providerId: number): Observable<void> {
    return this.http.delete<void>(`/api/providers/${providerId}`);
  }

  /** Registers a model on a provider: the id as the vendor spells it; a pasted row starts disabled. */
  createModel(request: CreateModelRequest): Observable<LlmModel> {
    return this.http.post<LlmModel>('/api/models', request);
  }

  /** Removes a registry row; the default row of a capability is refused with 409 until another one is default. */
  deleteModel(modelId: number): Observable<void> {
    return this.http.delete<void>(`/api/models/${modelId}`);
  }

  /** Enables, disables, promotes or corrects one registry row. */
  updateModel(modelId: number, edit: ModelEdit): Observable<LlmModel> {
    return this.http.put<LlmModel>(`/api/models/${modelId}`, edit);
  }

  getAiSettings(): Observable<AiSettings> {
    return this.http.get<AiSettings>('/api/settings/ai');
  }

  /** Stores the AI master switch; `null` clears the override and follows the deployment property again. */
  updateAiSettings(enabled: boolean | null): Observable<AiSettings> {
    return this.http.put<AiSettings>('/api/settings/ai', { enabled });
  }

  ingestCorpus(request: CorpusIngestRequest): Observable<CorpusIngestResponse> {
    return this.http.post<CorpusIngestResponse>('/api/corpus/ingest', request);
  }

  getCorpusStatus(): Observable<CorpusStatusResponse> {
    return this.http.get<CorpusStatusResponse>('/api/corpus/status');
  }

  /* ---------------------------------------------------------------- Analytics (Phase 5) */

  /** The current gold snapshot and its headline numbers; `available` is false before the first Spark load. */
  getAnalyticsOverview(): Observable<AnalyticsOverview> {
    return this.http.get<AnalyticsOverview>('/api/analytics/overview');
  }

  listRuleStats(filter: AnalyticsFilter = {}): Observable<Paged<RuleStat>> {
    return this.http.get<Paged<RuleStat>>('/api/analytics/rules', { params: this.analyticsParams(filter) });
  }

  listTrends(filter: AnalyticsFilter = {}): Observable<Paged<TrendPoint>> {
    return this.http.get<Paged<TrendPoint>>('/api/analytics/trends', {
      params: this.analyticsParams(filter, 100).set('page', filter.page ?? 0),
    });
  }

  listTimeToFix(filter: AnalyticsFilter = {}): Observable<Paged<TimeToFixBucket>> {
    return this.http.get<Paged<TimeToFixBucket>>('/api/analytics/time-to-fix', {
      params: this.analyticsParams(filter, 50),
    });
  }

  listHotspots(filter: AnalyticsFilter = {}): Observable<Paged<Hotspot>> {
    return this.http.get<Paged<Hotspot>>('/api/analytics/hotspots', {
      params: this.analyticsParams(filter, 10),
    });
  }

  listDatasetSplits(): Observable<Paged<DatasetSplit>> {
    return this.http.get<Paged<DatasetSplit>>('/api/analytics/dataset/splits', {
      params: this.page(0, 10),
    });
  }

  listDatasetRepositories(split?: string | null): Observable<Paged<DatasetRepository>> {
    let params = this.page(0, 50);
    if (split) {
      params = params.set('split', split);
    }
    return this.http.get<Paged<DatasetRepository>>('/api/analytics/dataset/repositories', { params });
  }

  /** The records behind an aggregate: every chart's drill-down uses this one call. */
  listAnalyticsFindings(filter: AnalyticsFilter = {}): Observable<Paged<AnalyticsFinding>> {
    return this.http.get<Paged<AnalyticsFinding>>('/api/analytics/findings', { params: this.analyticsParams(filter) });
  }

  /* ---------------------------------------------------------------- Benchmarks (Phase 6) */

  /** Leaderboard ranked by target-resolved rate; pass `datasetVersion` to filter. */
  listBenchmarkRuns(datasetVersion?: string | null): Observable<BenchmarkRun[]> {
    let params = new HttpParams();
    if (datasetVersion) {
      params = params.set('datasetVersion', datasetVersion);
    }
    return this.http.get<BenchmarkRun[]>('/api/benchmarks', { params });
  }

  /** Records a new evaluation run (called by `ai-lab/eval/run_eval.py` via the REST API). */
  recordBenchmarkRun(request: CreateBenchmarkRunRequest): Observable<BenchmarkRun> {
    return this.http.post<BenchmarkRun>('/api/benchmarks', request);
  }

  /** Side-by-side model comparison for a given finding. */
  compareModels(findingId: number, request: ModelCompareRequest): Observable<ModelCompareResponse> {
    return this.http.post<ModelCompareResponse>(`/api/findings/${findingId}/compare-models`, request);
  }

  /** Code Cosmos: symbol dependency & call graph bounded response (spec section 8 & 9). */
  getGraph(analysisId: number, maxNodes = 200): Observable<GraphResponse> {
    const params = new HttpParams().set('maxNodes', maxNodes);
    return this.http.get<GraphResponse>(`/api/analyses/${analysisId}/graph`, { params });
  }

  /** Code Cosmos: blast radius and transitive dependent impact analysis for a symbol. */
  getImpact(symbolId: number, maxDepth = 3): Observable<SymbolImpactResponse> {
    const params = new HttpParams().set('maxDepth', maxDepth);
    return this.http.get<SymbolImpactResponse>(`/api/symbols/${symbolId}/impact`, { params });
  }

  /** Code Cosmos: hotspot heuristic ranking for an analysis run. */
  getHotspots(analysisId: number, limit = 20): Observable<HotspotListResponse> {
    const params = new HttpParams().set('limit', limit);
    return this.http.get<HotspotListResponse>(`/api/analyses/${analysisId}/hotspots`, { params });
  }

  /** History: compare two snapshot runs and return finding transitions (NEW, RESOLVED, PERSISTENT, UNCERTAIN). */
  compareSnapshots(fromRunId: number, toRunId: number): Observable<SnapshotComparisonResponse> {
    const params = new HttpParams().set('from', fromRunId).set('to', toRunId);
    return this.http.get<SnapshotComparisonResponse>('/api/analyses/compare', { params });
  }

  /* ---------------------------------------------------------------- Commit Review (Phase 9) */

  /** Lists recent commits for a repository together with any existing commit review ID. */
  listCommits(repositoryId: number, limit = 30): Observable<CommitListResponse> {
    const params = new HttpParams().set('limit', limit);
    return this.http.get<CommitListResponse>(`/api/repositories/${repositoryId}/commits`, { params });
  }

  /** Creates a 3-layer commit review (facts → risk signals → optional AI summary). */
  createCommitReview(repositoryId: number, request: CreateCommitReviewRequest): Observable<CommitReviewResponse> {
    return this.http.post<CommitReviewResponse>(`/api/repositories/${repositoryId}/commit-reviews`, request);
  }

  /** Retrieves an existing commit review by its primary key. */
  getCommitReview(reviewId: number): Observable<CommitReviewResponse> {
    return this.http.get<CommitReviewResponse>(`/api/commit-reviews/${reviewId}`);
  }

  private analyticsParams(filter: AnalyticsFilter, defaultSize = 25): HttpParams {
    let params = this.page(filter.page ?? 0, filter.size ?? defaultSize);
    if (filter.repositoryId) {
      params = params.set('repositoryId', filter.repositoryId);
    }
    if (filter.ruleId) {
      params = params.set('ruleId', filter.ruleId);
    }
    if (filter.filePath) {
      params = params.set('filePath', filter.filePath);
    }
    if (filter.severity) {
      params = params.set('severity', filter.severity);
    }
    if (filter.from) {
      params = params.set('from', filter.from);
    }
    if (filter.to) {
      params = params.set('to', filter.to);
    }
    return params;
  }

  private page(page: number, size: number): HttpParams {
    return new HttpParams().set('page', page).set('size', size);
  }
}

