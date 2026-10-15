import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CorpusIngestResponse, CorpusStatusResponse } from '../core/api/models';
import { Corpus } from './corpus';

describe('Corpus component', () => {
  let fixture: ComponentFixture<Corpus>;
  let component: Corpus;
  let http: HttpTestingController;

  const mockStatus: CorpusStatusResponse = {
    repositories: 2,
    commitsRecorded: 15,
    items: [
      {
        repositoryId: 101,
        name: 'commons-lang',
        remoteUrl: 'https://github.com/apache/commons-lang',
        status: 'REGISTERED',
        commitsRecorded: 10,
        latestCommitHash: 'abcdef1234567890',
        latestCommitAt: '2026-10-14T12:00:00Z',
      },
    ],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Corpus],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Corpus);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('loads status on init', () => {
    fixture.detectChanges();

    const req = http.expectOne('/api/corpus/status');
    expect(req.request.method).toBe('GET');
    req.flush(mockStatus);

    expect(component['status']()?.repositories).toBe(2);
    expect(component['loading']()).toBeFalsy();
  });

  it('submits ingest request and refreshes status', () => {
    fixture.detectChanges();
    http.expectOne('/api/corpus/status').flush(mockStatus);

    const ingestRes: CorpusIngestResponse = {
      accepted: [
        { repositoryId: 102, name: 'junit5', remoteUrl: 'https://github.com/junit-team/junit5', newlyRegistered: true },
      ],
      rejected: [],
      commitWindow: 50,
    };

    component['urlsText'].set('https://github.com/junit-team/junit5\n');
    component.submitIngest();

    const ingestReq = http.expectOne('/api/corpus/ingest');
    expect(ingestReq.request.method).toBe('POST');
    expect(ingestReq.request.body).toEqual({
      urls: ['https://github.com/junit-team/junit5'],
      commitWindow: 50,
    });
    ingestReq.flush(ingestRes);

    // Refresh request
    http.expectOne('/api/corpus/status').flush(mockStatus);

    expect(component['ingestResult']()).toEqual(ingestRes);
    expect(component['urlsText']()).toBe('');
  });

  it('handles ingest error gracefully', () => {
    fixture.detectChanges();
    http.expectOne('/api/corpus/status').flush(mockStatus);

    component['urlsText'].set('https://untrusted.com/repo');
    component.submitIngest();

    const ingestReq = http.expectOne('/api/corpus/ingest');
    ingestReq.flush(
      { title: 'Host not allowed', detail: 'Host untrusted.com is not on the allowlist' },
      { status: 400, statusText: 'Bad Request' }
    );

    expect(component['ingestError']()).toContain('Host not allowed');
    expect(component['ingesting']()).toBeFalsy();
  });
});
