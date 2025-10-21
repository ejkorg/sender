import { AsyncPipe, NgClass, NgForOf, NgIf } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [NgForOf, AsyncPipe, NgClass, NgIf],
  template: `
    <div class="pointer-events-none fixed inset-x-0 top-4 z-[999] flex justify-center px-4">
      <div class="flex w-full max-w-xl flex-col gap-3">
        <div *ngFor="let toast of toasts$ | async" class="pointer-events-auto rounded-xl border p-4 shadow-lg transition" [ngClass]="toastClass(toast.kind)">
          <div class="flex items-start gap-3">
            <div class="mt-1 h-2.5 w-2.5 flex-none rounded-full" [ngClass]="dotClass(toast.kind)"></div>
            <div class="flex-1 text-sm font-medium">{{ toast.message }}</div>
            <button type="button" class="text-xs font-semibold uppercase tracking-wide text-onsemi-charcoal/70 hover:text-onsemi-charcoal" *ngIf="toast.dismissible" (click)="dismiss(toast.id)">
              Dismiss
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ToastContainerComponent {
  private readonly toasts = inject(ToastService);
  readonly toasts$ = this.toasts.toasts$;

  toastClass(kind: 'success' | 'error' | 'info'): string {
    switch (kind) {
      case 'success':
        return 'border-green-200 bg-green-50/90 text-green-900 backdrop-blur-sm';
      case 'error':
        return 'border-red-200 bg-red-50/95 text-red-900 backdrop-blur-sm';
      default:
        return 'border-slate-200 bg-surface-95 text-slate-900 backdrop-blur-sm';
    }
  }

  dotClass(kind: 'success' | 'error' | 'info'): string {
    switch (kind) {
      case 'success':
        return 'bg-green-500';
      case 'error':
        return 'bg-red-500';
      default:
        return 'bg-slate-400';
    }
  }

  dismiss(id: number): void {
    this.toasts.dismiss(id);
  }
}
