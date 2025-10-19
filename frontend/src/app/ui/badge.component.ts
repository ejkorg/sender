import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span [ngClass]="classes"><ng-content></ng-content></span>
  `
})
export class BadgeComponent {
  @Input() variant: 'default' | 'highlight' | 'warn' | 'muted' = 'default';

  get classes(): string {
    const base = 'inline-flex items-center justify-center rounded-full px-3 py-1 font-semibold text-sm';
    const variantMap: Record<string,string> = {
      default: 'bg-gray-100 text-onsemi-charcoal',
      highlight: 'bg-onsemi-primary/20 text-onsemi-charcoal',
      warn: 'bg-red-100 text-red-600',
      muted: 'bg-gray-50 text-gray-600'
    };
    return [base, variantMap[this.variant] || variantMap['default']].join(' ');
  }
}
