import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should create the app', () => {
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });

  it('should render the navigation', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const links = (fixture.nativeElement as HTMLElement).querySelectorAll('.app-nav__link');
    expect([...links].map((link) => link.textContent?.trim())).toEqual([
      'Repositories',
      'Quality',
      'Cosmos',
      'History',
      'Corpus',
      'Analytics',
      'Benchmarks',
      'Settings',
      'Reviews',
    ]);
  });

  it('should route to the repositories, quality, cosmos, history, corpus, analytics, benchmarks, settings and commit-reviews screens', () => {
    const paths = routes.map((route) => route.path);
    expect(paths).toContain('repositories');
    expect(paths).toContain('quality');
    expect(paths).toContain('quality/:analysisId');
    expect(paths).toContain('cosmos');
    expect(paths).toContain('cosmos/:analysisId');
    expect(paths).toContain('history');
    expect(paths).toContain('corpus');
    expect(paths).toContain('analytics');
    expect(paths).toContain('benchmarks');
    expect(paths).toContain('settings');
    expect(paths).toContain('commit-reviews');
  });
});
