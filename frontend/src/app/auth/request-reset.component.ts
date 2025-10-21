import { Component, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { ToastService } from '../ui/toast.service';
import { AuthCardComponent } from './auth-card.component';

@Component({
  selector: 'app-request-reset',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AuthCardComponent],
  template: `
    <app-auth-card>
      <h1 class="text-xl font-semibold text-onsemi-charcoal">Request password reset</h1>
      <p class="mt-1 text-sm text-slate-600">Enter your username or email and we will send reset instructions.</p>

      <form [formGroup]="form" (ngSubmit)="submit()" class="mt-8 space-y-6">
          <div>
            <label class="mb-2 block text-sm font-medium text-onsemi-charcoal" for="identifier">Username or email</label>
            <input
              id="identifier"
              type="text"
              formControlName="identifier"
              autocomplete="username"
              required
              class="block w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-onsemi-charcoal shadow-sm transition focus:border-onsemi-primary focus:outline-none focus:ring-2 focus:ring-onsemi-primary/40"
              [class.border-red-500]="identifierCtrl.invalid && identifierCtrl.touched"
            />
            <p class="mt-2 text-sm text-red-600" *ngIf="identifierCtrl.hasError('required') && identifierCtrl.touched">Username or email is required</p>
          </div>

          <button type="submit" class="btn-primary w-full py-3 text-base" [disabled]="form.invalid || submitting">
            <ng-container *ngIf="!submitting; else loading">Send reset link</ng-container>
          </button>
          <ng-template #loading>
            <div class="flex items-center justify-center gap-2">
              <span class="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
              Sending…
            </div>
          </ng-template>
        </form>

      <div class="mt-8 text-center text-sm text-slate-600">
        <button class="font-semibold text-onsemi-primary hover:underline" type="button" (click)="gotoLogin()">Back to sign in</button>
      </div>
    </app-auth-card>
  `
})
export class RequestResetComponent implements OnDestroy {
  submitting = false;

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private redirectTimer: ReturnType<typeof setTimeout> | null = null;

  readonly form = this.fb.nonNullable.group({
    identifier: ['', [Validators.required]]
  });

  get identifierCtrl() {
    return this.form.controls.identifier;
  }

  submit(): void {
    if (this.form.invalid || this.submitting) {
      this.form.markAllAsTouched();
      return;
    }
    const { identifier } = this.form.getRawValue();
    this.submitting = true;
    this.auth.requestPasswordReset(identifier).subscribe({
      next: res => {
        this.submitting = false;
        const token = res?.resetToken;
        this.toast.success('If the account exists, reset instructions are on the way.');
        if (token) {
          this.toast.info(`Dev token: ${token}`, { durationMs: 8000 });
        }
        this.startRedirect('/');
      },
      error: err => {
        this.submitting = false;
        const reason = err?.error?.error || err?.message || 'Reset request failed';
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
}
