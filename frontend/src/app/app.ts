import { Component } from '@angular/core';
import { AuthService } from './auth/auth.service';
import { CommonModule } from '@angular/common';
import { provideRouter, RouterModule } from '@angular/router';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { RegisterComponent } from './auth/register.component';
import { VerifyComponent } from './auth/verify.component';
import { RequestResetComponent } from './auth/request-reset.component';
import { ResetPasswordComponent } from './auth/reset-password.component';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { LoginComponent } from './auth/login.component';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { AuthInterceptor } from './auth/auth.interceptor';
import { StepperComponent } from './stepper/stepper.component';
import { DashboardComponent } from './dashboard/dashboard.component';

@Component({
  selector: 'app-root',
  standalone: true,
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }
  ],
  imports: [
    CommonModule,
    MatSnackBarModule,
    MatToolbarModule,
    MatButtonModule,
    MatTabsModule,
    LoginComponent,
    StepperComponent,
    DashboardComponent,
    RouterModule
  ],
  templateUrl: './app.html',
  styleUrls: ['./app.css']
})
export class App {
  constructor(public auth: AuthService) {}
}

export const appRoutes = [
  { path: 'register', component: RegisterComponent },
  { path: 'verify', component: VerifyComponent },
  { path: 'request-reset', component: RequestResetComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  { path: '', component: DashboardComponent }
];
