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
    const base = 'badge';
    const variantMap: Record<string,string> = {
      default: '',
      highlight: 'highlight',
      warn: 'warn',
      muted: 'muted'
    };
    return [base, variantMap[this.variant] || variantMap['default']].filter(Boolean).join(' ');
  }
}
