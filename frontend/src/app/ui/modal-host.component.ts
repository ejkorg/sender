import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit, Renderer2, ViewChild, ViewContainerRef } from '@angular/core';
import { ModalService } from './modal.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-modal-host',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div *ngIf="active" class="fixed inset-0 z-50 flex items-center justify-center">
      <div class="absolute inset-0 bg-black/50" (click)="onBackdropClick()" aria-hidden="true"></div>
      <div #container role="dialog" aria-modal="true" class="relative z-10 w-full max-w-3xl p-6">
        <ng-template #vc></ng-template>
      </div>
    </div>
  `
})
export class ModalHostComponent implements OnInit, OnDestroy {
  @ViewChild('vc', { read: ViewContainerRef, static: true }) vc!: ViewContainerRef;
  active = false;
  private sub?: Subscription;
  private currentId: string | null = null;

  // saved element to restore focus after modal closes
  private previouslyFocused: HTMLElement | null = null;

  constructor(private modal: ModalService, private cdr: ChangeDetectorRef, private renderer: Renderer2) {}

  ngOnInit(): void {
    // register our host view container with the modal service
    try {
      this.modal.registerHost(this.vc);
    } catch (err) {
      // ignore
    }

    this.sub = this.modal.opened$.subscribe(inst => {
      // clear existing content then render new instance when caller attaches
      this.vc.clear();
      this.currentId = inst?.id ?? null;
      this.active = !!inst;
      this.cdr.markForCheck();

      if (this.active) {
        // save currently focused element so we can restore later
        try {
          this.previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        } catch {
          this.previouslyFocused = null;
        }

        // small delay to let the component attach; then focus the first focusable element
        setTimeout(() => this.focusFirstFocusable(), 0);
      } else {
        // modal closed: restore focus
        setTimeout(() => {
          try { this.previouslyFocused?.focus(); } catch {}
          this.previouslyFocused = null;
        }, 0);
      }
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  onBackdropClick(): void {
    if (this.currentId) {
      this.modal.close(this.currentId);
    }
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(ev: KeyboardEvent): void {
    if (!this.active) return;
    // ESC closes
    if (ev.key === 'Escape' || ev.key === 'Esc') {
      ev.preventDefault();
      if (this.currentId) this.modal.close(this.currentId);
      return;
    }

    // trap tab navigation
    if (ev.key === 'Tab') {
      this.handleTab(ev);
    }
  }

  private focusFirstFocusable(): void {
    try {
      const root = this.vc.element.nativeElement as HTMLElement;
      if (!root) return;
      // find first focusable element inside the container
      const focusable = root.querySelectorAll<HTMLElement>("a[href], button:not([disabled]), textarea, input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex='-1'])");
      if (focusable && focusable.length) {
        focusable[0].focus();
        return;
      }
      // fallback: focus the host container
      try { root.focus(); } catch {}
    } catch {}
  }

  private handleTab(ev: KeyboardEvent): void {
    try {
      const root = this.vc.element.nativeElement as HTMLElement;
      if (!root) return;
      const focusable = Array.from(root.querySelectorAll<HTMLElement>("a[href], button:not([disabled]), textarea, input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex='-1'])")).filter(el => el.offsetParent !== null);
      if (!focusable.length) {
        ev.preventDefault();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement as HTMLElement | null;
      if (!ev.shiftKey && active === last) {
        ev.preventDefault();
        first.focus();
      } else if (ev.shiftKey && active === first) {
        ev.preventDefault();
        last.focus();
      }
    } catch {}
  }
}
