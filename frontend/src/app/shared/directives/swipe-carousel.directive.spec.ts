import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SwipeCarouselDirective } from './swipe-carousel.directive';

@Component({
  standalone: true,
  imports: [SwipeCarouselDirective],
  template: `
    <div
      class="carousel"
      [appSwipeCarousel]="true"
      (swipeCarouselNext)="nextCount = nextCount + 1"
      (swipeCarouselPrevious)="previousCount = previousCount + 1"
    >
      <button type="button" class="carousel-button">Toggle</button>
      <span class="carousel-surface">Surface</span>
    </div>
  `,
})
class SwipeCarouselHostComponent {
  nextCount = 0;
  previousCount = 0;
}

describe('SwipeCarouselDirective', () => {
  function dispatchPointer(
    target: Element,
    type: string,
    clientX: number,
    clientY = 0,
  ): void {
    target.dispatchEvent(
      new PointerEvent(type, {
        bubbles: true,
        button: 0,
        clientX,
        clientY,
        isPrimary: true,
        pointerId: 1,
        pointerType: 'mouse',
      }),
    );
  }

  it('keeps buttons inside a carousel clickable instead of treating them as swipe handles', () => {
    const fixture = TestBed.createComponent(SwipeCarouselHostComponent);
    fixture.detectChanges();
    const host = fixture.componentInstance;
    const button = fixture.nativeElement.querySelector(
      '.carousel-button',
    ) as HTMLButtonElement;
    const carousel = fixture.nativeElement.querySelector(
      '.carousel',
    ) as HTMLElement;

    dispatchPointer(button, 'pointerdown', 120);
    dispatchPointer(carousel, 'pointermove', 40);
    dispatchPointer(carousel, 'pointerup', 40);

    expect(host.nextCount).toBe(0);
    expect(host.previousCount).toBe(0);
    expect(carousel.classList.contains('is-swipe-dragging')).toBeFalse();
  });

  it('still emits swipe events when the gesture starts on carousel content', () => {
    const fixture = TestBed.createComponent(SwipeCarouselHostComponent);
    fixture.detectChanges();
    const host = fixture.componentInstance;
    const surface = fixture.nativeElement.querySelector(
      '.carousel-surface',
    ) as HTMLElement;
    const carousel = fixture.nativeElement.querySelector(
      '.carousel',
    ) as HTMLElement;

    dispatchPointer(surface, 'pointerdown', 120);
    dispatchPointer(carousel, 'pointermove', 40);
    dispatchPointer(carousel, 'pointerup', 40);

    expect(host.nextCount).toBe(1);
    expect(host.previousCount).toBe(0);
  });
});
