import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'board' },
  {
    path: 'login',
    title: 'Sign in · ApplyPilot',
    canActivate: [guestGuard],
    loadComponent: () => import('./auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    title: 'Create account · ApplyPilot',
    canActivate: [guestGuard],
    loadComponent: () => import('./auth/register/register').then((m) => m.Register),
  },
  {
    path: 'board',
    title: 'Board · ApplyPilot',
    canActivate: [authGuard],
    loadComponent: () => import('./board/board').then((m) => m.Board),
  },
  { path: '**', redirectTo: 'board' },
];
