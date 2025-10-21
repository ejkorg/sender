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
      default: 'bg-[color:var(--badge-bg,rgba(15,23,36,0.04))] text-[color:var(--onsemi-charcoal)]',
      highlight: 'bg-[linear-gradient(90deg,var(--onsemi-primary),var(--onsemi-primary-light))] text-white',
      warn: 'bg-[linear-gradient(90deg,#ff6b6b,#ff8a8a)] text-white',
      muted: 'bg-[color:var(--badge-muted-bg,#f8fafc)] text-gray-600'
    };
    return [base, variantMap[this.variant] || variantMap['default']].join(' ');
  }
}
