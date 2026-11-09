import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { LanguageService } from '../../core/services/language.service';
import itTranslations from '../../../assets/i18n/it.json';
import enTranslations from '../../../assets/i18n/en.json';
import { HomeProcessComponent } from './home-process.component';

describe('HomeProcessComponent', () => {
  let fixture: ComponentFixture<HomeProcessComponent>;
  let component: HomeProcessComponent;
  let host: HTMLElement;
  let translate: TranslateService;

  const nextFrame = () =>
    new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));

  beforeEach(async () => {
    const nativeMatchMedia = window.matchMedia.bind(window);
    spyOn(window, 'matchMedia').and.callFake((query) => {
      const media = nativeMatchMedia(query);
      if (query === '(max-width: 960px)') {
        spyOnProperty(media, 'matches').and.returnValue(false);
      }
      return media;
    });
    await TestBed.configureTestingModule({
      imports: [HomeProcessComponent],
      providers: [
        provideRouter([]),
        provideTranslateService(),
        {
          provide: LanguageService,
          useValue: { localizedPath: (value: string) => '/it' + value },
        },
      ],
    }).compileComponents();
    translate = TestBed.inject(TranslateService);
    translate.setFallbackLang('it');
    translate.setTranslation('it', itTranslations);
    translate.setTranslation('en', enTranslations);
    await firstValueFrom(translate.use('it'));
    // Browser checks exercise real layout resizing. Keep asynchronous resize
    // notifications from overriding explicitly scrubbed unit-test frames.
    spyOn(window, 'ResizeObserver').and.returnValue(
      jasmine.createSpyObj<ResizeObserver>('ResizeObserver', [
        'observe',
        'unobserve',
        'disconnect',
      ]),
    );
    fixture = TestBed.createComponent(HomeProcessComponent);
    component = fixture.componentInstance;
    host = fixture.nativeElement as HTMLElement;
    fixture.autoDetectChanges();
    await fixture.whenStable();
    // Keep the unit fixture beside the text, independently of Karma's iframe size.
    // The mobile cards are also covered by the browser regression check.
    const panel = host.querySelector<HTMLElement>('.process-visual')!;
    const grid = host.querySelector<HTMLElement>('.process-grid')!;
    panel.style.position = 'sticky';
    panel.style.top = '110px';
    grid.style.display = 'grid';
    // Geometry-driven unit cases model the ending track using the mocked last row.
    const ending = host.querySelector<HTMLElement>(
      '.process-step-track.is-ending',
    )!;
    spyOn(ending, 'getBoundingClientRect').and.callFake(() => {
      const rows = host.querySelectorAll<HTMLElement>('.process-step');
      return rows[rows.length - 1].getBoundingClientRect();
    });
    await nextFrame();
    await nextFrame();
    await fixture.whenStable();
  });

  it('changes the complete journey and its destination when an idea is selected', async () => {
    expect(host.querySelectorAll('.process-step').length).toBe(5);
    expect(host.querySelector('.process-finish a')?.getAttribute('href')).toBe(
      '/it/calculator/basic',
    );
    const options = host.querySelectorAll<HTMLButtonElement>('.path-option');
    options[1].click();
    await fixture.whenStable();
    expect(options[1].getAttribute('aria-pressed')).toBe('true');
    expect(host.querySelector('.path-summary')).toBeNull();
    expect(
      host.querySelector('.path-picker')?.hasAttribute('aria-describedby'),
    ).toBeFalse();
    expect(options[0].classList.contains('is-selected')).toBeFalse();
    expect(options[1].classList.contains('is-selected')).toBeTrue();
    expect(options[0].getAttribute('aria-pressed')).toBe('false');
    expect(host.querySelectorAll('.process-step').length).toBe(6);
    expect(host.querySelector('.process-step')?.textContent).toContain(
      'La tua idea',
    );
    expect(host.querySelector('.process-finish a')?.getAttribute('href')).toBe(
      '/it/contact',
    );
    options[0].click();
    await fixture.whenStable();
    expect(host.querySelectorAll('.process-step').length).toBe(5);
    expect(host.querySelector('.process-finish a')?.getAttribute('href')).toBe(
      '/it/calculator/basic',
    );
  });

  it('keeps the first animation and progress empty while the diagram approaches its sticky position', async () => {
    const panel = host.querySelector<HTMLElement>('.process-visual')!;
    const top = Number.parseFloat(getComputedStyle(panel).top);
    const rows = host.querySelectorAll<HTMLElement>('.process-step');
    let distance = 30;
    const grid = host.querySelector<HTMLElement>('.process-grid')!;
    spyOn(grid, 'getBoundingClientRect').and.callFake(
      () => new DOMRect(0, top + distance, 800, 2400),
    );
    const story = host.querySelector<HTMLElement>('.process-story')!;
    spyOn(story, 'getBoundingClientRect').and.callFake(
      () =>
        new DOMRect(
          0,
          top +
            distance +
            Math.max(0, Math.min(-distance, component.initialHold())),
          400,
          2400,
        ),
    );
    rows.forEach((row, index) =>
      spyOn(row, 'getBoundingClientRect').and.callFake(
        () =>
          new DOMRect(
            0,
            top + distance + index * 400 + component.firstTextOffset(),
            400,
            400,
          ),
      ),
    );
    window.dispatchEvent(new Event('scroll'));
    await nextFrame();
    await fixture.whenStable();
    expect(component.activeIndex()).toBe(0);
    expect(component.stepFraction()).toBe(0);
    expect(component.progress()).toBe(0);
    distance = 0;
    window.dispatchEvent(new Event('scroll'));
    await nextFrame();
    await fixture.whenStable();
    expect(component.stepFraction()).toBe(0);
    distance = -170;
    window.dispatchEvent(new Event('scroll'));
    await nextFrame();
    await fixture.whenStable();
    expect(component.activeIndex()).toBe(0);
    expect(component.stepFraction()).toBeCloseTo(0.5, 2);
    expect(component.firstTextOffset()).toBe(
      Math.min(170, component.initialHold()),
    );
    distance = -402;
    window.dispatchEvent(new Event('scroll'));
    await nextFrame();
    await fixture.whenStable();
    expect(component.activeIndex()).toBe(1);
    distance = 30;
    window.dispatchEvent(new Event('scroll'));
    await nextFrame();
    await fixture.whenStable();
    expect(component.progress()).toBe(0);
  });

  it('gives the ending text and diagram a common bottom edge and clears the holds on narrow layouts', async () => {
    const panel = host.querySelector<HTMLElement>('.process-visual')!;
    spyOnProperty(panel, 'offsetHeight', 'get').and.returnValue(580);
    window.dispatchEvent(new Event('resize'));
    await nextFrame();
    await fixture.whenStable();
    expect(component.finalTextTop() + component.finalBoxHeight()).toBe(690);
    expect(component.endingTrackHeight()).toBeGreaterThan(
      component.finalBoxHeight(),
    );
    const grid = host.querySelector<HTMLElement>('.process-grid')!;
    grid.style.display = 'block';
    window.dispatchEvent(new Event('resize'));
    await nextFrame();
    await fixture.whenStable();
    expect(component.endingTrackHeight()).toBe(0);
    expect(component.storyTrackHeight()).toBe(0);
    expect(component.initialHold()).toBe(0);
  });

  it('scrubs the print reveal from the reading position without capturing wheel events', async () => {
    const panel = host.querySelector<HTMLElement>('.process-visual')!;
    const line = Math.min(window.innerHeight * 0.5, 460);
    const rows = host.querySelectorAll<HTMLElement>('.process-step');
    rows.forEach((row, index) =>
      spyOn(row, 'getBoundingClientRect').and.returnValue(
        new DOMRect(0, (index - 2) * 400 + line - 170, 400, 400),
      ),
    );
    const scrollEvent = new Event('scroll', { cancelable: true });
    window.dispatchEvent(scrollEvent);
    await nextFrame();
    await fixture.whenStable();
    expect(component.activeStep().scene).toBe('print');
    expect(component.stepFraction()).toBeCloseTo(0.5, 2);
    expect(component.printTop()).toBeCloseTo(197, 1);
    expect(scrollEvent.defaultPrevented).toBeFalse();
    expect(
      host.querySelectorAll('.process-nav-item[aria-current="step"]').length,
    ).toBe(1);
  });

  it('moves the existing parcel from production to the house with scroll progress', async () => {
    const panel = host.querySelector<HTMLElement>('.process-visual')!;
    const line = Math.min(window.innerHeight * 0.5, 460);
    const rows = host.querySelectorAll<HTMLElement>('.process-step');
    let offset = 34;
    rows.forEach((row, index) =>
      spyOn(row, 'getBoundingClientRect').and.callFake(
        () => new DOMRect(0, (index - 3) * 400 + line - offset, 400, 400),
      ),
    );
    window.dispatchEvent(new Event('scroll'));
    await nextFrame();
    await fixture.whenStable();
    const initial = host
      .querySelector('.shipping-parcel')
      ?.getAttribute('transform');
    expect(component.activeStep().scene).toBe('ship');
    offset = 272;
    window.dispatchEvent(new Event('scroll'));
    await nextFrame();
    await fixture.whenStable();
    const final = host
      .querySelector('.shipping-parcel')
      ?.getAttribute('transform');
    const initialX = Number(initial?.split(/[() ]/)[1]);
    const finalX = Number(final?.split(/[() ]/)[1]);
    expect(finalX).toBeGreaterThan(initialX + 100);
  });

  it('starts printing with an empty reveal and no ghost of the complete object', async () => {
    component.stepFraction.set(0);
    await fixture.whenStable();
    const clip = host.querySelector('#home-process-print rect');
    expect(Number(clip?.getAttribute('height'))).toBe(0);
    const printer = host.querySelectorAll('.scene')[4];
    expect(printer.querySelectorAll('image').length).toBe(1);
    expect(printer.querySelector('image')?.getAttribute('href')).toContain(
      'benchy-printed-yellow.png',
    );
    expect(
      printer.querySelector('image')?.closest('g')?.getAttribute('clip-path'),
    ).toBe('url(#home-process-print)');
  });

  it('writes the idea message, reveals our reply and draws the confirmation with scroll progress', async () => {
    component.stepFraction.set(0);
    await fixture.whenStable();
    const lines = Array.from(host.querySelectorAll('.brief-message-line'));
    const reply = host.querySelector('.brief-reply');
    const check = host.querySelector('.brief-confirmation');
    expect(
      lines.every((line) => line.getAttribute('stroke-dashoffset') === '1'),
    ).toBeTrue();
    expect(reply?.getAttribute('opacity')).toBe('0');
    component.stepFraction.set(0.3);
    await fixture.whenStable();
    expect(lines[0].getAttribute('stroke-dashoffset')).toBe('0');
    expect(Number(lines[1].getAttribute('stroke-dashoffset'))).toBeLessThan(1);
    expect(lines[2].getAttribute('stroke-dashoffset')).toBe('1');
    expect(reply?.getAttribute('opacity')).toBe('0');
    component.stepFraction.set(0.65);
    await fixture.whenStable();
    expect(Number(reply?.getAttribute('opacity'))).toBeGreaterThan(0);
    expect(check?.getAttribute('stroke-dashoffset')).toBe('1');
    component.stepFraction.set(1);
    await fixture.whenStable();
    expect(reply?.getAttribute('opacity')).toBe('1');
    expect(check?.getAttribute('stroke-dashoffset')).toBe('0');
    component.stepFraction.set(0);
    await fixture.whenStable();
    expect(reply?.getAttribute('opacity')).toBe('0');
    component.reducedMotion.set(true);
    await fixture.whenStable();
    expect(
      lines.every((line) => line.getAttribute('stroke-dashoffset') === '0'),
    ).toBeTrue();
    expect(reply?.getAttribute('opacity')).toBe('1');
    expect(check?.getAttribute('stroke-dashoffset')).toBe('0');
  });

  it('moves the upload, reveals its model and confirms completion with scroll progress', async () => {
    component.stepFraction.set(0);
    await fixture.whenStable();
    const file = host.querySelector('.upload-file');
    const model = host.querySelector('.upload-model');
    const check = host.querySelector('.upload-confirmation');
    expect(file?.getAttribute('transform')).toBe('translate(0 20)');
    expect(model?.getAttribute('opacity')).toBe('0');
    component.stepFraction.set(0.2);
    await fixture.whenStable();
    expect(file?.getAttribute('transform')).not.toBe('translate(0 20)');
    expect(Number(model?.getAttribute('opacity'))).toBeGreaterThan(0);
    expect(check?.getAttribute('stroke-dashoffset')).toBe('1');
    component.stepFraction.set(1);
    await fixture.whenStable();
    expect(file?.getAttribute('transform')).toBe('translate(0 0)');
    expect(host.querySelector('.upload-arrow')?.getAttribute('opacity')).toBe(
      '0',
    );
    expect(check?.getAttribute('stroke-dashoffset')).toBe('0');
  });

  it('selects material and quality before revealing the quote and confirmation', async () => {
    const width = (selector: string) =>
      Number(host.querySelector(selector)?.getAttribute('width'));
    component.stepFraction.set(0.15);
    await fixture.whenStable();
    expect(
      Number(host.querySelector('.quote-material')?.getAttribute('opacity')),
    ).toBeGreaterThan(0);
    expect(width('.quote-quality')).toBe(0);
    expect(width('.quote-price')).toBe(0);
    component.stepFraction.set(0.4);
    await fixture.whenStable();
    expect(width('.quote-quality')).toBeGreaterThan(0);
    expect(width('.quote-price')).toBe(0);
    component.stepFraction.set(0.7);
    await fixture.whenStable();
    expect(width('.quote-price')).toBeGreaterThan(0);
    expect(width('.quote-complete')).toBe(0);
    component.stepFraction.set(0);
    component.reducedMotion.set(true);
    await fixture.whenStable();
    expect(width('.quote-price')).toBe(119);
    expect(width('.quote-complete')).toBe(96);
    expect(
      host
        .querySelector('.upload-confirmation')
        ?.getAttribute('stroke-dashoffset'),
    ).toBe('0');
    expect(
      host
        .querySelector('.quote-confirmation')
        ?.getAttribute('stroke-dashoffset'),
    ).toBe('0');
  });

  it('uses a matching hand sketch for design and the same object for printing', () => {
    const scenes = host.querySelectorAll('.scene');
    expect(scenes[2].querySelector('image')?.getAttribute('href')).toContain(
      'benchy-sketch-draft.png',
    );
    expect(scenes[4].querySelector('image')?.getAttribute('href')).toContain(
      'benchy-printed-yellow.png',
    );
    expect(scenes[0].querySelector('image')?.getAttribute('href')).toContain(
      'benchy-digital-grey.png',
    );
    expect(scenes[3].querySelector('image')?.getAttribute('href')).toContain(
      'benchy-digital-grey.png',
    );
  });

  it('draws the draft contour by contour as scrolling advances and erases it in reverse', async () => {
    component.stepFraction.set(0);
    await fixture.whenStable();
    const strokes = Array.from(host.querySelectorAll('.sketch-stroke'));
    expect(
      strokes.every(
        (stroke) => Number(stroke.getAttribute('stroke-dashoffset')) === 1,
      ),
    ).toBeTrue();
    component.stepFraction.set(0.4);
    await fixture.whenStable();
    expect(Number(strokes[0].getAttribute('stroke-dashoffset'))).toBe(0);
    expect(
      Number(strokes[strokes.length - 1].getAttribute('stroke-dashoffset')),
    ).toBe(1);
    component.stepFraction.set(1);
    await fixture.whenStable();
    expect(
      strokes.every(
        (stroke) => Number(stroke.getAttribute('stroke-dashoffset')) === 0,
      ),
    ).toBeTrue();
    component.stepFraction.set(0);
    await fixture.whenStable();
    expect(
      strokes.every(
        (stroke) => Number(stroke.getAttribute('stroke-dashoffset')) === 1,
      ),
    ).toBeTrue();
  });

  it('opens the parcel before lifting out the printed part and resets when scrolling back', async () => {
    component.stepFraction.set(0);
    await fixture.whenStable();
    const lid = host.querySelector('.parcel-closed-lid');
    const part = host.querySelector('.unboxed-part');
    expect(Number(lid?.getAttribute('opacity'))).toBe(1);
    expect(Number(part?.getAttribute('opacity'))).toBe(0);
    component.stepFraction.set(0.2);
    await fixture.whenStable();
    expect(Number(lid?.getAttribute('opacity'))).toBeLessThan(1);
    expect(Number(part?.getAttribute('opacity'))).toBe(0);
    component.stepFraction.set(1);
    await fixture.whenStable();
    expect(Number(lid?.getAttribute('opacity'))).toBe(0);
    expect(Number(part?.getAttribute('opacity'))).toBe(1);
    expect(part?.getAttribute('transform')).toBe('translate(0 -142)');
    component.stepFraction.set(0);
    await fixture.whenStable();
    expect(Number(lid?.getAttribute('opacity'))).toBe(1);
    expect(Number(part?.getAttribute('opacity'))).toBe(0);
  });

  it('renders complete illustrations for reduced motion and keeps all text available', async () => {
    component.stepFraction.set(0.2);
    component.reducedMotion.set(true);
    await fixture.whenStable();
    expect(component.printTop()).toBe(95);
    expect(component.sketchProgress()).toBe(1);
    expect(component.unboxOpen()).toBe(1);
    expect(component.unboxLift()).toBe(1);
    expect(host.querySelectorAll('.process-step h3').length).toBe(5);
    expect(host.querySelectorAll('.process-nav-item').length).toBe(5);
  });

  it('updates explanatory text when the language changes', async () => {
    await firstValueFrom(translate.use('en'));
    await fixture.whenStable();
    expect(host.querySelector('#process-title')?.textContent).toContain(
      'Where does your next part begin?',
    );
    expect(host.querySelector('.path-option')?.textContent).toContain(
      'I have a 3D file',
    );
  });

  it('removes its scroll and resize listeners when leaving the page', () => {
    const remove = spyOn(window, 'removeEventListener').and.callThrough();
    fixture.destroy();
    expect(remove).toHaveBeenCalledWith('scroll', jasmine.any(Function));
    expect(remove).toHaveBeenCalledWith('resize', jasmine.any(Function));
  });

  it('follows the scrolling mobile descriptions without hiding adjacent steps or adding a runway', async () => {
    component.mobileLayout.set(true);
    const panel = host.querySelector<HTMLElement>('.process-visual')!;
    const grid = host.querySelector<HTMLElement>('.process-grid')!;
    const story = host.querySelector<HTMLElement>('.process-story')!;
    const storyTrack = host.querySelector<HTMLElement>('.process-story-track')!;
    let storyTop = 120;
    spyOn(panel, 'getBoundingClientRect').and.returnValue(new DOMRect(0, 74, 360, 200));
    spyOn(storyTrack, 'getBoundingClientRect').and.callFake(
      () => new DOMRect(0, storyTop, 360, 1500),
    );
    spyOnProperty(storyTrack, 'offsetHeight', 'get').and.returnValue(1500);
    grid.style.display = 'block';
    panel.style.top = '74px';
    spyOnProperty(panel, 'offsetHeight', 'get').and.returnValue(200);
    await fixture.whenStable();
    const line = 274 + Number.parseFloat(getComputedStyle(story).paddingTop);
    let shift = 260 + 260 * 0.85 / 2;
    const rows = host.querySelectorAll<HTMLElement>('.process-step');
    rows.forEach((row, index) =>
      spyOn(row, 'getBoundingClientRect').and.callFake(
        () => new DOMRect(0, line + index * 260 - shift, 360, 260),
      ),
    );
    const event = new Event('scroll', {cancelable: true});
    window.dispatchEvent(event);
    await nextFrame();
    await fixture.whenStable();
    expect(component.activeIndex()).toBe(1);
    expect(component.stepFraction()).toBeCloseTo(0.5, 2);
    expect(event.defaultPrevented).toBeFalse();
    expect(host.querySelectorAll('.process-step-track[inert]').length).toBe(0);
    expect(host.querySelector('.process-scroll-track')).toBeNull();
    expect(component.storyTrackHeight()).toBe(0);
    expect(component.mobileStoryClip()).toBe(154);
    expect(Array.from(host.querySelectorAll('.process-nav-item')).map(e => e.textContent?.trim())).toEqual(['1', '2', '3', '4', '5']);
    shift = 0;
    storyTop = 300;
    window.dispatchEvent(new Event('scroll'));
    await nextFrame();
    expect(component.activeIndex()).toBe(0);
    expect(component.stepFraction()).toBe(0);
    expect(component.mobileStoryClip()).toBe(0);
  });

  it('aligns numbered mobile links below the diagram and supports keyboard navigation', async () => {
    component.mobileLayout.set(true);
    const panel = host.querySelector<HTMLElement>('.process-visual')!;
    const grid = host.querySelector<HTMLElement>('.process-grid')!;
    const story = host.querySelector<HTMLElement>('.process-story')!;
    grid.style.display = 'block';
    panel.style.top = '74px';
    spyOnProperty(panel, 'offsetHeight', 'get').and.returnValue(200);
    await fixture.whenStable();
    const rows = host.querySelectorAll<HTMLElement>('.process-step');
    spyOn(rows[2], 'getBoundingClientRect').and.returnValue(new DOMRect(0, 500, 360, 260));
    spyOn(rows[4], 'getBoundingClientRect').and.returnValue(new DOMRect(0, 1000, 360, 260));
    const line = 274 + Number.parseFloat(getComputedStyle(story).paddingTop);
    const scroll = spyOn(window, 'scrollTo');
    component.goToStep(2);
    expect(scroll.calls.mostRecent().args[0] as unknown).toEqual({
      top: window.scrollY + 500 - line + 2,
      behavior: 'smooth'
    });
    component.reducedMotion.set(true);
    grid.dispatchEvent(new KeyboardEvent('keydown', {key: 'End', bubbles: true}));
    expect(scroll.calls.mostRecent().args[0] as unknown).toEqual({
      top: window.scrollY + 1000 - line + 2,
      behavior: 'auto'
    });
  });
});
