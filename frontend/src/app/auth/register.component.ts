import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from './auth.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { NgForm } from '@angular/forms';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
  <mat-card style="max-width:480px;margin:24px auto;padding:16px;">
    <h3>Create account</h3>
    <form #f="ngForm" (ngSubmit)="register(f)">
      <mat-form-field style="width:100%">
        <input matInput name="username" required minlength="3" [(ngModel)]="username" #usernameCtrl="ngModel" placeholder="Username" />
        <mat-error *ngIf="usernameCtrl.invalid && usernameCtrl.touched">
          <span *ngIf="usernameCtrl.errors?.['required']">Username is required</span>
          <span *ngIf="usernameCtrl.errors?.['minlength']">Username must be at least 3 characters</span>
        </mat-error>
      </mat-form-field>
      <mat-form-field style="width:100%"><input matInput name="email" type="email" [(ngModel)]="email" #emailCtrl="ngModel" placeholder="Email (optional)" /></mat-form-field>
      <mat-form-field style="width:100%">
        <input matInput name="password" required minlength="8" type="password" [(ngModel)]="password" #passwordCtrl="ngModel" placeholder="Password" />
        <mat-error *ngIf="passwordCtrl.invalid && passwordCtrl.touched">
          <span *ngIf="passwordCtrl.errors?.['required']">Password is required</span>
          <span *ngIf="passwordCtrl.errors?.['minlength']">Password must be at least 8 characters</span>
        </mat-error>
      </mat-form-field>
      <div style="display:flex;gap:8px;justify-content:flex-end;">
        <button mat-button type="submit" [disabled]="f.invalid">Create</button>
        <button mat-button type="button" (click)="cancel()">Cancel</button>
      </div>
    </form>
  </mat-card>
  `
})
export class RegisterComponent {
  username = '';
  email: string | null = null;
  password = '';

  constructor(private auth: AuthService, private snack: MatSnackBar, private router: Router) {}

  register(form?: NgForm) {
    if (form && form.invalid) {
      // mark controls as touched to show inline validation messages
      Object.values((form as any).controls || {}).forEach((c: any) => c.markAsTouched());
      return;
    }

    if (!form && (!this.username || this.password.length < 8)) {
      this.snack.open('username required and password >= 8 chars', 'Close', { duration: 4000 });
      return;
    }

    this.auth.register(this.username, this.email, this.password).subscribe(
      (res) => {
        const token = (res as any)?.verificationToken as string | undefined;
        this.snack.open('Registered — verify your account', 'Close', { duration: 4000 });
        if (token) {
          this.router.navigate(['/verify'], { queryParams: { token } });
        } else {
          this.router.navigateByUrl('/verify');
        }
      },
      err => {
        this.snack.open('Registration failed: ' + (err?.error?.error || err?.message || 'unknown'), 'Close', { duration: 4000 });
      }
    );
  }

  cancel() { this.router.navigateByUrl('/login'); }
}
