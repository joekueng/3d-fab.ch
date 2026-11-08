import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  NgZone,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LanguageService } from '../../core/services/language.service';

type StartingPoint = 'file' | 'idea';
type Scene =
  | 'upload'
  | 'brief'
  | 'design'
  | 'quote'
  | 'print'
  | 'ship'
  | 'arrive';
interface ProcessStep {
  readonly scene: Scene;
  readonly key: string;
}

const FILE_STEPS: readonly ProcessStep[] = [
  { scene: 'upload', key: 'UPLOAD' },
  { scene: 'quote', key: 'QUOTE' },
  { scene: 'print', key: 'PRINT' },
  { scene: 'ship', key: 'SHIP' },
  { scene: 'arrive', key: 'ARRIVE' },
];
const IDEA_STEPS: readonly ProcessStep[] = [
  { scene: 'brief', key: 'BRIEF' },
  { scene: 'design', key: 'DESIGN' },
  { scene: 'quote', key: 'CUSTOM_QUOTE' },
  ...FILE_STEPS.slice(2),
];

@Component({
  selector: 'app-home-process',
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  templateUrl: './home-process.component.html',
  styleUrl: './home-process.component.scss',
})
export class HomeProcessComponent {
  readonly languageService = inject(LanguageService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly document = inject(DOCUMENT);
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private frame: number | null = null;
  private observer: ResizeObserver | null = null;
  private window: Window | null = null;

  readonly startingPoint = signal<StartingPoint>('file');
  readonly activeIndex = signal(0);
  readonly stepFraction = signal(0);
  readonly reducedMotion = signal(false);
  readonly steps = computed(() =>
    this.startingPoint() === 'file' ? FILE_STEPS : IDEA_STEPS,
  );
  readonly activeStep = computed(
    () => this.steps()[this.activeIndex()] ?? this.steps()[0],
  );
  readonly fraction = computed(() =>
    this.reducedMotion() ? 1 : this.stepFraction(),
  );
  readonly printTop = computed(() => 299 - this.fraction() * 204);
  readonly nozzleTransform = computed(
    () =>
      `translate(${280 + Math.sin(this.fraction() * Math.PI * 8) * 65} ${this.printTop() - 25})`,
  );
  readonly shippingTransform = computed(
    () => `translate(${30 + this.fraction() * 282} 148) scale(.42)`,
  );
  // Reveal the loose pencil contours in drawing order, driven by the same scroll fraction.
  readonly sketchStrokes: readonly string[] = [
    'M245 500 Q320 650 510 705 Q730 790 880 500',
    'M250 505 L375 465 L550 545 L885 435 Q920 450 875 500 L545 590 Z',
    'M410 505 L410 300 L635 355 L635 555',
    'M635 555 L782 470 L782 255 L635 355',
    'M415 300 L455 225 L780 255 L635 355 Z M455 225 L625 190 L780 255',
    'M515 245 L515 150 Q550 120 590 150 L590 245 M515 150 Q555 175 590 150',
    'M460 530 L460 410 Q480 320 525 385 L525 550',
    'M670 355 L740 320 L740 425 L670 470 Z',
    'M760 540 C715 520 725 480 765 490 C810 505 795 550 760 540',
    'M320 480 L310 425 Q335 395 350 425 L355 495 M250 505 Q390 540 545 590 L880 490',
    'M425 300 L620 347 L780 255 M414 310 L635 365 L785 270 M260 520 Q455 745 710 705',
  ];
  readonly sketchProgress = computed(() => Math.min(1, this.fraction() / 0.78));
  sketchStrokeOffset(index: number): number {
    return (
      1 -
      Math.max(
        0,
        Math.min(1, this.sketchProgress() * this.sketchStrokes.length - index),
      )
    );
  }

  readonly unboxOpen = computed(() => Math.min(1, this.fraction() / 0.4));
  readonly unboxLift = computed(() =>
    Math.max(0, Math.min(1, (this.fraction() - 0.28) / 0.72)),
  );
  readonly unboxTransform = computed(
    () => 'translate(0 ' + -142 * this.unboxLift() + ')',
  );
  readonly backLeftFlap = computed(() => {
    const p = this.unboxOpen();
    return (
      'M166 160 280 104 ' +
      (280 - 40 * p) +
      ' ' +
      (104 - 57 * p) +
      ' ' +
      (166 - 50 * p) +
      ' ' +
      (160 - 60 * p) +
      'Z'
    );
  });
  readonly backRightFlap = computed(() => {
    const p = this.unboxOpen();
    return (
      'M280 104 394 160 ' +
      (394 + 50 * p) +
      ' ' +
      (160 - 60 * p) +
      ' ' +
      (280 + 40 * p) +
      ' ' +
      (104 - 57 * p) +
      'Z'
    );
  });
  readonly frontLeftFlap = computed(() => {
    const p = this.unboxOpen();
    return (
      'M166 160 280 217 ' +
      (280 - 30 * p) +
      ' ' +
      (217 + 37 * p) +
      ' ' +
      (166 - 30 * p) +
      ' ' +
      (160 + 37 * p) +
      'Z'
    );
  });
  readonly frontRightFlap = computed(() => {
    const p = this.unboxOpen();
    return (
      'M280 217 394 160 ' +
      (394 + 30 * p) +
      ' ' +
      (160 + 37 * p) +
      ' ' +
      (280 + 30 * p) +
      ' ' +
      (217 + 37 * p) +
      'Z'
    );
  });
  readonly progress = computed(
    () => (this.activeIndex() + this.stepFraction()) / this.steps().length,
  );

  constructor() {
    afterNextRender(() => {
      if (!this.isBrowser) return;
      const view = this.document.defaultView;
      if (!view) return;
      this.window = view;
      const motion = view.matchMedia('(prefers-reduced-motion: reduce)');
      const updateMotion = () => this.reducedMotion.set(motion.matches);
      updateMotion();
      motion.addEventListener('change', updateMotion);
      this.zone.runOutsideAngular(() => {
        view.addEventListener('scroll', this.queueMeasurement, {
          passive: true,
        });
        view.addEventListener('resize', this.queueMeasurement, {
          passive: true,
        });
        if (typeof ResizeObserver !== 'undefined') {
          this.observer = new ResizeObserver(this.queueMeasurement);
          this.observer.observe(this.host.nativeElement);
        }
        this.queueMeasurement();
      });
      this.destroyRef.onDestroy(() => {
        view.removeEventListener('scroll', this.queueMeasurement);
        view.removeEventListener('resize', this.queueMeasurement);
        motion.removeEventListener('change', updateMotion);
        this.observer?.disconnect();
        if (this.frame !== null) view.cancelAnimationFrame(this.frame);
        this.window = null;
      });
    });
  }

  selectStartingPoint(value: StartingPoint): void {
    if (value === this.startingPoint()) return;
    this.startingPoint.set(value);
    this.activeIndex.set(0);
    this.stepFraction.set(0);
    this.queueMeasurement();
  }

  goToStep(index: number): void {
    const view = this.window;
    const step =
      this.host.nativeElement.querySelectorAll<HTMLElement>('.process-step')[
        index
      ];
    if (!view || !step) return;
    this.activeIndex.set(index);
    view.scrollTo({
      top:
        view.scrollY +
        step.getBoundingClientRect().top -
        this.readingLine(view) +
        2,
      behavior: this.reducedMotion() ? 'auto' : 'smooth',
    });
  }

  private readonly queueMeasurement = (): void => {
    const view = this.window;
    if (!view || this.frame !== null) return;
    this.frame = view.requestAnimationFrame(() => {
      this.frame = null;
      const rows =
        this.host.nativeElement.querySelectorAll<HTMLElement>('.process-step');
      const line = this.readingLine(view);
      let index = 0;
      let fraction = 0;
      rows.forEach((row, i) => {
        const rect = row.getBoundingClientRect();
        if (rect.top <= line) {
          index = i;
          fraction = Math.min(
            1,
            Math.max(0, (line - rect.top) / Math.max(1, rect.height * 0.85)),
          );
        }
      });
      const rounded = Math.round(fraction * 1000) / 1000;
      if (index !== this.activeIndex() || rounded !== this.stepFraction()) {
        this.zone.run(() => {
          this.activeIndex.set(index);
          this.stepFraction.set(rounded);
        });
      }
    });
  };

  private readingLine(view: Window): number {
    if (view.innerWidth <= 760 && view.innerHeight > 600) return 380;
    return Math.min(view.innerHeight * 0.5, 460);
  }
}
