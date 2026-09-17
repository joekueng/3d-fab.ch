import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageService } from '../../../core/services/language.service';
import { ColorSelectorComponent } from './color-selector.component';

describe('ColorSelectorComponent', () => {
  it('distinguishes product variants with the same color name and emits their opaque ids', async () => {
    await TestBed.configureTestingModule({
      imports: [ColorSelectorComponent, TranslateModule.forRoot()],
      providers: [
        { provide: LanguageService, useValue: { selectedLang: () => 'it' } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ColorSelectorComponent);
    fixture.componentRef.setInput('groups', [
      {
        name: 'PLA',
        colors: [
          {
            variantId: 'product-a',
            value: 'Blue',
            label: 'Light blue',
            hex: '#aaccee',
          },
          {
            variantId: 'product-b',
            value: 'Blue',
            label: 'Dark blue',
            hex: '#001144',
          },
          {
            variantId: 'product-c',
            value: 'Red',
            label: 'Red',
            hex: '#ff0000',
            outOfStock: true,
          },
        ],
      },
    ]);
    fixture.componentRef.setInput('selectedVariantId', 'product-b');
    fixture.componentRef.setInput('showLabel', true);
    const selected = jasmine.createSpy('selected');
    fixture.componentInstance.variantSelected.subscribe(selected);
    fixture.detectChanges();
    expect(fixture.componentInstance.getCurrentHex()).toBe('#001144');
    expect(fixture.componentInstance.getCurrentLabel()).toBe('Dark blue');
    const element: HTMLElement = fixture.nativeElement;
    element.querySelector<HTMLButtonElement>('.trigger')!.click();
    fixture.detectChanges();
    const options = element.querySelectorAll<HTMLButtonElement>('.color-item');
    expect(options.length).toBe(3);
    options[2].click();
    expect(selected).not.toHaveBeenCalled();
    options[0].click();
    fixture.detectChanges();
    expect(selected).toHaveBeenCalledWith('product-a');
    expect(element.querySelector('.color-popup')).toBeNull();
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    expect(
      element.querySelector<HTMLButtonElement>('.trigger')!.disabled,
    ).toBeTrue();
  });
});
