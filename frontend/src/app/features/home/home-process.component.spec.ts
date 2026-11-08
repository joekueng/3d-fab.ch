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
    fixture = TestBed.createComponent(HomeProcessComponent);
    component = fixture.componentInstance;
    host = fixture.nativeElement as HTMLElement;
    fixture.autoDetectChanges();
    await nextFrame();
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
    expect(host.querySelector('.path-summary')?.textContent).toContain(
      'idea → progetto CAD',
    );
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

  it('scrubs the print reveal from the reading position without capturing wheel events', async () => {
    const line =
      window.innerWidth <= 760 && window.innerHeight > 600
        ? 380
        : Math.min(window.innerHeight * 0.5, 460);
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
    const line =
      window.innerWidth <= 760 && window.innerHeight > 600
        ? 380
        : Math.min(window.innerHeight * 0.5, 460);
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
});
