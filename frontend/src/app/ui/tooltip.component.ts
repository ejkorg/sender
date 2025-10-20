import { Component, HostListener, Input, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-tooltip',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="relative inline-block" (mouseenter)="show()" (mouseleave)="hide()" (focusin)="show()" (focusout)="hide()" tabindex="-1">
      <ng-content></ng-content>
      <div *ngIf="visible" role="tooltip" [attr.id]="id" class="absolute z-50 mt-2 whitespace-nowrap rounded-md bg-slate-800 text-white text-xs px-2 py-1">
        {{ text }}
      </div>
    </span>
  `
})
export class TooltipComponent {
  @Input() text = '';
  visible = false;
  id = `tooltip-${Math.floor(Math.random() * 1e9)}`;

  constructor(private el: ElementRef<HTMLElement>) {}

  show() {
    this.visible = true;
    try { this.el.nativeElement.setAttribute('aria-describedby', this.id); } catch {}
  }

  hide() {
    this.visible = false;
    try { this.el.nativeElement.removeAttribute('aria-describedby'); } catch {}
  }
}
