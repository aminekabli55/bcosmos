import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'repositories' },
  {
    path: 'repositories',
    title: 'Repositories',
    loadComponent: () => import('./repositories/repositories').then((module) => module.Repositories),
  },
  {
    path: 'quality',
    title: 'Quality',
    loadComponent: () => import('./quality/quality').then((module) => module.Quality),
  },
  {
    path: 'quality/:analysisId',
    title: 'Quality',
    loadComponent: () => import('./quality/quality').then((module) => module.Quality),
  },
  {
    path: 'quality/:analysisId/findings/:findingId/review',
    title: 'AI Review',
    loadComponent: () => import('./ai-review/ai-review').then((module) => module.AiReview),
  },
  {
    path: 'findings/:findingId/review',
    title: 'AI Review',
    loadComponent: () => import('./ai-review/ai-review').then((module) => module.AiReview),
  },
  {
    path: 'cosmos',
    title: 'Code Cosmos',
    loadComponent: () => import('./cosmos/cosmos').then((module) => module.Cosmos),
  },
  {
    path: 'cosmos/:analysisId',
    title: 'Code Cosmos',
    loadComponent: () => import('./cosmos/cosmos').then((module) => module.Cosmos),
  },
  {
    path: 'history',
    title: 'History',
    loadComponent: () => import('./history/history').then((module) => module.History),
  },
  {
    path: 'corpus',
    title: 'Corpus',
    loadComponent: () => import('./corpus/corpus').then((module) => module.Corpus),
  },
  {
    path: 'analytics',
    title: 'Analytics',
    loadComponent: () => import('./analytics/analytics').then((module) => module.Analytics),
  },
  {
    path: 'benchmarks',
    title: 'Benchmarks',
    loadComponent: () => import('./benchmarks/benchmarks').then((module) => module.Benchmarks),
  },
  {
    path: 'settings',
    title: 'Settings',
    loadComponent: () => import('./settings/settings').then((module) => module.Settings),
  },
  {
    path: 'commit-reviews',
    title: 'Commit Review',
    loadComponent: () => import('./commit-review/commit-review').then((module) => module.CommitReview),
  },
  { path: '**', redirectTo: 'repositories' },
];
