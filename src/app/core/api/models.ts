/**
 * The REST contract of the backend (spec v2 section 8), as the UI consumes it. Field names and enum values mirror
 * the Java DTOs in `backend/.../web/dto`, so a change there must be mirrored here.
 */

export const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'] as const;
export type Severity = (typeof SEVERITIES)[number];

export const PARSE_STATUSES = ['OK', 'FAILED'] as const;
export type ParseStatus = (typeof PARSE_STATUSES)[number];

export type RepositorySourceType = 'LOCAL';
export type RepositoryStatus = 'REGISTERED' | 'ANALYSED' | 'FAILED';
export type AnalysisRunStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export type AnalysisStage = 'QUEUED' | 'DISCOVERY' | 'PARSING' | 'ANALYSIS' | 'PERSISTENCE' | 'COMPLETED';
export type JobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export type TriageStatus = 'OPEN' | 'ACCEPTED' | 'REJECTED' | 'DEFERRED';

export interface Paged<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
}

export interface Repository {
  id: number;
  name: string;
  sourceType: RepositorySourceType;
  primaryLanguage: string | null;
  status: RepositoryStatus;
  createdAt: string;
}

export interface RegisterRepositoryRequest {
  localPath: string;
  name?: string | null;
}

export interface AnalysisRun {
  id: number;
  repositoryId: number;
  status: AnalysisRunStatus;
  stage: AnalysisStage;
  commitHash: string | null;
  parserVersion: string | null;
  analyzerVersion: string | null;
  rulesetHash: string | null;
  schemaVersion: number;
  filesDiscovered: number;
  filesParsed: number;
  filesFailed: number;
  findingsCount: number;
  symbolsCount: number;
  errorCode: string | null;
  errorSummary: string | null;
  jobStatus: JobStatus | null;
  jobErrorSummary: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface Finding {
  id: number;
  ruleId: string;
  severity: Severity;
  originalSeverity: string | null;
  tool: string | null;
  toolVersion: string | null;
  message: string | null;
  /** Repository-relative; an absolute path never appears in a response. */
  filePath: string | null;
  startLine: number;
  endLine: number;
  symbolQualifiedName: string | null;
  /** The flagged source lines as stored with the finding, so the preview matches the analysed revision. */
  snippet: string | null;
  fingerprint: string;
  triageStatus: TriageStatus;
}

export interface FileRecord {
  id: number;
  filePath: string;
  language: string | null;
  parseStatus: ParseStatus;
  parseError: string | null;
  lineCount: number;
}

export interface FindingFilter {
  severity?: Severity | null;
  rule?: string | null;
  file?: string | null;
  page: number;
  size: number;
}

export type LlmModelKind = 'HOSTED' | 'FINETUNED';
/** What a registry row is for: a chat model cannot embed, and an embedding model cannot chat. */
export type LlmModelCapability = 'CHAT' | 'EMBEDDING';

export const PROVIDER_PROTOCOLS = ['GEMINI', 'OPENAI_COMPATIBLE'] as const;
/**
 * The wire shape a provider speaks, which is what selects the adapter. It is not the vendor: DeepSeek, Groq,
 * OpenRouter and a local vLLM server are all `OPENAI_COMPATIBLE` rows.
 */
export type ProviderProtocol = (typeof PROVIDER_PROTOCOLS)[number];

/**
 * Where a provider's credential comes from. Never the credential itself: that stays on the server (ADR 0038).
 * `STORED` was pasted into the screen and is encrypted, `ENVIRONMENT` is a variable name the row points at,
 * `NOT_REQUIRED` is a local server, `MISSING` is why its models cannot run.
 */
export type CredentialSource = 'STORED' | 'ENVIRONMENT' | 'NOT_REQUIRED' | 'MISSING';

/** One provider row (`GET /api/providers`): a code, the protocol it speaks, its endpoint and where its key is. */
export interface Provider {
  id: number;
  code: string;
  label: string;
  protocol: ProviderProtocol;
  baseUrl: string;
  apiKeyRequired: boolean;
  credentialSource: CredentialSource;
  /** Last four characters of a stored key; the key itself never reaches the browser. */
  credentialHint: string | null;
  credentialEnvironmentVariable: string | null;
  /** Registry rows attached to this provider — it cannot be deleted while one exists. */
  modelCount: number;
  updatedAt: string;
}

/** A new provider (`POST /api/providers`). `apiKey` travels once and is never echoed back. */
export interface CreateProviderRequest {
  code: string;
  label: string;
  protocol: ProviderProtocol;
  baseUrl: string;
  apiKeyEnv?: string | null;
  apiKeyRequired?: boolean | null;
  apiKey?: string | null;
}

/**
 * An edit of one provider (`PUT /api/providers/{id}`); a missing field means "leave it". An empty `apiKey` clears
 * the stored key. `code` is absent: it is the stable identity, so it never changes.
 */
export interface UpdateProviderRequest {
  label?: string | null;
  protocol?: ProviderProtocol | null;
  baseUrl?: string | null;
  apiKeyEnv?: string | null;
  apiKeyRequired?: boolean | null;
  apiKey?: string | null;
}

/** One registry row (`GET /api/models`), owned by the provider row that carries its endpoint and credential. */
export interface LlmModel {
  id: number;
  providerId: number;
  /** The provider's stable code, for example `deepseek`. */
  provider: string;
  providerLabel: string;
  providerProtocol: ProviderProtocol;
  modelName: string;
  kind: LlmModelKind;
  capability: LlmModelCapability;
  /** Vector width an embedding model produces, `null` for a chat model. */
  embeddingDimension: number | null;
  enabled: boolean;
  defaultModel: boolean;
  contextWindow: number;
  costPer1kIn: number;
  costPer1kOut: number;
  notes: string | null;
  updatedAt: string;
  credentialSource: CredentialSource;
  /** Last four characters of a stored key, or `null`. */
  credentialHint: string | null;
  credentialEnvironmentVariable: string | null;
}

/**
 * A new registry row (`POST /api/models`): the provider that owns the endpoint, the model id exactly as the vendor
 * spells it, and the knobs that have defaults. `embeddingDimension` is required for an `EMBEDDING` row.
 */
export interface CreateModelRequest {
  providerId: number;
  modelName: string;
  kind?: LlmModelKind | null;
  capability?: LlmModelCapability | null;
  embeddingDimension?: number | null;
  enabled?: boolean | null;
  defaultModel?: boolean | null;
  contextWindow?: number | null;
  costPer1kIn?: number | null;
  costPer1kOut?: number | null;
  notes?: string | null;
}

/**
 * An edit of one registry row; a missing field means "leave it as it is", so two open tabs cannot overwrite each
 * other with a whole-row update.
 */
export interface ModelEdit {
  enabled?: boolean;
  defaultModel?: boolean;
  contextWindow?: number;
  costPer1kIn?: number;
  costPer1kOut?: number;
  notes?: string;
}

/** The AI master switch and where its current value comes from. */
export interface AiSettings {
  enabled: boolean;
  propertyDefault: boolean;
  overridden: boolean;
  updatedAt: string | null;
}

/** Formats a price per 1000 tokens; the registry keeps six decimals but the UI shows what it costs. */
export function formatPrice(value: number | null): string {
  if (value === null || Number.isNaN(value)) {
    return '—';
  }
  return value === 0 ? 'free' : `$${value.toFixed(6)}`;
}

export const SUGGESTION_STATUSES = ['PROPOSED', 'ACCEPTED', 'REJECTED', 'DEFERRED'] as const;
export type SuggestionStatus = (typeof SUGGESTION_STATUSES)[number];
/** Why a piece of evidence is in the bundle: the enclosing declaration, or a chunk ranked similar. */
export type EvidenceKind = 'ENCLOSING' | 'SIMILAR';

/** One citation the model made; whether it survived the check is in `verdict.evidenceOk`. */
export interface Citation {
  file: string;
  startLine: number | null;
  endLine: number | null;
  why: string | null;
}

/** The model's answer (spec v2 section 7.3). */
export interface SuggestionAnswer {
  summary: string | null;
  problemExplanation: string | null;
  citations: Citation[];
  proposedDiff: string | null;
  risks: string[];
  testsToRun: string[];
  uncertainties: string[];
}

/** What BCosmos checked about the answer, automatically. */
export interface SuggestionVerdict {
  schemaOk: boolean;
  evidenceOk: boolean;
  diffApplies: boolean;
  parses: boolean;
  targetFindingGone: boolean;
  /** Every check passed. It is not an approval: the human decision is `status`. */
  allChecksPassed: boolean;
  newFindings: { ruleId: string; startLine: number; message: string | null }[];
  notes: string[];
}

/** One piece of evidence the model was shown. */
export interface EvidenceItem {
  kind: EvidenceKind;
  file: string;
  startLine: number;
  endLine: number;
  qualifiedName: string;
  text: string;
  similarity: number | null;
  estimatedTokens: number;
}

/** What retrieval gathered, and what it could not gather. */
export interface SuggestionRetrieval {
  embedModel: string;
  chunksConsidered: number;
  estimatedTokens: number;
  truncated: boolean;
  ruleDescription: string;
  notes: string[];
  items: EvidenceItem[];
  index: { chunks: number; reused: boolean; symbolsWithoutSource: number; tokensIn: number; latencyMs: number };
}

/** One stored suggestion with its verdict and evidence (spec v2 section 8). */
export interface Suggestion {
  id: number;
  findingId: number;
  modelId: number | null;
  modelLabel: string | null;
  promptVersion: string;
  status: SuggestionStatus;
  createdAt: string;
  reviewedAt: string | null;
  latencyMs: number;
  tokensIn: number;
  tokensOut: number;
  answer: SuggestionAnswer;
  verdict: SuggestionVerdict;
  retrieval: SuggestionRetrieval;
}

/**
 * Body of `POST /api/findings/{id}/suggestions`.
 *
 * <p>Both ids are required in practice: the registry has no stored default (changeset 0027), so a request that
 * names neither is refused with the list of models it could have named.</p>
 */
export interface SuggestRequest {
  modelId?: number | null;
  embeddingModelId?: number | null;
}

export function isTerminalStatus(status: AnalysisRunStatus): boolean {
  return status === 'SUCCEEDED' || status === 'FAILED';
}

/** The line range of a citation, or the whole file when the model cited no lines. */
export function formatRange(startLine: number | null, endLine: number | null): string {
  if (startLine === null) {
    return 'whole file';
  }
  return endLine === null || endLine === startLine ? `line ${startLine}` : `lines ${startLine}-${endLine}`;
}

/** Formats an ISO instant for display; the API always sends UTC. */
export function formatInstant(value: string | null): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export interface IngestedRepository {
  repositoryId: number;
  name: string;
  remoteUrl: string;
  newlyRegistered: boolean;
}

export interface CorpusIngestRequest {
  urls: string[];
  commitWindow?: number;
}

export interface CorpusIngestResponse {
  accepted: IngestedRepository[];
  rejected: string[];
  commitWindow: number;
}

export interface CorpusRepositoryStatus {
  repositoryId: number;
  name: string;
  remoteUrl: string;
  status: string;
  commitsRecorded: number;
  latestCommitHash?: string | null;
  latestCommitAt?: string | null;
}

export interface CorpusStatusResponse {
  repositories: number;
  commitsRecorded: number;
  items: CorpusRepositoryStatus[];
}

/* ------------------------------------------------------------------ Analytics (Phase 5) */

/**
 * The snapshot every analytics number belongs to (spec v2 section 6.1). A chart without its snapshot is a number
 * nobody can reproduce, so the screen shows the version and the generation time next to the headline figures.
 */
export interface AnalyticsSnapshot {
  available: boolean;
  datasetVersion: string | null;
  generatedAt: string | null;
  source: string | null;
  /** How many recent commits per repository the drill-down covers, or null when the snapshot predates the field. */
  drilldownCommits: number | null;
}

/** One repository that has rows in the snapshot, for the filter. */
export interface AnalyticsRepositoryRef {
  repositoryId: number;
  name: string;
  sourceType: string | null;
  licenseSpdx: string | null;
}

export interface SeverityCount {
  severity: Severity;
  findings: number;
}

export interface PipelineStat {
  key: string;
  value: number;
}

/** Findings per rule and severity, per KLOC (spec v2 section 6.1). */
export interface RuleStat {
  repositoryId: number;
  ruleId: string;
  severity: Severity | null;
  findings: number;
  filesAffected: number;
  measuredLines: number;
  /** Null when the repository reported no line count: not measured, never zero. */
  findingsPerKloc: number | null;
  parseFailures: number;
  rank: number;
}

/** One UTC week of the analysis window. */
export interface TrendPoint {
  repositoryId: number;
  bucketStart: string;
  findings: number;
  filesAnalysed: number;
  measuredLines: number;
  densityPerKloc: number | null;
  parseFailures: number;
}

/** One bar of the commits-until-resolved histogram; a null distance is the lower-bound row. */
export interface TimeToFixBucket {
  repositoryId: number;
  ruleId: string;
  commitsUntilResolved: number | null;
  resolvedPairs: number;
  observedIntroductions: number;
}

/** One ranked file with every component of the triage heuristic visible (spec v2 section 6.3). */
export interface Hotspot {
  repositoryId: number;
  filePath: string;
  score: number;
  rankInRepository: number;
  churn: number;
  churnScore: number | null;
  churnContribution: number | null;
  complexityProxy: number;
  complexityScore: number | null;
  complexityContribution: number | null;
  openFindings: number;
  openFindingsScore: number | null;
  openFindingsContribution: number | null;
  /** False until Phase 8 measures dependency impact; the UI says so instead of hiding the component. */
  dependencyMeasured: boolean;
  weightSumMeasured: number;
  latestCommitHash: string | null;
  weights: string | null;
}

/** One split of the fix-triple dataset (spec v2 section 6.2). */
export interface DatasetSplit {
  split: string;
  triples: number;
  repositories: number;
  rules: number;
  snippetChanged: number;
  introductionsUnobserved: number;
  maxChangedLines: number;
}

export interface DatasetRepository {
  split: string;
  repositoryId: number;
  name: string | null;
  licenseSpdx: string | null;
  triples: number;
}

/** One drill-down record: the finding an aggregate was computed from. */
export interface AnalyticsFinding {
  id: number;
  repositoryId: number;
  commitHash: string;
  filePath: string;
  side: string;
  ruleId: string;
  severity: Severity | null;
  symbol: string | null;
  fingerprint: string;
  startLine: number;
  endLine: number;
  lineCount: number | null;
  occurredAt: string | null;
  snippet: string | null;
}

export interface AnalyticsOverview extends AnalyticsSnapshot {
  repositories: number;
  rules: number;
  findings: number;
  measuredLines: number;
  parseFailures: number;
  triples: number;
  severityMix: SeverityCount[];
  topRules: RuleStat[];
  pipelineStats: PipelineStat[];
  repositoryRefs: AnalyticsRepositoryRef[];
  splits: DatasetSplit[];
}

/** The dimensions every chart can drill down on (spec v2 section 9: "every chart drills down to records"). */
export interface AnalyticsFilter {
  repositoryId?: number | null;
  ruleId?: string | null;
  filePath?: string | null;
  severity?: Severity | null;
  /** Inclusive ISO dates on the analysis time axis. */
  from?: string | null;
  to?: string | null;
  page?: number;
  size?: number;
}

/** Formats a density so "no measurement" is visibly different from a small value. */
export function formatDensity(value: number | null): string {
  return value === null || value === undefined ? 'not measured' : value.toFixed(1);
}

/** The mean of the observed introductions only: the lower-bound rows must never be averaged in. */
export function observedMeanDistance(buckets: TimeToFixBucket[]): number | null {
  let weighted = 0;
  let counted = 0;
  for (const bucket of buckets) {
    if (bucket.commitsUntilResolved === null) {
      continue;
    }
    weighted += bucket.commitsUntilResolved * bucket.observedIntroductions;
    counted += bucket.observedIntroductions;
  }
  return counted === 0 ? null : weighted / counted;
}

/* ------------------------------------------------------------------ Benchmarks (Phase 6) */

/**
 * One evaluation run stored in the `benchmark_run` table and returned by `GET /api/benchmarks`.
 * Field names mirror `BenchmarkRunResponse` in the backend.
 */
export interface BenchmarkRun {
  id: number;
  benchmarkId: string;
  datasetVersion: string;
  conditionName: string;
  modelName: string;
  promptVersion: string;
  retrievalStrategy: string;
  sampleSize: number;
  schemaValidRate: number;
  diffAppliesRate: number;
  parsesRate: number;
  targetResolvedRate: number;
  noNewFindingsRate: number;
  medianDiffSize: number;
  avgLatencyMs: number;
  totalTokens: number;
  estimatedCostUsd: number;
  metricsJson: string | null;
  notes: string | null;
  createdAt: string;
}

/** Body sent to `POST /api/benchmarks`. */
export interface CreateBenchmarkRunRequest {
  benchmarkId: string;
  datasetVersion: string;
  conditionName: string;
  modelName: string;
  promptVersion: string;
  retrievalStrategy: string;
  sampleSize: number;
  schemaValidRate: number;
  diffAppliesRate: number;
  parsesRate: number;
  targetResolvedRate: number;
  noNewFindingsRate: number;
  medianDiffSize: number;
  avgLatencyMs: number;
  totalTokens: number;
  estimatedCostUsd: number;
  metricsJson?: string | null;
  notes?: string | null;
}

/** Body sent to `POST /api/findings/{id}/compare-models`. */
export interface ModelCompareRequest {
  modelIds: number[];
}

/** One per-model result from the compare endpoint. */
export interface ModelComparisonItem {
  modelId: number | null;
  modelLabel: string | null;
  status: 'SUCCESS' | 'ERROR';
  errorMessage: string | null;
  suggestion: Suggestion | null;
  latencyMs: number;
  tokensIn: number;
  tokensOut: number;
}

/** Response from `POST /api/findings/{id}/compare-models`. */
export interface ModelCompareResponse {
  findingId: number;
  comparisons: ModelComparisonItem[];
}

/** Format a 0–1 rate as a percentage string (e.g. 0.875 → "87.5%"). */
export function formatRate(value: number): string {
  return `${(value * 100).toFixed(1)}%`;
}

/** Format an estimated cost in USD. */
export function formatCost(value: number): string {
  return value === 0 ? 'free' : `$${value.toFixed(4)}`;
}

// ---------------------------------------------------------------------------
// Phase 8: Code Cosmos Graph, Impact, Hotspots, and Snapshot History
// ---------------------------------------------------------------------------

export type RelationshipType = 'EXTENDS' | 'IMPLEMENTS' | 'USES_TYPE' | 'CALLS';
export type ResolutionStatus = 'RESOLVED' | 'UNRESOLVED';
export type FindingTransitionType = 'NEW' | 'RESOLVED' | 'PERSISTENT' | 'UNCERTAIN';

export interface GraphNode {
  id: string;
  name: string;
  qualifiedName: string;
  kind: string;
  filePath: string;
  lineCount: number;
  findingsCount: number;
  hotspotScore: number;
  isExternal: boolean;
  packageName: string;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  type: RelationshipType;
  status: ResolutionStatus;
  confidence: number;
  evidenceFile: string;
  evidenceLine: number;
}

export interface GraphResponse {
  analysisId: number;
  nodes: GraphNode[];
  edges: GraphEdge[];
  truncated: boolean;
  totalNodes: number;
  totalEdges: number;
  unresolvedEdgesCount: number;
  resolutionRate: number;
}

export interface SymbolImpactResponse {
  symbolId: number;
  qualifiedName: string;
  filePath: string;
  directDependentsCount: number;
  transitiveDependentsCount: number;
  directDependents: GraphNode[];
  transitiveDependents: GraphNode[];
  affectedFindingsCount: number;
  riskRating: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
}

export interface HotspotItem {
  symbolId: number;
  name: string;
  qualifiedName: string;
  filePath: string;
  lineCount: number;
  findingsCount: number;
  highSeverityCount: number;
  inDegree: number;
  outDegree: number;
  score: number;
}

export interface HotspotListResponse {
  analysisId: number;
  items: HotspotItem[];
  heuristicFormula: string;
}

export interface RunSummary {
  id: number;
  repositoryId: number;
  commitHash: string | null;
  status: string;
  filesDiscovered: number;
  filesParsed: number;
  filesFailed: number;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface FindingTransitionItem {
  id: number;
  fromFindingId: number | null;
  toFindingId: number | null;
  fingerprint: string;
  ruleId: string;
  filePath: string;
  type: FindingTransitionType;
  confidence: number;
  reason: string;
}

export interface SnapshotComparisonResponse {
  fromRun: RunSummary;
  toRun: RunSummary;
  newCount: number;
  resolvedCount: number;
  persistentCount: number;
  uncertainCount: number;
  transitions: FindingTransitionItem[];
}

/* ---------------------------------------------------------------- Commit Review (Phase 9) */

export interface CommitItem {
  hash: string;
  shortHash: string;
  message: string;
  author: string;
  committedAt: string;
  parentHashes: string[];
  reviewId: number | null;
}

export interface CommitListResponse {
  repositoryId: number;
  commits: CommitItem[];
}

export interface CreateCommitReviewRequest {
  fromCommit: string;
  toCommit: string;
  modelId: number | null;
}

export interface CommitFacts {
  filesChangedCount: number;
  filesChanged: string[];
  newFindingsCount: number;
  resolvedFindingsCount: number;
  persistentFindingsCount: number;
  uncertainFindingsCount: number;
  affectedDependentsCount: number;
  unresolvedEdgesCount: number;
}

export type RiskSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface RiskSignal {
  code: string;
  title: string;
  description: string;
  severity: RiskSeverity;
  isHeuristic: boolean;
}

export interface WhatToCheckItem {
  file: string;
  startLine: number;
  endLine: number | null;
  description: string;
}

export interface CommitReviewResponse {
  id: number;
  repositoryId: number;
  fromCommit: string;
  toCommit: string;
  facts: CommitFacts;
  riskSignals: RiskSignal[];
  aiSummary: string | null;
  whatToCheck: WhatToCheckItem[];
  testsToRun: string[];
  aiModelName: string | null;
  createdAt: string;
}
