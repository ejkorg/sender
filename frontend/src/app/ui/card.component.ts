import { CommonModule } from '@angular/common';
import { Component, Input, HostBinding } from '@angular/core';

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
  @HostBinding('class') hostClass = 'app-card';
  @HostBinding('attr.variant') get hostVariant() { return this.variant; }

  // container classes used inside the host section
  get containerClasses(): string {
    const base = 'rounded-2xl p-6 app-card__container';
    const variantClass = this.variant === 'elevated' ? 'app-card__elevated' : 'app-card__flat';
    return [base, variantClass].join(' ');
  }
}
