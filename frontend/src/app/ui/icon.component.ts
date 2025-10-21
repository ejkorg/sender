import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

/**
 * Lightweight Icon wrapper used during migration from Material icons -> SVG icons.
 * Default rendering mode is font (material-icons). Later we can switch to inline SVGs
 * by changing the implementation here without touching callers.
 */
@Component({
  selector: 'app-icon',
  standalone: true,
  imports: [CommonModule],
  template: `
    <ng-container [ngSwitch]="mode">
      <span *ngSwitchCase="'font'" class="material-icons" [attr.aria-hidden]="ariaHidden" [ngClass]="cssClass">{{ name }}</span>
      <span *ngSwitchDefault class="icon-svg" [innerHTML]="svgHtml" [ngClass]="cssClass" [attr.aria-hidden]="ariaHidden"></span>
    </ng-container>
  `
})
export class IconComponent {
  /** icon name when using font icons (e.g. 'download', 'autorenew') */
  @Input() name = '';
  /** optional classes to apply (Tailwind classes like 'w-5 h-5') */
  @Input('class') cssClass = '';
  /** if true, hide from accessibility tree */
  @Input() ariaHidden = 'true';
  /** rendering mode: 'font' (material font) or 'svg' */
  @Input() mode: 'font' | 'svg' = 'font';

  // placeholder for future svg support; keep empty for now
  get svgHtml(): string {
    // Provide some inlined SVGs keyed by `name` to avoid duplicating inline svgs throughout the app.
    // Add more icons here as needed.
    switch (this.name) {
      case 'theme':
        return `
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" focusable="false">
            <path d="M12 3v2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M12 19v2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M4.22 4.22l1.42 1.42" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M18.36 18.36l1.42 1.42" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M1 12h2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M21 12h2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M4.22 19.78l1.42-1.42" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M18.36 5.64l1.42-1.42" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        `;
      default:
        return '';
    }
  }
}
