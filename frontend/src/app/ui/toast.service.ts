import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

type ToastKind = 'success' | 'error' | 'info';

export interface ToastMessage {
  id: number;
  kind: ToastKind;
  message: string;
  dismissible: boolean;
}

@Injectable({ providedIn: 'root' })
export class ToastService implements OnDestroy {
  private counter = 0;
  private readonly stream = new BehaviorSubject<ToastMessage[]>([]);
  readonly toasts$ = this.stream.asObservable();
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  show(kind: ToastKind, message: string, options?: { durationMs?: number; dismissible?: boolean }): number {
    const id = ++this.counter;
    const toast: ToastMessage = {
      id,
      kind,
      message,
      dismissible: options?.dismissible ?? true
    };
    const duration = options?.durationMs ?? (kind === 'error' ? 7000 : 4500);
    this.stream.next([...this.stream.value, toast]);
    if (duration > 0) {
      const timer = setTimeout(() => this.dismiss(id), duration);
      this.timers.set(id, timer);
    }
    return id;
  }

  success(message: string, options?: { durationMs?: number; dismissible?: boolean }): number {
    return this.show('success', message, options);
  }

  error(message: string, options?: { durationMs?: number; dismissible?: boolean }): number {
    return this.show('error', message, options);
  }

  info(message: string, options?: { durationMs?: number; dismissible?: boolean }): number {
    return this.show('info', message, options);
  }

  dismiss(id: number): void {
    this.clearTimer(id);
    this.stream.next(this.stream.value.filter(toast => toast.id !== id));
  }

  clear(): void {
    for (const id of this.timers.keys()) {
      this.clearTimer(id);
    }
    this.stream.next([]);
  }

  private clearTimer(id: number): void {
    const timer = this.timers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
  }

  ngOnDestroy(): void {
    this.clear();
  }
}
