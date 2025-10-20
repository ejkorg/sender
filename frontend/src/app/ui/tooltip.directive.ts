import { Directive, ElementRef, HostListener, Input, Renderer2, OnDestroy, AfterViewInit, TemplateRef, EmbeddedViewRef, ApplicationRef, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[appTooltip]',
  standalone: true
})
export class TooltipDirective implements AfterViewInit, OnDestroy {
  // content can be a simple string or a TemplateRef for richer content
  @Input('appTooltip') content: string | TemplateRef<any> | null = null;
  // placement preference: top, bottom, left, right, auto
  @Input() tooltipPlacement: 'top' | 'bottom' | 'left' | 'right' | 'auto' = 'auto';
  // show/hide delays in ms
  @Input() tooltipShowDelay = 150;
  @Input() tooltipHideDelay = 50;
  // optional CSS class to add to the tooltip container
  @Input() tooltipClass = '';
  // if true, will add tabindex=0 to host when host isn't focusable
  @Input() tooltipFocusable = false;

  private tooltipEl: HTMLElement | null = null;
  private embeddedView: EmbeddedViewRef<any> | null = null;
  private appRef: ApplicationRef | null = null;
  private viewContainerRef: ViewContainerRef | null = null;
  private showTimeout = 0;
  private hideTimeout = 0;
  private resizeObserver: ResizeObserver | null = null;
  private scrollHandler?: () => void;
  private resizeHandler?: () => void;
  private touchStartHandler?: (e: TouchEvent) => void;
  private touchEndHandler?: (e: TouchEvent) => void;

  constructor(private host: ElementRef<HTMLElement>, private renderer: Renderer2, appRef: ApplicationRef, vcr: ViewContainerRef) {
    this.appRef = appRef;
    this.viewContainerRef = vcr;
  }

  ngAfterViewInit(): void {
    // ensure host is focusable for keyboard users if it isn't already
    const el = this.host.nativeElement;
    const tabIndex = el.getAttribute('tabindex');
    if (this.tooltipFocusable && !tabIndex && el.tabIndex === -1) {
      // opt-in behavior: only set tabindex when explicitly requested
      this.renderer.setAttribute(el, 'tabindex', '0');
    }

    // observe size changes to reposition tooltip if needed
    try {
      this.resizeObserver = new ResizeObserver(() => this.positionTooltip());
      this.resizeObserver.observe(el);
    } catch {}

    // listen to global scroll/resize to reposition while visible
    this.scrollHandler = this.throttle(() => this.positionTooltip(), 100);
    this.resizeHandler = this.throttle(() => this.positionTooltip(), 100);
    window.addEventListener('scroll', this.scrollHandler, true);
    window.addEventListener('resize', this.resizeHandler);

    // touch support: show on long-press
    this.touchStartHandler = (e: TouchEvent) => {
      // show only on larger viewports to avoid interfering with native mobile
      if (window.innerWidth < 480) return;
      window.clearTimeout(this.hideTimeout);
      this.showTimeout = window.setTimeout(() => this.createTooltip(), 400);
    };
    this.touchEndHandler = (_e: TouchEvent) => {
      window.clearTimeout(this.showTimeout);
      this.hideTimeout = window.setTimeout(() => this.destroyTooltip(), 300);
    };
    el.addEventListener('touchstart', this.touchStartHandler, { passive: true });
    el.addEventListener('touchend', this.touchEndHandler);
  }

  ngOnDestroy(): void {
    this.cleanUp();
    try {
      const el = this.host.nativeElement;
      if (this.touchStartHandler) el.removeEventListener('touchstart', this.touchStartHandler);
      if (this.touchEndHandler) el.removeEventListener('touchend', this.touchEndHandler);
      if (this.scrollHandler) window.removeEventListener('scroll', this.scrollHandler, true);
      if (this.resizeHandler) window.removeEventListener('resize', this.resizeHandler);
      if (this.resizeObserver) { try { this.resizeObserver.disconnect(); } catch {} }
    } catch {}
  }

  @HostListener('mouseenter')
  @HostListener('focus')
  onShow() {
    window.clearTimeout(this.hideTimeout);
    this.showTimeout = window.setTimeout(() => this.createTooltip(), this.tooltipShowDelay);
  }

  @HostListener('mouseleave')
  @HostListener('blur')
  onHide() {
    window.clearTimeout(this.showTimeout);
    this.hideTimeout = window.setTimeout(() => this.destroyTooltip(), this.tooltipHideDelay);
  }

