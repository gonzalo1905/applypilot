import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'ApplyPilot · AI co-pilot for your job search',
    loadComponent: () => import('./landing/landing').then((m) => m.Landing),
  },
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
  {
    path: 'profile',
    title: 'Profile · ApplyPilot',
    canActivate: [authGuard],
    loadComponent: () => import('./profile/profile').then((m) => m.Profile),
  },
  { path: '**', redirectTo: '' },
];
