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
        <button (click)="logout()">Logout</button>
      </ng-container>
      <ng-template #loginForm>
        <input [(ngModel)]="username" placeholder="username" />
        <input [(ngModel)]="password" type="password" placeholder="password" />
        <button (click)="login()">Login</button>
      </ng-template>
    </div>
  `
})
export class LoginComponent {
  username = '';
  password = '';

  constructor(public auth: AuthService) {}

  login() {
    if (!this.username || !this.password) return;
    this.auth.login(this.username, this.password).subscribe(
      _ => {},
      err => { alert('Login failed'); }
    );
  }

  logout() { this.auth.logout(); }
}
