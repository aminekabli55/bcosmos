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
});
