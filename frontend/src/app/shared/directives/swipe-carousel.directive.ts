import { isPlatformBrowser } from '@angular/common';
import {
  booleanAttribute,
  Directive,
  ElementRef,
  EventEmitter,
  HostBinding,
  HostListener,
  Input,
  OnDestroy,
  Output,
  PLATFORM_ID,
  inject,
  numberAttribute,
} from '@angular/core';

@Directive({
  selector: '[appSwipeCarousel]',
  standalone: true,
})
export class SwipeCarouselDirective implements OnDestroy {
  private readonly elementRef = inject(ElementRef<HTMLElement>);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private pointerId: number | null = null;
  private startX = 0;
  private startY = 0;
  private horizontalDrag = false;
  private verticalScroll = false;
  private suppressNextClick = false;
  private clickSuppressionTimer: number | null = null;
  private readonly clickCaptureListener = (event: MouseEvent) => {
    if (!this.suppressNextClick) {
      return;
    }
    event.preventDefault();
    event.stopImmediatePropagation();
    this.suppressNextClick = false;
  };

  constructor() {
    if (!this.isBrowser) {
      return;
    }
    this.elementRef.nativeElement.addEventListener(
      'click',
      this.clickCaptureListener,
      true,
    );
  }

  @Input({ alias: 'appSwipeCarousel', transform: booleanAttribute })
  enabled = true;

  @Input({ transform: numberAttribute }) swipeCarouselThreshold = 42;

  @Output() readonly swipeCarouselPrevious = new EventEmitter<void>();
  @Output() readonly swipeCarouselNext = new EventEmitter<void>();

  @HostBinding('class.ui-swipe-carousel')
  get swipeCarouselClass(): boolean {
    return this.enabled;
  }

  @HostBinding('class.is-swipe-dragging') dragging = false;

  @HostListener('pointerdown', ['$event'])
  onPointerDown(event: PointerEvent): void {
    if (
      !this.enabled ||
      !this.isBrowser ||
      !event.isPrimary ||
      this.isInteractiveTarget(event.target) ||
      (event.pointerType === 'mouse' && event.button !== 0)
    ) {
      return;
    }

    this.pointerId = event.pointerId;
    this.startX = event.clientX;
    this.startY = event.clientY;
    this.horizontalDrag = false;
    this.verticalScroll = false;
    this.dragging = false;

    try {
      this.elementRef.nativeElement.setPointerCapture(event.pointerId);
    } catch {
      // Some browser/element combinations do not support pointer capture.
    }
  }

  @HostListener('pointermove', ['$event'])
  onPointerMove(event: PointerEvent): void {
    if (event.pointerId !== this.pointerId) {
      return;
    }

    const deltaX = event.clientX - this.startX;
    const deltaY = event.clientY - this.startY;
    const absX = Math.abs(deltaX);
    const absY = Math.abs(deltaY);

    if (!this.horizontalDrag && !this.verticalScroll) {
      if (absX < 8 && absY < 8) {
        return;
      }
      if (absY > absX * 1.15) {
        this.verticalScroll = true;
        this.releasePointer(event.pointerId);
        this.resetGesture();
        return;
      }
      if (absX > absY * 1.15) {
        this.horizontalDrag = true;
        this.dragging = true;
      }
    }

    if (this.horizontalDrag) {
      event.preventDefault();
    }
  }

  @HostListener('pointerup', ['$event'])
  onPointerUp(event: PointerEvent): void {
    this.finishGesture(event);
  }

  @HostListener('pointercancel', ['$event'])
  onPointerCancel(event: PointerEvent): void {
    this.finishGesture(event, true);
  }

  ngOnDestroy(): void {
    if (this.isBrowser) {
      this.elementRef.nativeElement.removeEventListener(
        'click',
        this.clickCaptureListener,
        true,
      );
    }
    if (this.clickSuppressionTimer !== null) {
      window.clearTimeout(this.clickSuppressionTimer);
      this.clickSuppressionTimer = null;
    }
  }

  private finishGesture(event: PointerEvent, cancelled = false): void {
    if (event.pointerId !== this.pointerId) {
      return;
    }

    const deltaX = event.clientX - this.startX;
    const deltaY = event.clientY - this.startY;
    const absX = Math.abs(deltaX);
    const absY = Math.abs(deltaY);
    const didSwipe =
      !cancelled &&
      this.horizontalDrag &&
      absX >= this.swipeCarouselThreshold &&
      absX > absY * 1.15;

    if (didSwipe) {
      event.preventDefault();
      this.suppressUpcomingClick();
      if (deltaX < 0) {
        this.swipeCarouselNext.emit();
      } else {
        this.swipeCarouselPrevious.emit();
      }
    }

    this.releasePointer(event.pointerId);
    this.resetGesture();
  }

  private suppressUpcomingClick(): void {
    if (!this.isBrowser) {
      return;
    }
    this.suppressNextClick = true;
    if (this.clickSuppressionTimer !== null) {
      window.clearTimeout(this.clickSuppressionTimer);
    }
    this.clickSuppressionTimer = window.setTimeout(() => {
      this.suppressNextClick = false;
      this.clickSuppressionTimer = null;
    }, 160);
  }

  private releasePointer(pointerId: number): void {
    try {
      if (this.elementRef.nativeElement.hasPointerCapture(pointerId)) {
        this.elementRef.nativeElement.releasePointerCapture(pointerId);
      }
    } catch {
      // Ignore pointer-capture cleanup failures during cancelled gestures.
    }
  }

  private resetGesture(): void {
    this.pointerId = null;
    this.horizontalDrag = false;
    this.verticalScroll = false;
    this.dragging = false;
  }

  private isInteractiveTarget(target: EventTarget | null): boolean {
    return (
      target instanceof Element &&
      target.closest(
        'a,button,input,select,textarea,label,[role="button"],[role="link"]',
      ) !== null
    );
  }
}
