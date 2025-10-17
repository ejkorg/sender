import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { HttpClient } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

@Component({
  selector: 'app-request-reset',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatInputModule, MatButtonModule],
  template: `
  <mat-card style="max-width:480px;margin:24px auto;padding:16px;">
    <h3>Request password reset</h3>
    <mat-form-field style="width:100%"><input matInput placeholder="username or email" [(ngModel)]="username" /></mat-form-field>
    <div style="display:flex;gap:8px;justify-content:flex-end;">
      <button mat-button (click)="request()">Request</button>
      <button mat-button (click)="cancel()">Cancel</button>
    </div>
  </mat-card>
  `
})
export class RequestResetComponent {
  username = '';
  constructor(private http: HttpClient, private snack: MatSnackBar, private router: Router) {}
  request() {
    if (!this.username) { this.snack.open('username required', 'Close', { duration: 3000 }); return; }
    this.http.post('/api/auth/request-reset', { username: this.username }).subscribe(
      (res: any) => { const token = res?.resetToken; this.snack.open('Reset requested: ' + (token? token : ''), 'Close', { duration: 6000 }); this.router.navigateByUrl('/'); },
      err => { this.snack.open('Request failed', 'Close', { duration: 4000 }); }
    );
  }
  cancel() { this.router.navigateByUrl('/'); }
}
