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
    return '';
  }
}
