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

export function isTerminalStatus(status: AnalysisRunStatus): boolean {
  return status === 'SUCCEEDED' || status === 'FAILED';
}

/** Formats an ISO instant for display; the API always sends UTC. */
export function formatInstant(value: string | null): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
