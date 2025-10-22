import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-button',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button
      [attr.type]="type"
      [disabled]="disabled"
      (click)="handleClick($event)"
      [ngClass]="classes"
    >
      <ng-content></ng-content>
    </button>
  `
})
export class ButtonComponent {
  @Input() variant: 'primary' | 'secondary' | 'ghost' = 'secondary';
  @Input() size: 'sm' | 'md' | 'lg' = 'md';
  @Input() type: 'button' | 'submit' | 'reset' = 'button';
  @Input() disabled = false;
  @Input('class') extra = '';
  @Output() click = new EventEmitter<Event>();

  get classes(): string {
    const base = 'inline-flex items-center justify-center font-semibold rounded focus:outline-none focus-visible:outline focus-visible:outline-2';
    const sizeCls = this.size === 'sm' ? 'px-3 py-1.5 text-sm h-8' : this.size === 'lg' ? 'px-6 py-3 text-base h-12' : 'px-4 py-2 text-sm h-10';
    // use centralized btn-* utility classes defined in styles.scss so styles use tokens
    const variantCls = {
      primary: 'btn-primary',
      secondary: 'btn-secondary',
      ghost: 'btn-ghost'
    }[this.variant];
    const disabledCls = this.disabled ? 'opacity-50 cursor-not-allowed' : '';
    return [base, sizeCls, variantCls, disabledCls, this.extra].filter(Boolean).join(' ');
  }

  handleClick(ev: Event) {
    if (this.disabled) {
      ev.preventDefault();
      return;
    }
    this.click.emit(ev);
  }
}
