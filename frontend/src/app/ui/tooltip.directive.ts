import { Directive, ElementRef, HostListener, Input, Renderer2, OnDestroy, AfterViewInit } from '@angular/core';

@Directive({
  selector: '[appTooltip]',
  standalone: true
})
export class TooltipDirective implements AfterViewInit, OnDestroy {
  @Input('appTooltip') text = '';
  private tooltipEl: HTMLElement | null = null;
  private showTimeout = 0;
  private hideTimeout = 0;

  constructor(private host: ElementRef<HTMLElement>, private renderer: Renderer2) {}

  ngAfterViewInit(): void {
    // ensure host is focusable for keyboard users if it isn't already
    const el = this.host.nativeElement;
    const tabIndex = el.getAttribute('tabindex');
    if (!tabIndex && el.tabIndex === -1) {
      // don't override if the element is intentionally not focusable
      this.renderer.setAttribute(el, 'tabindex', '0');
    }
  }

  ngOnDestroy(): void {
    this.cleanUp();
  }

  @HostListener('mouseenter')
  @HostListener('focus')
  onShow() {
    window.clearTimeout(this.hideTimeout);
    this.showTimeout = window.setTimeout(() => this.createTooltip(), 150);
  }

  @HostListener('mouseleave')
  @HostListener('blur')
  onHide() {
    window.clearTimeout(this.showTimeout);
    this.hideTimeout = window.setTimeout(() => this.destroyTooltip(), 50);
  }

  private createTooltip() {
    if (this.tooltipEl) return;
    const host = this.host.nativeElement;
    const id = `tooltip-${Math.floor(Math.random() * 1e9)}`;
    const tip = this.renderer.createElement('div') as HTMLElement;
    this.renderer.setAttribute(tip, 'role', 'tooltip');
    this.renderer.setAttribute(tip, 'id', id);
    this.renderer.addClass(tip, 'absolute');
    this.renderer.addClass(tip, 'z-50');
    this.renderer.addClass(tip, 'whitespace-nowrap');
    this.renderer.addClass(tip, 'rounded-md');
    this.renderer.addClass(tip, 'bg-slate-800');
    this.renderer.addClass(tip, 'text-white');
    this.renderer.addClass(tip, 'text-xs');
    this.renderer.addClass(tip, 'px-2');
    this.renderer.addClass(tip, 'py-1');
    this.renderer.setStyle(tip, 'pointerEvents', 'none');
    tip.textContent = this.text || '';

    this.tooltipEl = tip;
    document.body.appendChild(tip);
    // set aria-describedby on host
    try { this.renderer.setAttribute(host, 'aria-describedby', id); } catch {}
    this.positionTooltip();
  }

  private destroyTooltip() {
    if (!this.tooltipEl) return;
    const host = this.host.nativeElement;
    try { this.renderer.removeAttribute(host, 'aria-describedby'); } catch {}
    try { document.body.removeChild(this.tooltipEl); } catch {}
    this.tooltipEl = null;
  }

  private cleanUp() {
    window.clearTimeout(this.showTimeout);
    window.clearTimeout(this.hideTimeout);
    this.destroyTooltip();
  }

  private positionTooltip() {
    if (!this.tooltipEl) return;
    const hostRect = this.host.nativeElement.getBoundingClientRect();
    const tipRect = this.tooltipEl.getBoundingClientRect();
    // prefer above the host, otherwise below
    const margin = 8;
    let top = hostRect.top - tipRect.height - margin;
    let left = hostRect.left + (hostRect.width - tipRect.width) / 2;

    // if not enough space above, place below
    if (top < 0) {
      top = hostRect.bottom + margin;
    }

    // keep within viewport horizontally
    const maxLeft = window.innerWidth - tipRect.width - 8;
    if (left < 8) left = 8;
    if (left > maxLeft) left = maxLeft;

    this.renderer.setStyle(this.tooltipEl, 'top', `${Math.max(8, top)}px`);
    this.renderer.setStyle(this.tooltipEl, 'left', `${left}px`);
  }
}
