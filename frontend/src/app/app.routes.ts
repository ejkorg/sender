import { Routes } from '@angular/router';
import { inject } from '@angular/core';
import { AdminDashboardComponent } from './admin-dashboard.component';
import { RegisterComponent } from './auth/register.component';
import { VerifyComponent } from './auth/verify.component';
import { RequestResetComponent } from './auth/request-reset.component';
import { ResetPasswordComponent } from './auth/reset-password.component';
import { LoginComponent } from './auth/login.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { AuthService } from './auth/auth.service';

export const appRoutes: Routes = [
  {
    path: 'admin',
    component: AdminDashboardComponent,
    canActivate: [() => {
      const auth = inject(AuthService);
      return auth.isAdmin() ? true : ['/'];
    }]
  },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'verify', component: VerifyComponent },
  { path: 'request-reset', component: RequestResetComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  { path: '', component: DashboardComponent }
];
