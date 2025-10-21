import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { ToastService } from '../ui/toast.service';

@Component({
  selector: 'app-verify',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="flex min-h-screen items-center justify-center bg-onsemi-ice px-4 py-16">
      <section class="w-full max-w-md rounded-2xl bg-surface p-8 shadow-xl ring-1 ring-onsemi-primary/15">
        <h1 class="text-xl font-semibold text-onsemi-charcoal">Verify account</h1>
        <p class="mt-1 text-sm text-slate-600">Paste the verification token from your email to activate your account.</p>

        <form [formGroup]="form" (ngSubmit)="submit()" class="mt-8 space-y-6">
          <div>
            <label class="mb-2 block text-sm font-medium text-onsemi-charcoal" for="token">Verification token</label>
            <textarea
              id="token"
              rows="3"
              formControlName="token"
              class="block w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-onsemi-charcoal shadow-sm transition focus:border-onsemi-primary focus:outline-none focus:ring-2 focus:ring-onsemi-primary/40"
              [class.border-red-500]="tokenCtrl.invalid && tokenCtrl.touched"
            ></textarea>
            <p class="mt-2 text-sm text-red-600" *ngIf="tokenCtrl.hasError('required') && tokenCtrl.touched">Verification token is required</p>
          </div>

          <button type="submit" class="btn-primary w-full py-3 text-base" [disabled]="form.invalid || verifying">
            <ng-container *ngIf="!verifying; else loading">Verify account</ng-container>
          </button>
          <ng-template #loading>
            <div class="flex items-center justify-center gap-2">
              <span class="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
              Verifying…
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
export class VerifyComponent implements OnInit {
  verifying = false;

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly form = this.fb.nonNullable.group({
    token: ['', [Validators.required]]
  });

  get tokenCtrl() {
    return this.form.controls.token;
  }

  ngOnInit(): void {
    const params = new URLSearchParams(location.search);
    const token = params.get('token');
    if (token) {
      this.form.patchValue({ token });
      this.submit();
    }
  }

  submit(): void {
    if (this.form.invalid || this.verifying) {
      this.form.markAllAsTouched();
      return;
    }
    const { token } = this.form.getRawValue();
    this.verifying = true;
    this.auth.verifyAccount(token).subscribe({
      next: () => {
        this.verifying = false;
        this.toast.success('Account verified. You can sign in now.');
        this.router.navigateByUrl('/login');
      },
      error: err => {
        this.verifying = false;
        const reason = err?.error?.error || err?.message || 'Verification failed';
        this.toast.error(reason);
      }
    });
  }

  gotoLogin(): void {
    this.router.navigateByUrl('/login');
  }
}
