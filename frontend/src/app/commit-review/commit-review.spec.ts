import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { CommitReview } from './commit-review';
import {
  CommitListResponse,
  CommitReviewResponse,
} from '../core/api/models';

function makeListResponse(commits: CommitListResponse['commits'] = []): CommitListResponse {
  return { repositoryId: 1, commits };
}

function makeReview(partial: Partial<CommitReviewResponse> = {}): CommitReviewResponse {
  return {
    id: 1,
    repositoryId: 1,
    fromCommit: 'aaa',
    toCommit: 'bbb',
    facts: {
      filesChangedCount: 2,
      filesChanged: ['Foo.java', 'Bar.java'],
      newFindingsCount: 1,
      resolvedFindingsCount: 0,
      persistentFindingsCount: 0,
      uncertainFindingsCount: 0,
      affectedDependentsCount: 0,
      unresolvedEdgesCount: 0,
    },
    riskSignals: [],
    aiSummary: null,
    whatToCheck: [],
    testsToRun: [],
    aiModelName: null,
    createdAt: new Date().toISOString(),
    ...partial,
  };
}

describe('CommitReview', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CommitReview],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function create() {
    const fixture = TestBed.createComponent(CommitReview);
    fixture.detectChanges();
    // flush repositories
    http.expectOne((r) => r.url.includes('/api/repositories')).flush({
      items: [{ id: 1, name: 'Repo A', sourceType: 'LOCAL', primaryLanguage: 'Java', status: 'REGISTERED', createdAt: new Date().toISOString() }],
      page: 0, size: 50, totalItems: 1, totalPages: 1, hasNext: false,
    });
    // flush commits
    http.expectOne((r) => r.url.includes('/api/repositories/1/commits')).flush(
      makeListResponse([
        { hash: 'bbb', shortHash: 'bbb', message: 'feat: B', author: 'dev', committedAt: new Date().toISOString(), parentHashes: ['aaa'], reviewId: null },
        { hash: 'aaa', shortHash: 'aaa', message: 'fix: A', author: 'dev', committedAt: new Date().toISOString(), parentHashes: [], reviewId: null },
      ])
    );
    // flush models
    http.expectOne((r) => r.url.includes('/api/models')).flush({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0, hasNext: false });
    fixture.detectChanges();
    return fixture;
  }

  it('should create and show commit list', () => {
    const fixture = create();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.cr-commit-row').length).toBe(2);
  });

  it('should pre-select from/to commits from the list', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    // to = latest (index 0), from = second (index 1)
    expect(component.toCommit()).toBe('bbb');
    expect(component.fromCommit()).toBe('aaa');
  });

  it('canRunReview is false when fromCommit is empty', () => {
    const fixture = create();
    fixture.componentInstance.fromCommit.set('');
    expect(fixture.componentInstance.canRunReview).toBe(false);
  });

  it('canRunReview is true when both commits are set', () => {
    const fixture = create();
    expect(fixture.componentInstance.canRunReview).toBe(true);
  });

  it('should POST to commit-reviews and display facts', () => {
    const fixture = create();
    const btn = fixture.nativeElement.querySelector('#cr-run-review-btn') as HTMLButtonElement;
    btn.click();
    fixture.detectChanges();

    const req = http.expectOne((r) => r.method === 'POST' && r.url.includes('/commit-reviews'));
    req.flush(makeReview({ facts: { filesChangedCount: 3, filesChanged: ['A.java', 'B.java', 'C.java'], newFindingsCount: 2, resolvedFindingsCount: 1, persistentFindingsCount: 0, uncertainFindingsCount: 0, affectedDependentsCount: 0, unresolvedEdgesCount: 0 } }));
    fixture.detectChanges();

    const factValues = fixture.nativeElement.querySelectorAll('.cr-fact-value');
    expect(factValues[0].textContent.trim()).toBe('3');  // filesChanged
    expect(factValues[1].textContent.trim()).toBe('2');  // newFindings
  });

  it('should show risk signal cards when signals present', () => {
    const fixture = create();
    fixture.componentInstance.review.set(makeReview({
      riskSignals: [
        { code: 'NEW_FINDINGS', title: 'Quality regression', description: '2 new findings', severity: 'CRITICAL', isHeuristic: false },
      ],
    }));
    fixture.detectChanges();
    const cards = fixture.nativeElement.querySelectorAll('.cr-risk-card');
    expect(cards.length).toBe(1);
    expect(cards[0].querySelector('.cr-risk-title').textContent).toContain('Quality regression');
  });

  it('should mark heuristic signals with a tag', () => {
    const fixture = create();
    fixture.componentInstance.review.set(makeReview({
      riskSignals: [
        { code: 'MAIN_CHANGED_NO_TESTS', title: 'No test changes', description: 'Main changed', severity: 'MEDIUM', isHeuristic: true },
      ],
    }));
    fixture.detectChanges();
    const tag = fixture.nativeElement.querySelector('.cr-heuristic-tag');
    expect(tag).toBeTruthy();
  });

  it('should show no-ai hint when AI summary is absent', () => {
    const fixture = create();
    fixture.componentInstance.review.set(makeReview({ aiSummary: null }));
    fixture.detectChanges();
    const hint = fixture.nativeElement.querySelector('.cr-no-ai-hint');
    expect(hint).toBeTruthy();
  });

  it('should show AI summary section when present', () => {
    const fixture = create();
    fixture.componentInstance.review.set(makeReview({ aiSummary: 'This commit adds a new feature.' }));
    fixture.detectChanges();
    const summary = fixture.nativeElement.querySelector('.cr-ai-summary');
    expect(summary).toBeTruthy();
    expect(summary.textContent).toContain('This commit adds a new feature.');
  });

  it('should load existing review on commit row view button click', () => {
    const fixture = create();
    // Simulate a commit that has a review
    const commit = fixture.componentInstance.commits()[0];
    Object.defineProperty(commit, 'reviewId', { value: 42, writable: true });
    fixture.componentInstance.commits.set([...fixture.componentInstance.commits()]);
    fixture.detectChanges();

    fixture.componentInstance.loadExistingReview(42);
    const req = http.expectOne((r) => r.url.includes('/api/commit-reviews/42'));
    req.flush(makeReview({ id: 42 }));
    fixture.detectChanges();

    expect(fixture.componentInstance.review()?.id).toBe(42);
  });

  it('should display error message on API failure', () => {
    const fixture = create();
    fixture.componentInstance.fromCommit.set('from');
    fixture.componentInstance.toCommit.set('to');
    fixture.componentInstance.runReview();
    fixture.detectChanges();

    const req = http.expectOne((r) => r.method === 'POST');
    req.flush({ title: 'Bad request', detail: 'Invalid hash' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    const err = fixture.nativeElement.querySelector('.cr-error');
    expect(err).toBeTruthy();
  });
});
