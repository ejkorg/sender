import { Component } from '@angular/core';
import { AuthService } from './auth/auth.service';
import { CommonModule } from '@angular/common';
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
    MatToolbarModule,
    MatButtonModule,
    MatTabsModule,
    LoginComponent,
    StepperComponent,
    DashboardComponent
  ],
  templateUrl: './app.html',
  styleUrls: ['./app.css']
})
export class App {
  constructor(public auth: AuthService) {}
}
