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
  private documentPointerDown?: (e: Event) => void;
  private lastInteractionWasPointer = false;
  private globalPointerDown?: (e: Event) => void;
  private globalKeyDown?: (e: KeyboardEvent) => void;
  private lastPointerX = 0;
  private lastPointerY = 0;
  private pointerMoveHandler?: (e: PointerEvent) => void;
  private tooltipInstanceId = `t-${Math.floor(Math.random() * 1e9)}`;

  // use a global hover registry so we can know when no hosts are hovered
  // and hide the shared tooltip. Stored on the window to keep it global
  // across directive instances.
  private get hoverSet(): Set<string> {
    const w = window as any;
    if (!w.__appTooltipHoverSet) w.__appTooltipHoverSet = new Set<string>();
    return w.__appTooltipHoverSet as Set<string>;
  }

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

    // track last interaction type so focus events caused by pointer
    // clicks do not open the tooltip. Keyboard users still get tooltips
    // via focus.
    this.globalPointerDown = () => { this.lastInteractionWasPointer = true; };
    this.globalKeyDown = () => { this.lastInteractionWasPointer = false; };
    window.addEventListener('pointerdown', this.globalPointerDown, true);
    window.addEventListener('keydown', this.globalKeyDown, true);

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

    // pointer events: prefer pointerenter/pointerleave so hovering over
    // the small bar segments reliably triggers the tooltip. We keep
    // mouseenter/mouseleave decorators for keyboard focus handling.
    const pointerEnter = (e: PointerEvent) => {
      // only respond to non-touch pointers here; touch is handled above
      if ((e as any).pointerType === 'touch') return;
      // mark ourselves as an active hover owner
      try { this.hoverSet.add(this.tooltipInstanceId); } catch {}
      this.lastPointerX = e.clientX;
      this.lastPointerY = e.clientY;
      window.clearTimeout(this.hideTimeout);
      this.showTimeout = window.setTimeout(() => this.createTooltip(), this.tooltipShowDelay);
    };
    const pointerLeave = (_e: PointerEvent) => {
      // pointerleave may not include coordinates; keep lastPointerX/Y from enter/move
      try { this.hoverSet.delete(this.tooltipInstanceId); } catch {}
      window.clearTimeout(this.showTimeout);
      // only destroy tooltip when there are no remaining hoverers
      const hideIfEmpty = () => {
        try {
          if ((this.hoverSet && this.hoverSet.size === 0)) {
            this.destroyTooltip();
          }
        } catch { this.destroyTooltip(); }
      };
      this.hideTimeout = window.setTimeout(hideIfEmpty, this.tooltipHideDelay);
    };
    // keep pointermove so we have more accurate coordinates when the user moves
    this.pointerMoveHandler = (e: PointerEvent) => {
      if ((e as any).pointerType === 'touch') return;
      this.lastPointerX = e.clientX;
      this.lastPointerY = e.clientY;
    };
    el.addEventListener('pointerenter', pointerEnter as EventListener);
    el.addEventListener('pointerleave', pointerLeave as EventListener);
    el.addEventListener('pointermove', this.pointerMoveHandler as EventListener);
    // store small closures so we can remove them on destroy
    (this as any)._pointerEnter = pointerEnter;
    (this as any)._pointerLeave = pointerLeave;
  }

  ngOnDestroy(): void {
    this.cleanUp();
    try {
      const el = this.host.nativeElement;
      if (this.touchStartHandler) el.removeEventListener('touchstart', this.touchStartHandler);
      if (this.touchEndHandler) el.removeEventListener('touchend', this.touchEndHandler);
      if ((this as any)._pointerEnter) el.removeEventListener('pointerenter', (this as any)._pointerEnter);
      if ((this as any)._pointerLeave) el.removeEventListener('pointerleave', (this as any)._pointerLeave);
      if (this.pointerMoveHandler) el.removeEventListener('pointermove', this.pointerMoveHandler as EventListener);
      if (this.scrollHandler) window.removeEventListener('scroll', this.scrollHandler, true);
      if (this.resizeHandler) window.removeEventListener('resize', this.resizeHandler);
      if (this.resizeObserver) { try { this.resizeObserver.disconnect(); } catch {} }
      if (this.globalPointerDown) window.removeEventListener('pointerdown', this.globalPointerDown, true);
      if (this.globalKeyDown) window.removeEventListener('keydown', this.globalKeyDown, true);
    } catch {}
  }

  @HostListener('mouseenter')
  @HostListener('focus')
  onShow() {
    // If the last interaction was a pointer, we avoid opening the tooltip
    // on focus events caused by clicking — pointerenter/leave already
    // handle hover-based showing. This keeps keyboard focus behavior intact.
    if (this.lastInteractionWasPointer) return;
    // mark keyboard focus as a hoverer so blur will clear appropriately
    try { this.hoverSet.add(this.tooltipInstanceId); } catch {}
    window.clearTimeout(this.hideTimeout);
    this.showTimeout = window.setTimeout(() => this.createTooltip(), this.tooltipShowDelay);
  }

  @HostListener('mouseleave')
  @HostListener('blur')
  onHide() {
    // remove from hover set and only destroy when the set is empty
    try { this.hoverSet.delete(this.tooltipInstanceId); } catch {}
    window.clearTimeout(this.showTimeout);
    const hideIfEmpty = () => {
      try {
        if ((this.hoverSet && this.hoverSet.size === 0)) {
          this.destroyTooltip();
        }
      } catch { this.destroyTooltip(); }
    };
    this.hideTimeout = window.setTimeout(hideIfEmpty, this.tooltipHideDelay);
  }

  private createTooltip() {
    // Use a single global tooltip element to avoid many overlapping tooltips
    // when multiple hosts are hovered in the same area. Reuse the element
    // for all instances and track ownership via dataset.owner.
    const host = this.host.nativeElement;
    const globalId = 'app-tooltip-global';
    let tip = document.getElementById(globalId) as HTMLElement | null;
    if (!tip) {
      tip = this.renderer.createElement('div') as HTMLElement;
      this.renderer.setAttribute(tip, 'id', globalId);
      this.renderer.setAttribute(tip, 'role', 'tooltip');
      this.renderer.setStyle(tip, 'position', 'fixed');
      this.renderer.setStyle(tip, 'pointerEvents', 'none');
      const baseClasses = ['z-50', 'whitespace-nowrap', 'rounded-md', 'bg-slate-800', 'text-white', 'text-xs', 'px-2', 'py-1', 'transition', 'opacity-0'];
      for (const c of baseClasses) this.renderer.addClass(tip, c);
      document.body.appendChild(tip);
    }

    // replace content of global tip with ours
    // remove previous children (if any)
    while (tip.firstChild) tip.removeChild(tip.firstChild);
    if (this.content instanceof TemplateRef) {
      try {
        const view = this.content.createEmbeddedView({});
        this.appRef?.attachView(view);
        this.embeddedView = view;
        for (const node of view.rootNodes) {
          try { tip.appendChild(node); } catch {}
        }
      } catch (e) {}
    } else {
      tip.textContent = String(this.content ?? '');
    }

    this.tooltipEl = tip;
    // mark ownership so multiple instances don't remove each other's tooltip
    this.tooltipEl.dataset['owner'] = this.tooltipInstanceId;
    // hide tooltip if the user clicks/touches anywhere (avoids stuck tooltips when host triggers a dialog or navigation)
    this.documentPointerDown = (_e: Event) => this.destroyTooltip();
    document.addEventListener('pointerdown', this.documentPointerDown, true);
    // set aria-describedby on host (global id)
    try { this.renderer.setAttribute(host, 'aria-describedby', globalId); } catch {}
    // allow the browser a frame to render the new element, then measure
    // using requestAnimationFrame which is more reliable than setTimeout(,0)
    requestAnimationFrame(() => {
      // position may need host measurements; guard against degenerate rects
      // and retry once if we get a zero-sized rect (can happen for elements
      // that are temporarily display:none or in a collapsed container).
      this.positionTooltip();
      const hostRect = this.host.nativeElement.getBoundingClientRect();
      if (hostRect.width === 0 && hostRect.height === 0) {
        // try one more frame to allow layout to stabilize
        requestAnimationFrame(() => this.positionTooltip());
      }
      // fade in
      try { this.renderer.setStyle(this.tooltipEl, 'opacity', '1'); } catch {}
    });
  }

  private destroyTooltip() {
    if (!this.tooltipEl) return;
    const host = this.host.nativeElement;
    // only the owner should remove/hide the global tooltip; if another
    // instance currently owns it, don't touch it.
    const owner = this.tooltipEl.dataset['owner'];
    if (owner !== this.tooltipInstanceId) {
      // remove our aria-describedby if we had set it previously
      try { this.renderer.removeAttribute(host, 'aria-describedby'); } catch {}
      return;
    }

    try { this.renderer.removeAttribute(host, 'aria-describedby'); } catch {}
    // fade out then remove ownership
    try { this.renderer.setStyle(this.tooltipEl, 'opacity', '0'); } catch {}
    try {
      if (this.documentPointerDown) {
        document.removeEventListener('pointerdown', this.documentPointerDown, true);
        this.documentPointerDown = undefined;
      }
    } catch {}

    setTimeout(() => {
      try { if (this.embeddedView) { this.appRef?.detachView(this.embeddedView); this.embeddedView = null; } } catch {}
      try {
        // clear content but keep the global element in DOM for reuse
        if (this.tooltipEl) {
          while (this.tooltipEl.firstChild) this.tooltipEl.removeChild(this.tooltipEl.firstChild);
          delete this.tooltipEl.dataset['owner'];
        }
      } catch {}
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
    let hostRect = this.host.nativeElement.getBoundingClientRect();
    const tipRect = this.tooltipEl.getBoundingClientRect();
    const margin = 8;
    const placements: Array<'top' | 'bottom' | 'left' | 'right'> = this.tooltipPlacement === 'auto' ? ['top', 'bottom', 'right', 'left'] : [this.tooltipPlacement as any];
    let chosen: 'top' | 'bottom' | 'left' | 'right' = 'top';
    let top = 0; let left = 0;
    let placed = false; // true when we pick a placement
    for (const p of placements) {
      if (p === 'top') {
        const t = hostRect.top - tipRect.height - margin;
        const l = hostRect.left + (hostRect.width - tipRect.width) / 2;
        if (t >= 0) { chosen = 'top'; top = t; left = l; placed = true; break; }
      }
      if (p === 'bottom') {
        const t = hostRect.bottom + margin;
        const l = hostRect.left + (hostRect.width - tipRect.width) / 2;
        if (t + tipRect.height <= window.innerHeight) { chosen = 'bottom'; top = t; left = l; placed = true; break; }
      }
      if (p === 'right') {
        const t = hostRect.top + (hostRect.height - tipRect.height) / 2;
        const l = hostRect.right + margin;
        if (l + tipRect.width <= window.innerWidth) { chosen = 'right'; top = t; left = l; placed = true; break; }
      }
      if (p === 'left') {
        const t = hostRect.top + (hostRect.height - tipRect.height) / 2;
        const l = hostRect.left - tipRect.width - margin;
        if (l >= 0) { chosen = 'left'; top = t; left = l; placed = true; break; }
      }
    }

    // fallback: center above only if no placement was chosen
    if (!placed) {
      top = Math.max(8, hostRect.top - tipRect.height - margin);
      left = hostRect.left + (hostRect.width - tipRect.width) / 2;
    }

    // constrain horizontally
    const maxLeft = window.innerWidth - tipRect.width - 8;
    if (left < 8) left = 8;
    if (left > maxLeft) left = maxLeft;
    if (top < 8) top = 8;

    // If the tooltip is positioned absolute (not fixed), its coordinates must
    // be relative to the document, not the viewport. In that case add the
    // current scroll offsets. If it's fixed, getBoundingClientRect already
    // provides viewport coordinates which are correct for 'fixed'.
    try {
      const computed = window.getComputedStyle(this.tooltipEl);
      if (computed && computed.position === 'absolute') {
        top = top + (window.scrollY || 0);
        left = left + (window.scrollX || 0);
      }
    } catch (e) {
      // ignore errors reading computed style
    }

    // Defensive: if hostRect appears degenerate (0x0), try to replace it
    // with a non-zero ancestor rect so the tooltip doesn't collapse to the
    // top-left of the page in some browser/layout combos.
    if ((hostRect.width === 0 && hostRect.height === 0) && this.host && this.host.nativeElement) {
      let node: HTMLElement | null = this.host.nativeElement as HTMLElement;
      while (node && node !== document.body) {
        const r = node.getBoundingClientRect();
        if (r.width > 0 || r.height > 0) { hostRect = r; break; }
        node = node.parentElement;
      }
      // recompute reasonable fallback positions based on possibly-updated hostRect
      if (!placed) {
        top = Math.max(8, hostRect.top - tipRect.height - margin);
        left = hostRect.left + (hostRect.width - tipRect.width) / 2;
      }
      // If still degenerate, fall back to last pointer position (hover/click)
      if (hostRect.width === 0 && hostRect.height === 0) {
        // place the tooltip slightly offset from the pointer so it doesn't
        // sit directly under the cursor
        top = Math.max(8, this.lastPointerY + 12);
        left = Math.max(8, this.lastPointerX + 12);
      }
    }

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
