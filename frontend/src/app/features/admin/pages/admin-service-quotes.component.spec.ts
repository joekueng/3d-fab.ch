import { TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { LanguageService } from '../../../core/services/language.service';
import { ServiceQuotesApiService } from '../../service-quotes/service-quotes-api.service';
import { AdminServiceQuotesComponent } from './admin-service-quotes.component';

describe('admin service quote editor', () => {
  function setup() {
    const api = jasmine.createSpyObj<ServiceQuotesApiService>('api', ['list', 'save']);
    api.list.and.returnValue(of([]));
    TestBed.configureTestingModule({ imports: [AdminServiceQuotesComponent], providers: [
      provideRouter([]), provideTranslateService(),
      { provide: ServiceQuotesApiService, useValue: api },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
      { provide: LanguageService, useValue: { selectedLang: () => 'it', localizedPath: (path: string) => '/it' + path } },
    ] });
    const fixture = TestBed.createComponent(AdminServiceQuotesComponent);
    fixture.autoDetectChanges();
    return { fixture, component: fixture.componentInstance, api };
  }

  it('identifies the missing scope without asking for customer contact details', async () => {
    const { fixture, component, api } = setup();
    component.form.controls.title.setValue('Prototype');
    component.save();
    await fixture.whenStable();
    expect(api.save).not.toHaveBeenCalled();
    expect(component.fieldError('description')).toBe('SERVICE_QUOTES.FIELD_REQUIRED');
    expect(fixture.nativeElement.querySelector('app-textarea textarea').required).toBeTrue();
    expect(fixture.nativeElement.querySelector('input[type=email]')).toBeNull();
    fixture.destroy();
  });

  it('saves a quote without an email or customer address', () => {
    const { fixture, component, api } = setup();
    api.save.and.returnValue(of({ id: 'quote', createdAt: new Date().toISOString(), version: 0, clientName: '', collaborationName: '', sourceRequestId: null, draft: null, revisions: [], linkedJobs: [], emailLogs: [] }));
    component.form.patchValue({ title: 'Prototype', description: 'Model one prototype' });
    component.save();
    expect(api.save).toHaveBeenCalledTimes(1);
    expect(api.save.calls.mostRecent().args[1].content).not.toEqual(jasmine.objectContaining({ recipientEmail: jasmine.anything() }));
    fixture.destroy();
  });
});
