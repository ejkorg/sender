import { Component, OnInit } from '@angular/core';
import { ThemeService } from './theme.service';
import { IconComponent } from './ui/icon.component';

@Component({
  selector: 'app-theme-toggle',
  template: `
    <button
      (click)="toggle()"
      class="theme-toggle"
      aria-pressed="{{ theme.current === 'andromeda' }}"
      aria-label="Toggle theme"
      title="Toggle theme"
    >
      <app-icon mode="svg" name="theme" class="h-4 w-4" ariaHidden="true"></app-icon>
      <span class="sr-only">{{ label }}</span>
      <span class="hidden sm:inline text-sm">{{ shortLabel }}</span>
    </button>
  `,
  imports: [IconComponent],
})
export class ThemeToggleComponent implements OnInit {
  label = 'Theme';
  shortLabel = '';

  constructor(public theme: ThemeService) {}

  ngOnInit(): void {
    const cur = this.theme.current === 'andromeda' ? 'Andromeda' : 'Default';
    this.label = `${cur} theme active`;
    this.shortLabel = cur;
  }

  toggle() {
    const next = this.theme.current === 'andromeda' ? 'default' : 'andromeda';
    this.theme.current = next;
    const cur = next === 'andromeda' ? 'Andromeda' : 'Default';
    this.label = `${cur} theme active`;
    this.shortLabel = cur;
  }
}
