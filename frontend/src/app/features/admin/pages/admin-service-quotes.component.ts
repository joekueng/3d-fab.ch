import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { AppInputComponent } from '../../../shared/components/app-input/app-input.component';
import { AppSelectComponent } from '../../../shared/components/app-select/app-select.component';
import { AppTextareaComponent } from '../../../shared/components/app-textarea/app-textarea.component';
import { AppButtonComponent } from '../../../shared/components/app-button/app-button.component';
import { AppAlertComponent } from '../../../shared/components/app-alert/app-alert.component';
import { CopyOnClickDirective } from '../../../shared/directives/copy-on-click.directive';
import { LanguageService } from '../../../core/services/language.service';
import { downloadBlobInBrowser } from '../../../core/utils/browser-download';
import { ServiceQuotesApiService } from '../../service-quotes/service-quotes-api.service';
import { AdminServiceQuote, QuoteDraftRequest, QuoteRevision, QuotePricingMode, QuoteVariationType } from '../../service-quotes/service-quotes.models';
import { ServiceQuoteSummaryComponent } from '../../service-quotes/service-quote-summary.component';

@Component({
  selector: 'app-admin-service-quotes',
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslatePipe, AppInputComponent, AppSelectComponent,
    AppTextareaComponent, AppButtonComponent, AppAlertComponent, CopyOnClickDirective, ServiceQuoteSummaryComponent],
  templateUrl: './admin-service-quotes.component.html',
  styleUrl: './admin-service-quotes.component.scss',
})
export class AdminServiceQuotesComponent implements OnInit {
  readonly language = inject(LanguageService);
  private readonly api = inject(ServiceQuotesApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder).nonNullable;
  readonly rows = signal<AdminServiceQuote[]>([]);
  readonly selected = signal<AdminServiceQuote | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(160)]], description: ['', [Validators.required, Validators.maxLength(20000)]],
    pricingMode: this.fb.control<QuotePricingMode>('HOURLY_ESTIMATE'), estimatedHours: [1], hourlyRateChf: [80], lumpSumChf: [0],
    variationType: this.fb.control<QuoteVariationType>('PERCENTAGE'), variationValue: [0, [Validators.required, Validators.min(0)]],
    validUntil: [this.defaultDate(), Validators.required], language: ['it', Validators.required],
    clientName: ['', Validators.maxLength(160)], collaborationName: ['', Validators.maxLength(160)], sourceRequestId: [''],
  });
  readonly email = this.fb.control('', [Validators.required, Validators.email]);
  ngOnInit() { this.refresh(); }
  refresh() {
    if (this.busy()) return;
    this.busy.set(true);
    this.api.list().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: rows => {
      this.rows.set(rows); const selected = rows.find(row => row.id === this.selected()?.id);
      if (selected) this.selected.set(selected); this.busy.set(false);
      const linked = rows.find(row => row.id === this.route.snapshot.queryParamMap.get('quote'));
      if (!this.selected() && linked) this.edit(linked);
    }, error: () => this.fail() });
  }
  newQuote() { if (this.busy()) return; this.selected.set(null); this.form.reset({ title: '', description: '', pricingMode: 'HOURLY_ESTIMATE', estimatedHours: 1, hourlyRateChf: 80, lumpSumChf: 0, variationType: 'PERCENTAGE', variationValue: 0, validUntil: this.defaultDate(), language: this.language.selectedLang(), clientName: '', collaborationName: '', sourceRequestId: '' }); this.email.reset(''); this.error.set(null); this.notice.set(null); }
  edit(row: AdminServiceQuote) {
    if (this.busy()) return;
    this.selected.set(row);
    const content = row.draft ?? row.revisions[0]?.offer.content;
    if (content) this.form.reset({ ...content, estimatedHours: content.estimatedHours ?? 1, hourlyRateChf: content.hourlyRateChf ?? 80, lumpSumChf: content.lumpSumChf ?? 0,
      validUntil: this.localDate(content.validUntil),
      clientName: row.clientName ?? '', collaborationName: row.collaborationName ?? '', sourceRequestId: row.sourceRequestId ?? '' });
    this.email.reset('');
    this.error.set(null); this.notice.set(null);
  }
  fieldError(name: 'title' | 'description') {
    const control = this.form.controls[name];
    return control.touched && control.invalid ? this.translate.instant('SERVICE_QUOTES.FIELD_REQUIRED') : null;
  }
  save() {
    if (this.busy()) return;
    this.form.controls.title.setValue(this.form.controls.title.value.trim());
    this.form.controls.description.setValue(this.form.controls.description.value.trim());
    this.form.markAllAsTouched();
    const value = this.form.getRawValue();
    if (this.form.invalid || !Number.isFinite(Date.parse(value.validUntil))) { this.error.set('SERVICE_QUOTES.ADMIN_INVALID'); return; }
    const payload: QuoteDraftRequest = {
      version: this.selected()?.version ?? 0, clientName: value.clientName.trim(), collaborationName: value.collaborationName.trim(), sourceRequestId: value.sourceRequestId.trim() || null,
      content: { title: value.title.trim(), description: value.description.trim(), pricingMode: value.pricingMode,
        estimatedHours: value.pricingMode === 'HOURLY_ESTIMATE' ? Number(value.estimatedHours) : null,
        hourlyRateChf: value.pricingMode === 'HOURLY_ESTIMATE' ? Number(value.hourlyRateChf) : null,
        lumpSumChf: value.pricingMode === 'LUMP_SUM_ESTIMATE' ? Number(value.lumpSumChf) : null,
        variationType: value.variationType, variationValue: Number(value.variationValue), validUntil: new Date(value.validUntil).toISOString(), language: value.language },
    };
    this.change(this.api.save(this.selected()?.id ?? null, payload));
  }
  publish() { const quote = this.selected(); if (quote?.draft && !this.form.dirty) this.change(this.api.publish(quote.id, quote.version)); }
  revoke(revision: QuoteRevision) { const quote = this.selected(); if (quote) this.change(this.api.revoke(quote.id, revision.offer.revisionId)); }
  private change(operation: Observable<AdminServiceQuote>) {
    if (this.busy()) return;
    this.busy.set(true); this.error.set(null); this.notice.set(null);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: row => {
      this.rows.update(rows => [row, ...rows.filter(other => other.id !== row.id)]); this.busy.set(false); this.edit(row); this.notice.set('SERVICE_QUOTES.SAVED');
    }, error: () => this.fail() });
  }
  invite(revision: QuoteRevision) {
    const quote = this.selected(); if (!quote || this.busy()) return;
    this.email.markAsTouched(); if (this.email.invalid) { this.error.set('SERVICE_QUOTES.INVALID'); return; }
    this.busy.set(true); this.error.set(null);
    this.api.email(quote.id, revision.offer.revisionId, this.email.value).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: result => {
      this.busy.set(false); this.notice.set(result.status === 'SENT' ? 'SERVICE_QUOTES.EMAIL_SENT' : 'SERVICE_QUOTES.EMAIL_PROBLEM'); this.refresh();
    }, error: () => this.fail() });
  }
  copyLink(revision: QuoteRevision) {
    if (typeof navigator === 'undefined' || !navigator.clipboard) return;
    navigator.clipboard.writeText(revision.publicUrl).then(() => this.notice.set('SERVICE_QUOTES.LINK_COPIED')).catch(() => this.error.set('SERVICE_QUOTES.COPY_ERROR'));
  }
  pdf(revision?: QuoteRevision) {
    const quote = this.selected(); if (!quote || this.busy()) return;
    this.file(revision?.acceptance ? this.api.adminPdf(quote.id, revision.acceptance.id) : this.api.preview(quote.id, revision?.offer.revisionId));
  }
  private file(operation: Observable<Blob>) {
    this.busy.set(true); this.error.set(null);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: blob => { downloadBlobInBrowser(blob, `preventivo-${this.selected()?.id}.pdf`); this.busy.set(false); }, error: () => this.fail() });
  }
  retry(revision: QuoteRevision) { const quote = this.selected(); if (quote && revision.acceptance) this.change(this.api.retryPdf(quote.id, revision.acceptance.id)); }
  resend(revision: QuoteRevision) { const quote = this.selected(); if (quote && revision.acceptance) this.change(this.api.resendReceipt(quote.id, revision.acceptance.id)); }
  createJob(revision: QuoteRevision) {
    const quote = this.selected(); if (!quote || !revision.acceptance) return;
    void this.router.navigate([this.language.localizedPath('/admin/cad-invoices')], { queryParams: { serviceQuote: quote.id, revision: revision.offer.revisionId } });
  }
  pricingOptions() { return ['HOURLY_ESTIMATE', 'LUMP_SUM_ESTIMATE'].map(value => ({ value, label: this.translate.instant(`SERVICE_QUOTES.${value}`) })); }
  variationOptions() { return ['PERCENTAGE', 'AMOUNT_CHF'].map(value => ({ value, label: this.translate.instant(`SERVICE_QUOTES.${value}`) })); }
  private defaultDate() { return this.localDate(new Date(Date.now() + 30 * 86400000).toISOString()); }
  private localDate(value: string) { const date = new Date(value); return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16); }
  private fail() { this.busy.set(false); this.error.set('SERVICE_QUOTES.ERROR'); }
}
