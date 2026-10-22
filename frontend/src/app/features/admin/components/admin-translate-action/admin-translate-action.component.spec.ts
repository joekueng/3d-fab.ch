import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {Subject} from 'rxjs';
import {AdminTranslateActionComponent} from './admin-translate-action.component';
import {
  AdminTranslateLocalizedTextResponse,
  AdminTranslationService,
} from '../../services/admin-translation.service';

describe('AdminTranslateActionComponent', () => {
  function setup(overwriteExisting = false) {
    const response = new Subject<AdminTranslateLocalizedTextResponse>();
    const service = jasmine.createSpyObj<AdminTranslationService>(
      'translation',
      ['translateLocalizedText'],
    );
    service.translateLocalizedText.and.returnValue(response);
    TestBed.configureTestingModule({
      imports: [AdminTranslateActionComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideTranslateService(),
        {provide: AdminTranslationService, useValue: service},
      ],
    });
    const fixture = TestBed.createComponent(AdminTranslateActionComponent);
    const values = {it: 'Nero', en: 'Old English', de: 'Alt', fr: 'Ancien'};
    fixture.componentRef.setInput('target', values);
    fixture.componentRef.setInput('context', 'Filament colors');
    fixture.componentRef.setInput('fields', {
      colorLabel: {required: true, values},
    });
    fixture.autoDetectChanges();
    fixture.componentInstance.overwriteExisting = overwriteExisting;
    const emitted = jasmine.createSpy('translated');
    fixture.componentInstance.translated.subscribe(emitted);
    return {
      fixture,
      component: fixture.componentInstance,
      values,
      response,
      service,
      emitted,
    };
  }

  const translated: AdminTranslateLocalizedTextResponse = {
    sourceLanguage: 'it',
    targetLanguages: ['en', 'de', 'fr'],
    fields: {
      colorLabel: {
        it: 'Do not change',
        en: 'Black',
        de: 'Schwarz',
        fr: 'Noir',
      },
    },
  };

  it('overwrites other languages when explicitly selected and preserves the source', async () => {
    const {fixture, component, service, response, emitted} = setup(true);
    await fixture.whenStable();
    fixture.nativeElement.querySelector('button').click();
    expect(service.translateLocalizedText).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({
        sourceLanguage: 'it',
        overwriteExisting: true,
      }),
    );
    response.next(translated);
    await fixture.whenStable();
    expect(emitted).toHaveBeenCalledWith(
      jasmine.objectContaining({
        fields: {colorLabel: {en: 'Black', de: 'Schwarz', fr: 'Noir'}},
      }),
    );
    expect(component.busy()).toBeFalse();
    expect(
      fixture.nativeElement.querySelector('[role="status"]'),
    ).not.toBeNull();
  });

  it('requires an Italian source and prevents duplicate requests', () => {
    const {component, values, service} = setup(true);
    values.it = ' ';
    component.translate();
    expect(service.translateLocalizedText).not.toHaveBeenCalled();
    values.it = 'Nero';
    component.translate();
    component.translate();
    expect(service.translateLocalizedText).toHaveBeenCalledTimes(1);
  });

  it('keeps existing values by default and translates populated optional fields', () => {
    const {fixture, component, values, response, service, emitted} = setup();
    fixture.componentRef.setInput('fields', {
      colorLabel: {required: true, values},
      description: {
        required: false,
        values: {it: 'Opaco', en: '', de: '', fr: ''},
      },
    });
    fixture.detectChanges();
    component.translate();
    expect(
      service.translateLocalizedText.calls.mostRecent().args[0].fields[
        'description'
        ].required,
    ).toBeTrue();
    response.next({
      ...translated,
      fields: {...translated.fields, description: {en: 'Matte'}},
    });
    expect(emitted.calls.mostRecent().args[0].fields).toEqual({
      description: {en: 'Matte'},
    });
  });

  it('preserves edits made while the request is in progress', () => {
    const {component, values, response, emitted} = setup(true);
    component.translate();
    values.en = 'Manually edited';
    response.next(translated);
    expect(emitted.calls.mostRecent().args[0].fields).toEqual({
      colorLabel: {de: 'Schwarz', fr: 'Noir'},
    });
  });

  it('discards translations when the source changes or the editor switches records', async () => {
    const {fixture, component, values, response, emitted} = setup(true);
    component.translate();
    values.it = 'Bianco';
    response.next(translated);
    expect(emitted.calls.mostRecent().args[0].fields).toEqual({});
    emitted.calls.reset();
    component.translate();
    fixture.componentRef.setInput('target', {});
    await fixture.whenStable();
    response.next(translated);
    expect(emitted).not.toHaveBeenCalled();
    expect(component.busy()).toBeFalse();
  });

  it('renders asynchronous errors and allows retry', async () => {
    const {fixture, component, response} = setup(true);
    component.translate();
    response.error(new Error('Unavailable'));
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('[role="alert"]'),
    ).not.toBeNull();
    expect(component.canTranslate()).toBeTrue();
  });
});
