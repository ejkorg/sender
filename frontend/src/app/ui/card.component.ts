import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section [ngClass]="containerClasses">
      <ng-content></ng-content>
    </section>
  `
})
export class CardComponent {
  @Input() variant: 'elevated' | 'flat' = 'elevated';
  get containerClasses(): string {
    const base = 'rounded-2xl p-6';
    const variantCls = this.variant === 'elevated' ? 'shadow-md bg-white border' : 'bg-transparent';
    return [base, variantCls].join(' ');
  }
}
