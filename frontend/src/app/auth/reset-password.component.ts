import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { ToastService } from '../ui/toast.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="flex min-h-screen items-center justify-center bg-onsemi-ice px-4 py-16">
      <section class="w-full max-w-md rounded-2xl bg-white p-8 shadow-xl ring-1 ring-onsemi-primary/15">
        <h1 class="text-xl font-semibold text-onsemi-charcoal">Reset password</h1>
        <p class="mt-1 text-sm text-slate-600">Provide your reset token and choose a new password.</p>

        <form [formGroup]="form" (ngSubmit)="submit()" class="mt-8 space-y-6">
          <div>
            <label class="mb-2 block text-sm font-medium text-onsemi-charcoal" for="token">Reset token</label>
            <input
              id="token"
              type="text"
              formControlName="token"
              required
              class="block w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-onsemi-charcoal shadow-sm transition focus:border-onsemi-primary focus:outline-none focus:ring-2 focus:ring-onsemi-primary/40"
              [class.border-red-500]="tokenCtrl.invalid && tokenCtrl.touched"
            />
            <p class="mt-2 text-sm text-red-600" *ngIf="tokenCtrl.hasError('required') && tokenCtrl.touched">Reset token is required</p>
          </div>

          <div>
            <label class="mb-2 block text-sm font-medium text-onsemi-charcoal" for="password">New password</label>
            <input
              id="password"
              type="password"
              formControlName="password"
              autocomplete="new-password"
              required
              class="block w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-onsemi-charcoal shadow-sm transition focus:border-onsemi-primary focus:outline-none focus:ring-2 focus:ring-onsemi-primary/40"
              [class.border-red-500]="passwordCtrl.invalid && passwordCtrl.touched"
            />
            <p class="mt-2 text-sm text-red-600" *ngIf="passwordCtrl.hasError('required') && passwordCtrl.touched">Password is required</p>
            <p class="mt-2 text-sm text-red-600" *ngIf="passwordCtrl.hasError('minlength') && passwordCtrl.touched">Password must be at least 8 characters</p>
          </div>

          <button type="submit" class="btn-primary w-full py-3 text-base" [disabled]="form.invalid || submitting">
            <ng-container *ngIf="!submitting; else loading">Reset password</ng-container>
          </button>
          <ng-template #loading>
            <div class="flex items-center justify-center gap-2">
              <span class="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
              Updating…
            </div>
          </ng-template>
        </form>

        <div class="mt-8 text-center text-sm text-slate-600">
          <button class="font-semibold text-onsemi-primary hover:underline" type="button" (click)="gotoLogin()">Back to sign in</button>
        </div>
      </section>
    </div>
  `
})
export class ResetPasswordComponent implements OnInit, OnDestroy {
  submitting = false;

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private redirectTimer: ReturnType<typeof setTimeout> | null = null;

  readonly form = this.fb.nonNullable.group({
    token: ['', [Validators.required]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  get tokenCtrl() {
    return this.form.controls.token;
  }

  get passwordCtrl() {
    return this.form.controls.password;
  }

  submit(): void {
    if (this.form.invalid || this.submitting) {
      this.form.markAllAsTouched();
      return;
    }
    const { token, password } = this.form.getRawValue();
    this.submitting = true;
    this.auth.resetPassword(token, password).subscribe({
      next: () => {
        this.submitting = false;
        this.toast.success('Password updated. You can sign in with your new password.');
        this.startRedirect('/login');
      },
      error: err => {
        this.submitting = false;
        const reason = err?.error?.error || err?.message || 'Reset failed';
        this.toast.error(reason);
      }
    });
  }

  gotoLogin(): void {
    this.router.navigateByUrl('/login');
  }

  private startRedirect(url: string): void {
    this.redirectTimer = setTimeout(() => {
      this.router.navigateByUrl(url);
    }, 1500);
  }

  ngOnDestroy(): void {
    if (this.redirectTimer) {
      clearTimeout(this.redirectTimer);
      this.redirectTimer = null;
    }
  }

  ngOnInit(): void {
    try {
      const params = new URLSearchParams(window.location.search || '');
      const t = params.get('token');
      if (t) {
        this.form.controls.token.setValue(t);
      }
    } catch (e) {
      // ignore
    }
  }
}
