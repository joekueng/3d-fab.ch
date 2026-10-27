import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { LanguageService } from '../../core/services/language.service';
import { ServiceQuoteAcceptanceComponent } from './service-quote-acceptance.component';
import { ServiceQuotesApiService } from './service-quotes-api.service';
import { QuoteConfirmation, ServiceQuoteOffer } from './service-quotes.models';

const offer: ServiceQuoteOffer = {
  quoteId: 'quote', revisionId: 'revision', revisionNumber: 1, status: 'PUBLISHED',
  content: { title: 'Prototype', description: 'One design service', pricingMode: 'HOURLY_ESTIMATE', estimatedHours: 10, hourlyRateChf: 80, lumpSumChf: null, variationType: 'PERCENTAGE', variationValue: 15, validUntil: '2026-12-01T12:00:00Z', language: 'en' },
  estimatedAmountChf: 800, maximumAmountChf: 920, acceptanceText: 'Accepted scope', contentVersion: 'version', termsVersion: 'terms', privacyVersion: 'privacy',
};
function setup(accepted = false, delayed?: Subject<ServiceQuoteOffer>) {
  const api = jasmine.createSpyObj<ServiceQuotesApiService>('api', ['resume', 'offer', 'accept', 'pdfStatus', 'publicPdf']);
  api.resume.and.returnValue(of({ status: accepted ? 'ACCEPTED' : 'PUBLISHED', quoteId: accepted ? null : 'quote', revisionId: accepted ? null : 'revision', token: accepted ? null : 'offer-token' }));
  api.offer.and.returnValue(delayed ?? of(offer));
  TestBed.configureTestingModule({ imports: [ServiceQuoteAcceptanceComponent], providers: [provideTranslateService(),
    { provide: ServiceQuotesApiService, useValue: api },
    { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ publicLinkId: 'link' }) } } },
    { provide: LanguageService, useValue: { localizedPath: (path: string) => '/en' + path } },
  ] });
  const fixture = TestBed.createComponent(ServiceQuoteAcceptanceComponent); fixture.autoDetectChanges();
  return { fixture, component: fixture.componentInstance, api };
}
function fill(component: ServiceQuoteAcceptanceComponent) {
  component.form.patchValue({ email: 'test@example.test', firstName: 'Ada', lastName: 'Example', addressLine1: 'Example 1', zip: '6900', city: 'Lugano', countryCode: 'CH', acceptQuote: true, legal: true });
}

describe('service quote acceptance', () => {
  it('a reopened accepted link cannot recover credentials or offer a PDF download', async () => {
    const { fixture, component, api } = setup(true);
    await fixture.whenStable();
    expect(component.alreadyAccepted()).toBeTrue(); expect(api.offer).not.toHaveBeenCalled(); expect(api.pdfStatus).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('SERVICE_QUOTES.ALREADY_ACCEPTED');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('SERVICE_QUOTES.DOWNLOAD_NOTICE');
    fixture.destroy();
  });
  it('rejects missing identity and consents before sending', () => {
    const { fixture, component, api } = setup();
    component.submit(); expect(api.accept).not.toHaveBeenCalled();
    fill(component); component.form.controls.firstName.setValue(' '); component.submit(); expect(api.accept).not.toHaveBeenCalled();
    component.form.patchValue({ firstName: 'Ada', customerType: 'BUSINESS', companyName: 'ACME', contactPerson: '' });
    component.submit(); expect(api.accept).not.toHaveBeenCalled(); fixture.destroy();
  });
  it('allows a business to accept without private-person names after changing customer type', async () => {
    const { fixture, component, api } = setup();
    await fixture.whenStable();
    component.form.patchValue({ customerType: 'BUSINESS', email: 'business@example.test', companyName: 'Example AG', contactPerson: 'Ada Example', addressLine1: 'Example 1', zip: '6900', city: 'Lugano', acceptQuote: true, legal: true });
    await fixture.whenStable();
    expect(Object.entries(component.form.controls).filter(([,control]) => control.invalid).map(([name]) => name)).toEqual([]);
    api.accept.and.returnValue(throwError(() => ({ status: 0 })));
    component.submit(); expect(api.accept).toHaveBeenCalledTimes(1); fixture.destroy();
  });
  it('network retries keep the original payload and do not write credentials to browser storage', () => {
    const { fixture, component, api } = setup(); fill(component);
    const storage = spyOn(Storage.prototype, 'setItem');
    api.accept.and.returnValue(throwError(() => ({ status: 0 })));
    component.submit(); const original = api.accept.calls.mostRecent().args[2];
    expect(component.form.disabled).toBeTrue(); expect(original.downloadToken).toMatch(/^[0-9a-f]{64}$/);
    component.submit(); expect(api.accept.calls.mostRecent().args[2]).toBe(original);
    expect(storage).not.toHaveBeenCalled(); fixture.destroy();
  });
  it('blocks duplicate clicks while the acceptance response is pending', () => {
    const { fixture, component, api } = setup(); fill(component);
    const response = new Subject<QuoteConfirmation>(); api.accept.and.returnValue(response);
    component.submit(); component.submit(); expect(api.accept).toHaveBeenCalledTimes(1); fixture.destroy();
  });
  it('renders an asynchronously loaded offer without an extra click', async () => {
    const response = new Subject<ServiceQuoteOffer>(); const { fixture } = setup(false, response);
    response.next(offer);
    await fixture.whenStable(); expect(fixture.nativeElement.textContent).toContain('Prototype'); expect(fixture.nativeElement.textContent).toContain('920'); fixture.destroy();
  });
});
