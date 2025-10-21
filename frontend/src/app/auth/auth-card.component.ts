import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-auth-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex items-center justify-center bg-onsemi-ice px-4 auth-wrapper">
      <section [ngClass]="['w-full', maxWidthClass, 'rounded-2xl', 'bg-white', paddingClass, 'shadow-xl', 'ring-1', 'ring-onsemi-primary/15']">
        <ng-content></ng-content>
      </section>
    </div>
  `
})
export class AuthCardComponent {
  @Input()
  maxWidth: 'md' | 'xl' = 'md';

  @Input()
  paddingClass = 'p-8';

  get maxWidthClass(): string {
    return this.maxWidth === 'xl' ? 'max-w-xl' : 'max-w-md';
  }
}
