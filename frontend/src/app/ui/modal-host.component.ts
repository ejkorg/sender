import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit, ViewChild, ViewContainerRef } from '@angular/core';
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

  constructor(private modal: ModalService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    // register our host view container with the modal service
    try {
      this.modal.registerHost(this.vc);
    } catch (err) {
      // ignore
    }

    this.sub = this.modal.opened$.subscribe(inst => {
      this.vc.clear();
      this.currentId = inst?.id ?? null;
      if (inst && inst.componentRef === null) {
        // host will wait for attachRef from caller if they're doing dynamic creation
      }
      this.active = !!inst;
      this.cdr.markForCheck();
      if (this.active) {
        // focus trap: focus host container
        setTimeout(() => {
          try { (this.vc.element.nativeElement as HTMLElement).focus(); } catch {}
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

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.currentId) {
      this.modal.close(this.currentId);
    }
  }
}
