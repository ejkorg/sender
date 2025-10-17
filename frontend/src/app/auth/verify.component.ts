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
  selector: 'app-verify',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatInputModule, MatButtonModule],
  template: `
  <mat-card style="max-width:480px;margin:24px auto;padding:16px;">
    <h3>Verify account</h3>
    <mat-form-field style="width:100%"><input matInput placeholder="Verification token" [(ngModel)]="token" /></mat-form-field>
    <div style="display:flex;gap:8px;justify-content:flex-end;">
      <button mat-button (click)="verify()">Verify</button>
      <button mat-button (click)="cancel()">Cancel</button>
    </div>
  </mat-card>
  `
})
export class VerifyComponent {
  token = '';
  constructor(private http: HttpClient, private snack: MatSnackBar, private router: Router) {}
  verify() {
    if (!this.token) { this.snack.open('token required', 'Close', { duration: 3000 }); return; }
    this.http.post('/api/auth/verify', { token: this.token }).subscribe(
      _ => { this.snack.open('Account verified', 'Close', { duration: 3000 }); this.router.navigateByUrl('/'); },
      err => { this.snack.open('Verification failed', 'Close', { duration: 4000 }); }
    );
  }
  cancel() { this.router.navigateByUrl('/'); }
}
