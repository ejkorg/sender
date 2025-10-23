import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  let service: ThemeService;

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    TestBed.configureTestingModule({ providers: [ThemeService] });
    service = TestBed.inject(ThemeService);
  });

  it('defaults to "default" when no theme set', () => {
    expect(service.current).toBe('default');
  });

  it('persists current theme to localStorage when set', () => {
    service.current = 'andromeda';
    expect(localStorage.getItem('app-theme')).toBe('andromeda');
  });

  it('apply sets data-theme attribute for andromeda', () => {
    service.apply('andromeda');
    expect(document.documentElement.getAttribute('data-theme')).toBe('andromeda');
  });

  it('apply removes data-theme for default', () => {
    document.documentElement.setAttribute('data-theme', 'andromeda');
    service.apply('default');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
  });

  it('init applies stored theme', () => {
    localStorage.setItem('app-theme', 'andromeda');
    service.init();
    expect(document.documentElement.getAttribute('data-theme')).toBe('andromeda');
  });
});
