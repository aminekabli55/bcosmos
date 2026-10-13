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
  { path: '**', redirectTo: 'repositories' },
];
