import { Routes } from '@angular/router';
import { adminAuthGuard } from './guards/admin-auth.guard';

export const ADMIN_ROUTES: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/admin-login.component').then(m => m.AdminLoginComponent)
  },
  {
    path: '',
    canActivate: [adminAuthGuard],
    loadComponent: () => import('./pages/admin-dashboard.component').then(m => m.AdminDashboardComponent)
  }
];
