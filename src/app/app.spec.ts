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

  it('should render the Phase 1 navigation', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const links = (fixture.nativeElement as HTMLElement).querySelectorAll('.app-nav__link');
    expect([...links].map((link) => link.textContent?.trim())).toEqual(['Repositories', 'Quality']);
  });

  it('should route to the repositories and quality screens', () => {
    const paths = routes.map((route) => route.path);
    expect(paths).toContain('repositories');
    expect(paths).toContain('quality');
    expect(paths).toContain('quality/:analysisId');
  });
});
