import deTranslations from '../../../../assets/i18n/de.json';
import { TestBed } from '@angular/core/testing';
import { FormControl, Validators } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LanguageService } from '../../../core/services/language.service';
import { LegalConsentComponent } from './legal-consent.component';

describe('LegalConsentComponent', () => {
  it('keeps consent required, links localized, and follows form reset and disabled state', async () => {
    await TestBed.configureTestingModule({
      imports: [LegalConsentComponent, TranslateModule.forRoot()],
      providers: [
        {
          provide: LanguageService,
          useValue: { localizedPath: (path: string) => '/fr' + path },
        },
      ],
    }).compileComponents();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('de', deTranslations);
    translate.use('de');
    const fixture = TestBed.createComponent(LegalConsentComponent);
    const control = new FormControl(false, Validators.requiredTrue);
    fixture.componentRef.setInput('control', control);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    element.style.width = '320px';
    expect(element.scrollWidth).toBeLessThanOrEqual(320);
    expect(
      Array.from(element.querySelectorAll('a')).map((link) =>
        link.getAttribute('href'),
      ),
    ).toEqual(['/fr/terms', '/fr/privacy']);
    expect(element.querySelector('[role=alert]')).toBeNull();
    control.markAsTouched();
    fixture.detectChanges();
    expect(element.querySelector('[role=alert]')).not.toBeNull();
    const checkbox = element.querySelector<HTMLInputElement>('input')!;
    checkbox.click();
    fixture.detectChanges();
    expect(control.value).toBeTrue();
    expect(control.valid).toBeTrue();
    expect(element.querySelector('[role=alert]')).toBeNull();
    control.disable();
    fixture.detectChanges();
    expect(checkbox.disabled).toBeTrue();
    control.enable();
    control.reset(false);
    fixture.detectChanges();
    expect(checkbox.disabled).toBeFalse();
    expect(checkbox.checked).toBeFalse();
    expect(control.invalid).toBeTrue();
  });
});
