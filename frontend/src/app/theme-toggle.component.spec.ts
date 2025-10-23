import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ThemeToggleComponent } from './theme-toggle.component';
import { ThemeService } from './theme.service';
import { IconComponent } from './ui/icon.component';

describe('ThemeToggleComponent', () => {
  let component: ThemeToggleComponent;
  let fixture: ComponentFixture<ThemeToggleComponent>;
  let theme: ThemeService;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [ThemeToggleComponent],
      providers: [ThemeService],
    }).compileComponents();

    fixture = TestBed.createComponent(ThemeToggleComponent);
    component = fixture.componentInstance;
    theme = TestBed.inject(ThemeService);
  });

  it('initializes labels based on current theme', () => {
    theme.current = 'default';
    component.ngOnInit();
    expect(component.shortLabel).toBe('Default');
    expect(component.label).toContain('Default');

    theme.current = 'andromeda';
    component.ngOnInit();
    expect(component.shortLabel).toBe('Andromeda');
    expect(component.label).toContain('Andromeda');
  });

  it('toggle flips the theme and updates labels', () => {
    // start default
    theme.current = 'default';
    component.ngOnInit();
    component.toggle();
    expect(theme.current).toBe('andromeda');
    expect(component.shortLabel).toBe('Andromeda');

    component.toggle();
    expect(theme.current).toBe('default');
    expect(component.shortLabel).toBe('Default');
  });
});
