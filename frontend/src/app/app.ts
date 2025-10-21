import { Component } from '@angular/core';
import { AuthService } from './auth/auth.service';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { RegisterComponent } from './auth/register.component';
import { VerifyComponent } from './auth/verify.component';
import { RequestResetComponent } from './auth/request-reset.component';
import { ResetPasswordComponent } from './auth/reset-password.component';
import { LoginComponent } from './auth/login.component';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { AuthInterceptor } from './auth/auth.interceptor';
import { StepperComponent } from './stepper/stepper.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { AdminDashboardComponent } from './admin-dashboard.component';
import { ToastContainerComponent } from './ui/toast-container.component';
import { ModalHostComponent } from './ui/modal-host.component';

@Component({
  selector: 'app-root',
  standalone: true,
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }
  ],
  imports: [
    CommonModule,
    LoginComponent,
    RegisterComponent,
    RequestResetComponent,
    ResetPasswordComponent,
    VerifyComponent,
    StepperComponent,
    DashboardComponent,
    AdminDashboardComponent,
    RouterModule,
    ToastContainerComponent,
    ModalHostComponent
  ],
  templateUrl: './app.html',
  styleUrls: ['./app.css']
})
export class App {
  // basic local tab state to replace the previous tab control
  tabIndex = 0;
  constructor(public auth: AuthService) {}
  // show reset UI when URL path is /reset-password or token query is present
  get showReset(): boolean {
    try {
      const p = window.location.pathname || '';
      const q = new URLSearchParams(window.location.search || '');
      return p === '/reset-password' || q.has('token');
    } catch (e) {
      return false;
    }
  }

  setTab(idx: number) {
    this.tabIndex = idx;
  }

  get activePublicView(): 'login' | 'register' | 'verify' | 'request-reset' | 'reset-password' {
    const url = this.router.url ?? '';
    if (url.startsWith('/register')) {
      return 'register';
    }
    if (url.startsWith('/verify')) {
      return 'verify';
    }
    if (url.startsWith('/request-reset')) {
      return 'request-reset';
    }
    if (url.startsWith('/reset-password')) {
      return 'reset-password';
    }
    return 'login';
  }
}

// routes moved to app.routes.ts
