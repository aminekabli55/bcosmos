import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AnalysisRun,
  FileRecord,
  Finding,
  FindingFilter,
  Paged,
  ParseStatus,
  RegisterRepositoryRequest,
  Repository,
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

  private page(page: number, size: number): HttpParams {
    return new HttpParams().set('page', page).set('size', size);
  }
}
