import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { HttpClient } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { NgForm } from '@angular/forms';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatInputModule, MatButtonModule],
  template: `
  <mat-card style="max-width:480px;margin:24px auto;padding:16px;">
    <h3>Reset password</h3>
    <form #rf="ngForm" (ngSubmit)="reset(rf)">
      <mat-form-field style="width:100%">
        <input matInput name="token" placeholder="Reset token" required [(ngModel)]="token" #tokenCtl="ngModel" />
        <mat-error *ngIf="tokenCtl.invalid && tokenCtl.touched">Reset token is required</mat-error>
      </mat-form-field>
      <mat-form-field style="width:100%">
        <input matInput name="password" placeholder="New password" required minlength="8" type="password" [(ngModel)]="password" #passwordCtl="ngModel" />
        <mat-error *ngIf="passwordCtl.invalid && passwordCtl.touched">
          <span *ngIf="passwordCtl.errors?.['required']">Password is required</span>
          <span *ngIf="passwordCtl.errors?.['minlength']">Password must be at least 8 characters</span>
        </mat-error>
      </mat-form-field>
      <div style="display:flex;gap:8px;justify-content:flex-end;">
        <button mat-button type="submit" [disabled]="rf.invalid">Reset</button>
        <button mat-button type="button" (click)="cancel()">Cancel</button>
      </div>
    </form>
  </mat-card>
  `
})
export class ResetPasswordComponent {
  token = '';
  password = '';
  constructor(private http: HttpClient, private snack: MatSnackBar, private router: Router) {}
  reset(form?: NgForm) {
    if (form && form.invalid) {
      Object.values((form as any).controls || {}).forEach((c: any) => c.markAsTouched());
      return;
    }
    if (!form && (!this.token || this.password.length < 8)) { this.snack.open('token and password >=8 required', 'Close', { duration: 3000 }); return; }
    this.http.post('/api/auth/reset-password', { token: this.token, password: this.password }).subscribe(
      _ => { this.snack.open('Password reset', 'Close', { duration: 3000 }); this.router.navigateByUrl('/'); },
      err => { this.snack.open('Reset failed', 'Close', { duration: 4000 }); }
    );
  }
  cancel() { this.router.navigateByUrl('/'); }
}
