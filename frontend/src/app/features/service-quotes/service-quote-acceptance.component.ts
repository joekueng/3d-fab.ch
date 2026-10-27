import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Component, DestroyRef, PLATFORM_ID, inject, signal, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, switchMap, takeWhile, timer } from 'rxjs';
import { AppInputComponent } from '../../shared/components/app-input/app-input.component';
import { AppSelectComponent } from '../../shared/components/app-select/app-select.component';
import { AppCheckboxComponent } from '../../shared/components/app-checkbox/app-checkbox.component';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { AppAlertComponent } from '../../shared/components/app-alert/app-alert.component';
import { LegalConsentComponent } from '../../shared/components/legal-consent/legal-consent.component';
import { downloadBlobInBrowser } from '../../core/utils/browser-download';
import { ServiceQuotesApiService } from './service-quotes-api.service';
import { QuoteAcceptRequest, QuoteConfirmation, ServiceQuoteOffer } from './service-quotes.models';
import { ServiceQuoteSummaryComponent } from './service-quote-summary.component';

@Component({
  selector: 'app-service-quote-acceptance',
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe, AppInputComponent, AppSelectComponent,
    AppCheckboxComponent, AppButtonComponent, AppCardComponent, AppAlertComponent, LegalConsentComponent, ServiceQuoteSummaryComponent],
  templateUrl: './service-quote-acceptance.component.html',
  styleUrl: './service-quote-acceptance.component.scss',
})
export class ServiceQuoteAcceptanceComponent implements OnInit {
  private readonly api = inject(ServiceQuotesApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  readonly offer = signal<ServiceQuoteOffer | null>(null);
  readonly confirmation = signal<QuoteConfirmation | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly alreadyAccepted = signal(false);
  readonly downloadExpired = signal(false);
  private offerToken = '';
  private downloadToken = '';
  protected pendingRequest: QuoteAcceptRequest | null = null;
  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    customerType: this.fb.control<'PRIVATE' | 'BUSINESS'>('PRIVATE'),
    firstName: ['', Validators.maxLength(100)], lastName: ['', Validators.maxLength(100)],
    companyName: ['', Validators.maxLength(160)], contactPerson: ['', Validators.maxLength(160)],
    addressLine1: ['', [Validators.required, Validators.maxLength(200)]],
    zip: ['', [Validators.required, Validators.maxLength(20)]], city: ['', [Validators.required, Validators.maxLength(100)]],
    countryCode: ['CH', [Validators.required, Validators.pattern('[A-Z]{2}')]], phone: ['', Validators.maxLength(40)],
    acceptQuote: [false, Validators.requiredTrue], legal: [false, Validators.requiredTrue],
  });
  ngOnInit() {
    const identityValidators = (type: 'PRIVATE' | 'BUSINESS') => {
      for (const name of ['firstName', 'lastName', 'companyName', 'contactPerson'] as const) {
        const privateField = name === 'firstName' || name === 'lastName';
        const required = privateField ? type === 'PRIVATE' : type === 'BUSINESS';
        const control = this.form.controls[name];
        control.setValidators([Validators.maxLength(privateField ? 100 : 160), ...(required ? [Validators.required] : [])]);
        control.updateValueAndValidity({ emitEvent: false });
      }
    };
    identityValidators(this.form.controls.customerType.value);
    this.form.controls.customerType.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(type => { if (!this.busy()) identityValidators(type); });
    if (!this.browser) return;
    const previousReferrer = document.querySelector<HTMLMetaElement>('meta[name="referrer"]');
    const previousContent = previousReferrer?.content;
    const referrer = previousReferrer ?? document.createElement('meta');
    referrer.name = 'referrer'; referrer.content = 'no-referrer';
    if (!previousReferrer) document.head.appendChild(referrer);
    this.destroyRef.onDestroy(() => { if (previousReferrer) previousReferrer.content = previousContent ?? ''; else referrer.remove(); });
    this.api.resume(this.route.snapshot.paramMap.get('publicLinkId') ?? '').pipe(
      switchMap(resume => {
        if (resume.status === 'ACCEPTED') { this.alreadyAccepted.set(true); this.loading.set(false); return EMPTY; }
        if (!resume.quoteId || !resume.revisionId || !resume.token) { this.error.set('SERVICE_QUOTES.UNAVAILABLE'); this.loading.set(false); return EMPTY; }
        this.offerToken = resume.token;
        return this.api.offer(resume.quoteId, resume.revisionId, resume.token);
      }), takeUntilDestroyed(this.destroyRef),
    ).subscribe({ next: offer => { this.offer.set(offer); this.loading.set(false); }, error: () => { this.error.set('SERVICE_QUOTES.UNAVAILABLE'); this.loading.set(false); } });
  }
  submit() {
    if (this.busy()) return;
    this.form.markAllAsTouched();
    const value = this.form.getRawValue();
    const identity = value.customerType === 'BUSINESS' ? value.companyName.trim() && value.contactPerson.trim() : value.firstName.trim() && value.lastName.trim();
    if (this.form.invalid || !identity) { this.error.set('SERVICE_QUOTES.INVALID'); return; }
    const offer = this.offer(); if (!offer) return;
    if (!this.pendingRequest) {
      this.downloadToken = Array.from(crypto.getRandomValues(new Uint8Array(32)), byte => byte.toString(16).padStart(2, '0')).join('');
      const { acceptQuote, legal, ...customer } = value;
      this.pendingRequest = { customer, requestId: crypto.randomUUID(), downloadToken: this.downloadToken, contentVersion: offer.contentVersion, acceptQuote, acceptTerms: legal, acceptPrivacy: legal };
    }
    this.busy.set(true); this.error.set(null); this.form.disable();
    this.api.accept(offer, this.offerToken, this.pendingRequest).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: confirmation => { this.confirmation.set(confirmation); this.busy.set(false); this.poll(confirmation); },
      error: response => {
        this.busy.set(false);
        if (response.status === 0 || response.status >= 500) { this.error.set('SERVICE_QUOTES.RETRY_SUBMIT'); }
        else { this.pendingRequest = null; this.form.enable(); this.error.set(response.status === 409 || response.status === 410 ? 'SERVICE_QUOTES.CHANGED' : 'SERVICE_QUOTES.INVALID'); }
      },
    });
  }
  private poll(initial: QuoteConfirmation) {
    timer(Math.max(0, Date.parse(initial.downloadExpiresAt) - Date.now())).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.downloadExpired.set(true));
    timer(0, 2000).pipe(
      takeWhile(() => !this.downloadExpired() && Date.now() < Date.parse(initial.downloadExpiresAt)),
      exhaustMap(() => this.api.pdfStatus(initial.acceptanceId, this.downloadToken).pipe(catchError(() => {
        this.downloadExpired.set(true); return EMPTY;
      }))),
      takeWhile(status => status.documentStatus !== 'FAILED' && (status.documentStatus === 'PENDING' || status.emailStatus === 'PENDING'), true),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({ next: status => this.confirmation.set(status), complete: () => { if (Date.now() >= Date.parse(initial.downloadExpiresAt)) this.downloadExpired.set(true); } });
  }
  download() {
    const confirmation = this.confirmation(); if (!confirmation || this.busy()) return;
    if (Date.now() >= Date.parse(confirmation.downloadExpiresAt)) { this.downloadExpired.set(true); return; }
    this.busy.set(true); this.error.set(null);
    this.api.publicPdf(confirmation.acceptanceId, this.downloadToken).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: blob => { downloadBlobInBrowser(blob, `preventivo-${this.offer()?.quoteId}-accettato.pdf`); this.busy.set(false); },
      error: () => { this.busy.set(false); this.error.set('SERVICE_QUOTES.DOWNLOAD_ERROR'); },
    });
  }
}
