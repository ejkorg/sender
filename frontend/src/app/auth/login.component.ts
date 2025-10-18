import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from './auth.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <div style="display:flex; gap:8px; align-items:center;">
      <ng-container *ngIf="(auth.user$ | async) as u; else loginForm">
        <span>{{ u.username }}</span>
        <button mat-button type="button" (click)="logout()">Logout</button>
      </ng-container>
      <ng-template #loginForm>
        <div style="display:flex; flex-direction:column; gap:8px;">
          <div *ngIf="!registerMode">
            <form #lf="ngForm" (ngSubmit)="login($event)">
              <mat-form-field>
                <input matInput name="username" required [(ngModel)]="username" #usernameCtl="ngModel" placeholder="username" />
                <mat-error *ngIf="usernameCtl.invalid && usernameCtl.touched">Username is required</mat-error>
              </mat-form-field>
              <mat-form-field>
                <input matInput name="password" required [(ngModel)]="password" #passwordCtl="ngModel" type="password" placeholder="password" />
                <mat-error *ngIf="passwordCtl.invalid && passwordCtl.touched">Password is required</mat-error>
              </mat-form-field>
              <div style="display:flex; gap:8px;">
                <button mat-raised-button color="primary" type="submit" [disabled]="lf.invalid">Login</button>
                <button mat-button type="button" (click)="router.navigateByUrl('/register')">Register</button>
                <button mat-button type="button" (click)="router.navigateByUrl('/request-reset')">Reset</button>
              </div>
            </form>
          </div>
          <div *ngIf="registerMode">
            <mat-form-field><input matInput [(ngModel)]="regUsername" placeholder="username" /></mat-form-field>
            <mat-form-field><input matInput [(ngModel)]="regEmail" placeholder="email (optional)" /></mat-form-field>
            <mat-form-field><input matInput [(ngModel)]="regPassword" type="password" placeholder="password" /></mat-form-field>
            <div style="display:flex; gap:8px;">
              <button mat-button type="button" (click)="register()">Create account</button>
              <button mat-button type="button" (click)="cancelRegister()">Cancel</button>
            </div>
          </div>
        </div>
      </ng-template>
    </div>
  `
})
export class LoginComponent {
  username = '';
  password = '';
  registerMode = false;
  regUsername = '';
  regEmail: string | null = null;
  regPassword = '';

  constructor(public auth: AuthService, public snack: MatSnackBar, public router: Router) {}

  login(event?: Event) {
    event?.preventDefault();
    event?.stopPropagation();
    if (!this.username || !this.password) return;
    this.auth.login(this.username, this.password).subscribe(
      _ => {},
      err => { this.snack.open('Login failed', 'Close', { duration: 3000 }); }
    );
  }

  logout() { this.auth.logout(); }

  register() {
    if (!this.regUsername || !this.regPassword || this.regPassword.length < 8) {
      this.snack.open('username and password (>=8) required', 'Close', { duration: 4000 });
      return;
    }
    this.auth.register(this.regUsername, this.regEmail, this.regPassword).subscribe(
      _ => { this.registerMode = false; this.username = this.regUsername; this.password = this.regPassword; },
      err => { this.snack.open('Registration failed: ' + (err?.error?.error || err?.message || 'unknown'), 'Close', { duration: 4000 }); }
    );
  }

  cancelRegister() { this.registerMode = false; this.regUsername = ''; this.regEmail = null; this.regPassword = ''; }
}
