import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private key = 'app-theme';

  get current(): string {
    return localStorage.getItem(this.key) || 'default';
  }

  set current(theme: string) {
    localStorage.setItem(this.key, theme);
    this.apply(theme);
  }

  apply(theme: string) {
    if (theme === 'andromeda') {
      document.documentElement.setAttribute('data-theme', 'andromeda');
    } else {
      document.documentElement.removeAttribute('data-theme');
    }
  }

  init() {
    this.apply(this.current);
  }
}
