import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div style="display:flex; gap:8px; align-items:center;">
      <ng-container *ngIf="(auth.user$ | async) as u; else loginForm">
        <span>{{ u.username }}</span>
        <button type="button" (click)="logout()">Logout</button>
      </ng-container>
      <ng-template #loginForm>
        <div style="display:flex; flex-direction:column; gap:8px;">
          <div *ngIf="!registerMode">
            <input [(ngModel)]="username" placeholder="username" (keydown.enter)="login($event)" />
            <input [(ngModel)]="password" type="password" placeholder="password" (keydown.enter)="login($event)" />
            <div style="display:flex; gap:8px;">
              <button type="button" (click)="login($event)">Login</button>
              <button type="button" (click)="registerMode = true">Register</button>
            </div>
          </div>
          <div *ngIf="registerMode">
            <input [(ngModel)]="regUsername" placeholder="username" />
            <input [(ngModel)]="regEmail" placeholder="email (optional)" />
            <input [(ngModel)]="regPassword" type="password" placeholder="password" />
            <div style="display:flex; gap:8px;">
              <button type="button" (click)="register()">Create account</button>
              <button type="button" (click)="cancelRegister()">Cancel</button>
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

  constructor(public auth: AuthService) {}

  login(event?: Event) {
    event?.preventDefault();
    event?.stopPropagation();
    if (!this.username || !this.password) return;
    this.auth.login(this.username, this.password).subscribe(
      _ => {},
      err => { alert('Login failed'); }
    );
  }

  logout() { this.auth.logout(); }

  register() {
    if (!this.regUsername || !this.regPassword || this.regPassword.length < 8) {
      alert('username and password (>=8) required');
      return;
    }
    this.auth.register(this.regUsername, this.regEmail, this.regPassword).subscribe(
      _ => { this.registerMode = false; this.username = this.regUsername; this.password = this.regPassword; },
      err => { alert('Registration failed: ' + (err?.error?.error || err?.message || 'unknown')); }
    );
  }

  cancelRegister() { this.registerMode = false; this.regUsername = ''; this.regEmail = null; this.regPassword = ''; }
}
