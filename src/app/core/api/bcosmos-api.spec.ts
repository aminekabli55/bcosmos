import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BcosmosApi } from './bcosmos-api';

describe('BcosmosApi', () => {
  let api: BcosmosApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(BcosmosApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists repositories with pagination', () => {
    api.listRepositories(2, 10).subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/repositories');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('10');
    request.flush({ items: [], page: 2, size: 10, totalItems: 0, totalPages: 0, hasNext: false });
  });

  it('registers a repository', () => {
    api.registerRepository({ localPath: '/repos/java-fixture', name: 'java-fixture' }).subscribe();
    const request = http.expectOne('/api/repositories');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ localPath: '/repos/java-fixture', name: 'java-fixture' });
    request.flush({
      id: 1,
      name: 'java-fixture',
      sourceType: 'LOCAL',
      primaryLanguage: null,
      status: 'REGISTERED',
      createdAt: '2026-10-12T10:00:00Z',
    });
  });

  it('starts an analysis', () => {
    api.startAnalysis(4).subscribe();
    const request = http.expectOne('/api/repositories/4/analyses');
    expect(request.request.method).toBe('POST');
    request.flush({});
  });

  it('deletes a repository', () => {
    api.deleteRepository(7).subscribe();
    const request = http.expectOne('/api/repositories/7');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    request.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('sends only the finding filters that were set', () => {
    api.listFindings(9, { severity: 'CRITICAL', rule: null, file: 'BookService.java', page: 0, size: 25 }).subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/analyses/9/findings');
    expect(request.request.params.get('severity')).toBe('CRITICAL');
    expect(request.request.params.get('file')).toBe('BookService.java');
    expect(request.request.params.has('rule')).toBe(false);
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('25');
    request.flush({ items: [], page: 0, size: 25, totalItems: 0, totalPages: 0, hasNext: false });
  });

  it('asks for the failed files of a run', () => {
    api.listFiles(9, 'FAILED').subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/analyses/9/files');
    expect(request.request.params.get('parseStatus')).toBe('FAILED');
    request.flush({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0, hasNext: false });
  });

  it('lists the model registry with pagination', () => {
    api.listModels(1, 10).subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/models');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('10');
    request.flush({ items: [], page: 1, size: 10, totalItems: 0, totalPages: 0, hasNext: false });
  });

  it('sends only the registry fields that were edited', () => {
    api.updateModel(7, { enabled: true }).subscribe();
    const request = http.expectOne('/api/models/7');
    expect(request.request.method).toBe('PUT');
    // A missing field means "leave it", so flipping one switch cannot overwrite prices.
    expect(request.request.body).toEqual({ enabled: true });
    request.flush({});
  });

  it('reads the AI master switch', () => {
    api.getAiSettings().subscribe();
    const request = http.expectOne('/api/settings/ai');
    expect(request.request.method).toBe('GET');
    request.flush({ enabled: false, propertyDefault: true, overridden: true, updatedAt: null });
  });

  it('stores the AI master switch and can hand the decision back to the deployment', () => {
    api.updateAiSettings(null).subscribe();
    const request = http.expectOne('/api/settings/ai');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ enabled: null });
    request.flush({ enabled: true, propertyDefault: true, overridden: false, updatedAt: null });
  });

  it('reads the analytics overview without parameters', () => {
    api.getAnalyticsOverview().subscribe();
    const request = http.expectOne('/api/analytics/overview');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('sends only the drill-down dimensions that were set', () => {
    api.listAnalyticsFindings({ repositoryId: 3, ruleId: 'EmptyCatchBlock', severity: 'HIGH', page: 1 }).subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/analytics/findings');
    expect(request.request.params.get('repositoryId')).toBe('3');
    expect(request.request.params.get('ruleId')).toBe('EmptyCatchBlock');
    expect(request.request.params.get('severity')).toBe('HIGH');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.has('filePath')).toBe(false);
    expect(request.request.params.has('from')).toBe(false);
    request.flush({ items: [], page: 1, size: 50, totalItems: 0, totalPages: 0, hasNext: false });
  });

  it('asks the trend endpoint for one page of a whole window', () => {
    api.listTrends({ repositoryId: 2 }).subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/analytics/trends');
    // The weeks of a repository fit one page; the default keeps the chart from being drawn from a truncated series.
    expect(request.request.params.get('size')).toBe('100');
    expect(request.request.params.get('page')).toBe('0');
    request.flush({ items: [], page: 0, size: 100, totalItems: 0, totalPages: 0, hasNext: false });
  });

  it('lists the dataset splits and the repositories of one split', () => {
    api.listDatasetSplits().subscribe();
    const splits = http.expectOne((candidate) => candidate.url === '/api/analytics/dataset/splits');
    expect(splits.request.params.get('page')).toBe('0');
    splits.flush({ items: [], page: 0, size: 10, totalItems: 0, totalPages: 0, hasNext: false });

    api.listDatasetRepositories('train').subscribe();
    const repositories = http.expectOne((candidate) => candidate.url === '/api/analytics/dataset/repositories');
    expect(repositories.request.params.get('split')).toBe('train');
    repositories.flush({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0, hasNext: false });
  });
});