  private createTooltip() {
    if (this.tooltipEl) return;
    const host = this.host.nativeElement;
    const id = `tooltip-${Math.floor(Math.random() * 1e9)}`;
    const tip = this.renderer.createElement('div') as HTMLElement;
    this.renderer.setAttribute(tip, 'role', 'tooltip');
    this.renderer.setAttribute(tip, 'id', id);
    this.renderer.setStyle(tip, 'position', 'absolute');
    this.renderer.setStyle(tip, 'pointerEvents', 'none');
    // basic styling; allow custom class
    const baseClasses = ['z-50', 'whitespace-nowrap', 'rounded-md', 'bg-slate-800', 'text-white', 'text-xs', 'px-2', 'py-1', 'transition', 'opacity-0'];
    for (const c of baseClasses) this.renderer.addClass(tip, c);
    if (this.tooltipClass) {
      this.tooltipClass.split(' ').forEach(cl => { if (cl) this.renderer.addClass(tip, cl); });
    }

    // if content is a TemplateRef, render it into the tooltip element
    if (this.content instanceof TemplateRef) {
      try {
        const view = this.content.createEmbeddedView({});
        this.appRef?.attachView(view);
        this.embeddedView = view;
        // append all root nodes of the view into tip
        for (const node of view.rootNodes) {
          try { tip.appendChild(node); } catch {}
        }
      } catch (e) {
        // fallback to empty
      }
    } else {
      tip.textContent = String(this.content ?? '');
    }

    this.tooltipEl = tip;
    document.body.appendChild(tip);
    // set aria-describedby on host
    try { this.renderer.setAttribute(host, 'aria-describedby', id); } catch {}
    // allow the browser a tick to measure and then position + show
    setTimeout(() => {
      this.positionTooltip();
      // fade in
      try { this.renderer.setStyle(this.tooltipEl, 'opacity', '1'); } catch {}
    }, 0);
  }

  private destroyTooltip() {
    if (!this.tooltipEl) return;
    const host = this.host.nativeElement;
    try { this.renderer.removeAttribute(host, 'aria-describedby'); } catch {}
    // fade out then remove
    try { this.renderer.setStyle(this.tooltipEl, 'opacity', '0'); } catch {}
    setTimeout(() => {
      try { if (this.embeddedView) { this.appRef?.detachView(this.embeddedView); this.embeddedView = null; } } catch {}
      try { if (this.tooltipEl && this.tooltipEl.parentNode) this.tooltipEl.parentNode.removeChild(this.tooltipEl); } catch {}
      this.tooltipEl = null;
    }, 180);
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
    const margin = 8;
    const placements: Array<'top' | 'bottom' | 'left' | 'right'> = this.tooltipPlacement === 'auto' ? ['top', 'bottom', 'right', 'left'] : [this.tooltipPlacement as any];
    let chosen: 'top' | 'bottom' | 'left' | 'right' = 'top';
    let top = 0; let left = 0;
    for (const p of placements) {
      if (p === 'top') {
        const t = hostRect.top - tipRect.height - margin;
        const l = hostRect.left + (hostRect.width - tipRect.width) / 2;
        if (t >= 0) { chosen = 'top'; top = t; left = l; break; }
      }
      if (p === 'bottom') {
        const t = hostRect.bottom + margin;
        const l = hostRect.left + (hostRect.width - tipRect.width) / 2;
        if (t + tipRect.height <= window.innerHeight) { chosen = 'bottom'; top = t; left = l; break; }
      }
      if (p === 'right') {
        const t = hostRect.top + (hostRect.height - tipRect.height) / 2;
        const l = hostRect.right + margin;
        if (l + tipRect.width <= window.innerWidth) { chosen = 'right'; top = t; left = l; break; }
      }
      if (p === 'left') {
        const t = hostRect.top + (hostRect.height - tipRect.height) / 2;
        const l = hostRect.left - tipRect.width - margin;
        if (l >= 0) { chosen = 'left'; top = t; left = l; break; }
      }
    }

    // fallback: center above
    if (!top && !left) {
      top = Math.max(8, hostRect.top - tipRect.height - margin);
      left = hostRect.left + (hostRect.width - tipRect.width) / 2;
    }

    // constrain horizontally
    const maxLeft = window.innerWidth - tipRect.width - 8;
    if (left < 8) left = 8;
    if (left > maxLeft) left = maxLeft;
    if (top < 8) top = 8;

    this.renderer.setStyle(this.tooltipEl, 'top', `${Math.round(top)}px`);
    this.renderer.setStyle(this.tooltipEl, 'left', `${Math.round(left)}px`);
  }

  // simple throttle helper
  private throttle(fn: () => void, wait: number) {
    let last = 0;
    let timer: any = null;
    return () => {
      const now = Date.now();
      if (last && now < last + wait) {
        clearTimeout(timer);
        timer = setTimeout(() => { last = now; fn(); }, wait - (now - last));
      } else {
        last = now;
        fn();
      }
    };
  }
}
