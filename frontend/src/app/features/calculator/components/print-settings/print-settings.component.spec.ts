import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { PrintSettingsComponent } from './print-settings.component';

describe('PrintSettingsComponent', () => {
  it('updates the supplied form and follows changing options and disabled state', async () => {
    await TestBed.configureTestingModule({
      imports: [PrintSettingsComponent, TranslateModule.forRoot()],
    }).compileComponents();
    const fixture = TestBed.createComponent(PrintSettingsComponent);
    const form = new FormGroup({
      material: new FormControl('PLA'),
      nozzleDiameter: new FormControl(0.4),
      infillPattern: new FormControl('grid'),
      layerHeight: new FormControl(0.2),
      infillDensity: new FormControl(15),
      supportEnabled: new FormControl(true),
    });
    fixture.componentRef.setInput('form', form);
    fixture.componentRef.setInput('title', 'Global settings');
    fixture.componentRef.setInput('materials', [
      { label: 'PLA', value: 'PLA' },
    ]);
    fixture.componentRef.setInput('nozzles', [{ label: '0.4', value: 0.4 }]);
    fixture.componentRef.setInput('patterns', [
      { label: 'Grid', value: 'grid' },
    ]);
    fixture.componentRef.setInput('layers', [
      { label: '0.2', value: 0.2 },
      { label: '0.3', value: 0.3 },
    ]);
    fixture.detectChanges();
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;
    const density =
      element.querySelector<HTMLInputElement>('input[type=number]')!;
    density.value = '30';
    density.dispatchEvent(new Event('input'));
    expect(form.controls.infillDensity.value).toBe(30);
    const layers = element.querySelectorAll('select')[3];
    layers.selectedIndex = 1;
    layers.dispatchEvent(new Event('change'));
    expect(form.controls.layerHeight.value).toBe(0.3);
    form.disable();
    fixture.detectChanges();
    await fixture.whenStable();
    element
      .querySelectorAll<HTMLInputElement | HTMLSelectElement>('input, select')
      .forEach((control) => expect(control.disabled).toBeTrue());
    form.enable();
    fixture.detectChanges();
    await fixture.whenStable();
    element
      .querySelectorAll<HTMLInputElement | HTMLSelectElement>('input, select')
      .forEach((control) => expect(control.disabled).toBeFalse());
    fixture.componentRef.setInput('layers', [{ label: '0.1', value: 0.1 }]);
    form.controls.layerHeight.setValue(0.1);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(layers.options.length).toBe(1);
    expect(layers.options[0].textContent).toBe('0.1');
    expect(form.controls.layerHeight.value).toBe(0.1);
  });
});
