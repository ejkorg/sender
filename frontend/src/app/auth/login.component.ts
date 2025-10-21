import { Component, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthCardComponent } from './auth-card.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AuthCardComponent],
  template: `
    <app-auth-card>
      <div class="mb-6 text-center">
        <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-onsemi-primary/10">
          <span class="text-lg font-semibold uppercase tracking-wide text-onsemi-primary">ON</span>
        </div>
        <h1 class="text-2xl font-semibold text-onsemi-charcoal">Welcome back</h1>
        <p class="mt-1 text-sm text-onsemi-charcoal/70">Manage resend requests and sender queues.</p>
      </div>

      <form [formGroup]="form" (ngSubmit)="submit()" class="space-y-6">
          <div>
            <label class="mb-2 block text-sm font-medium text-onsemi-charcoal" for="username">Username</label>
            <input
              id="username"
              type="text"
              formControlName="username"
              autocomplete="username"
              required
              class="block w-full rounded-xl border border-slate-300 bg-surface px-4 py-3 text-sm text-onsemi-charcoal shadow-sm transition focus:border-onsemi-primary focus:outline-none focus:ring-2 focus:ring-onsemi-primary/40"
              [class.border-red-500]="usernameControl.invalid && usernameControl.touched"
              [class.focus\:ring-red-200]="usernameControl.invalid && usernameControl.touched"
            />
            <p class="mt-2 text-sm text-red-600" *ngIf="usernameControl.hasError('required') && usernameControl.touched">Username is required</p>
            <p class="mt-2 text-sm text-red-600" *ngIf="usernameControl.hasError('minlength') && usernameControl.touched">Use at least 3 characters</p>
          </div>

          <div>
            <label class="mb-2 block text-sm font-medium text-onsemi-charcoal" for="password">Password</label>
            <input
              id="password"
              type="password"
              formControlName="password"
              autocomplete="current-password"
              required
              class="block w-full rounded-xl border border-slate-300 bg-surface px-4 py-3 text-sm text-onsemi-charcoal shadow-sm transition focus:border-onsemi-primary focus:outline-none focus:ring-2 focus:ring-onsemi-primary/40"
              [class.border-red-500]="passwordControl.invalid && passwordControl.touched"
              [class.focus\:ring-red-200]="passwordControl.invalid && passwordControl.touched"
            />
            <p class="mt-2 text-sm text-red-600" *ngIf="passwordControl.hasError('required') && passwordControl.touched">Password is required</p>
            <p class="mt-2 text-sm text-red-600" *ngIf="passwordControl.hasError('minlength') && passwordControl.touched">Password must be at least 8 characters</p>
          </div>

          <div *ngIf="status as s" class="rounded-lg border px-3 py-2 text-sm" [ngClass]="s.kind === 'success' ? 'border-green-500 bg-green-50 text-green-800' : 'border-red-500 bg-red-50 text-red-800'">
            {{ s.message }}
          </div>

          <button
            type="submit"
            class="btn-primary w-full py-3 text-base"
            [disabled]="form.invalid || submitting"
          >
            <ng-container *ngIf="!submitting; else loadingState">Sign in</ng-container>
          </button>
          <ng-template #loadingState>
            <div class="flex items-center justify-center gap-2">
              <span class="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
              Signing in…
            </div>
          </ng-template>
        </form>

        <div class="mt-8 flex flex-col gap-3 text-center text-sm text-slate-600 sm:flex-row sm:items-center sm:justify-between">
          <button class="btn-secondary justify-center" type="button" (click)="gotoRegister()">Create account</button>
          <button class="text-sm font-medium text-onsemi-primary hover:underline" type="button" (click)="gotoReset()">Forgot password?</button>
        </div>
    </app-auth-card>
  `
})
export class LoginComponent implements OnDestroy {
  submitting = false;
  status: { kind: 'success' | 'error'; message: string } | null = null;
  private statusTimeout: ReturnType<typeof setTimeout> | null = null;

  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly routerService = inject(Router);

  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  get usernameControl() {
    return this.form.controls.username;
  }

  get passwordControl() {
    return this.form.controls.password;
  }

  submit(): void {
    if (this.form.invalid || this.submitting) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.submitting = true;
    this.authService.login(username, password).subscribe({
      next: () => {
        this.submitting = false;
        this.setStatus('success', 'Signed in successfully');
        this.routerService.navigateByUrl('/');
      },
      error: err => {
        this.submitting = false;
        const reason = err?.error?.error || err?.message || 'Login failed';
        this.setStatus('error', reason);
      }
    });
  }

  gotoRegister(): void {
    this.routerService.navigateByUrl('/register');
  }

  gotoReset(): void {
    this.routerService.navigateByUrl('/request-reset');
  }

  ngOnDestroy(): void {
    this.clearStatusTimeout();
  }

  private setStatus(kind: 'success' | 'error', message: string): void {
    this.clearStatusTimeout();
    this.status = { kind, message };
    this.statusTimeout = setTimeout(() => {
      this.status = null;
      this.statusTimeout = null;
    }, 4500);
  }

  private clearStatusTimeout(): void {
    if (this.statusTimeout) {
      clearTimeout(this.statusTimeout);
      this.statusTimeout = null;
    }
  }
}
