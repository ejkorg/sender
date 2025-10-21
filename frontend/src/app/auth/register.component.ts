import { Component, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { ToastService } from '../ui/toast.service';
import { AuthCardComponent } from './auth-card.component';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AuthCardComponent],
  template: `
    <app-auth-card [maxWidth]="'xl'" [paddingClass]="'p-10'">
      <div class="mb-8 space-y-2 text-center">
        <h1 class="text-2xl font-semibold text-onsemi-charcoal">Create your account</h1>
        <p class="text-sm text-slate-600">Register to access the sender tools.</p>
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
              class="block w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-onsemi-charcoal shadow-sm transition focus:border-onsemi-primary focus:outline-none focus:ring-2 focus:ring-onsemi-primary/40"
              [class.border-red-500]="usernameCtrl.invalid && usernameCtrl.touched"
            />
            <p class="mt-2 text-sm text-red-600" *ngIf="usernameCtrl.hasError('required') && usernameCtrl.touched">Username is required</p>
            <p class="mt-2 text-sm text-red-600" *ngIf="usernameCtrl.hasError('minlength') && usernameCtrl.touched">Use at least 3 characters</p>
          </div>

          <div>
            <label class="mb-2 block text-sm font-medium text-onsemi-charcoal" for="email">Email (optional)</label>
            <input
              id="email"
              type="email"
              formControlName="email"
              autocomplete="email"
              class="block w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-onsemi-charcoal shadow-sm transition focus:border-onsemi-primary focus:outline-none focus:ring-2 focus:ring-onsemi-primary/40"
              [class.border-red-500]="emailCtrl.invalid && emailCtrl.touched"
            />
            <p class="mt-2 text-sm text-red-600" *ngIf="emailCtrl.hasError('email') && emailCtrl.touched">Provide a valid email address</p>
          </div>

          <div>
            <label class="mb-2 block text-sm font-medium text-onsemi-charcoal" for="password">Password</label>
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
            <ng-container *ngIf="!submitting; else loading">Create account</ng-container>
          </button>
          <ng-template #loading>
            <div class="flex items-center justify-center gap-2">
              <span class="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
              Creating…
            </div>
          </ng-template>
        </form>

        <div class="mt-8 text-center text-sm text-slate-600">
          Already have an account?
          <button class="font-semibold text-onsemi-primary hover:underline" type="button" (click)="gotoLogin()">Sign in</button>
        </div>
    </app-auth-card>
  `
})
export class RegisterComponent implements OnDestroy {
  submitting = false;

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private redirectTimer: ReturnType<typeof setTimeout> | null = null;

  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  get usernameCtrl() {
    return this.form.controls.username;
  }

  get emailCtrl() {
    return this.form.controls.email;
  }

  get passwordCtrl() {
    return this.form.controls.password;
  }

  submit(): void {
    if (this.form.invalid || this.submitting) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, email, password } = this.form.getRawValue();
    this.submitting = true;
    const sendEmail = email && email.trim() !== '' ? email.trim() : null;
    this.auth.register(username, sendEmail, password).subscribe({
      next: res => {
        this.submitting = false;
        const token = res?.verificationToken;
        this.toast.success('Registered — verify your account to continue.');
        if (token) {
          this.startRedirect(`/verify?token=${encodeURIComponent(token)}`);
        } else {
          this.startRedirect('/verify');
        }
      },
      error: err => {
        this.submitting = false;
        const reason = err?.error?.error || err?.message || 'Registration failed';
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
    }, 1200);
  }

  ngOnDestroy(): void {
    if (this.redirectTimer) {
      clearTimeout(this.redirectTimer);
      this.redirectTimer = null;
    }
  }
}
