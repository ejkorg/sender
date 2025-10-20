import { Component } from '@angular/core';
import { AuthService } from './auth/auth.service';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
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
import { ToastService } from './ui/toast.service';

@Component({
  selector: 'app-root',
  standalone: true,
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }
  ],
  imports: [
    CommonModule,
    LoginComponent,
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
  constructor(public auth: AuthService, private toast: ToastService) {}

  setTab(idx: number) {
    this.tabIndex = idx;
  }
}

// routes moved to app.routes.ts
