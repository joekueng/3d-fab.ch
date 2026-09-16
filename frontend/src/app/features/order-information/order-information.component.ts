import { LanguageService } from '../../core/services/language.service';
import { CopyOnClickDirective } from '../../shared/directives/copy-on-click.directive';
import { Component, OnChanges, OnDestroy, SimpleChanges, PLATFORM_ID, inject, input, output, signal, effect } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AppTextareaComponent } from '../../shared/components/app-textarea/app-textarea.component';
import { AppSelectComponent } from '../../shared/components/app-select/app-select.component';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { Information, InformationFile, InformationModel, OrderInformationService } from './order-information.service';

@Component({
  selector: 'app-order-information', standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, AppTextareaComponent, AppSelectComponent, AppButtonComponent, AppCardComponent, CopyOnClickDirective],
  templateUrl: './order-information.component.html', styleUrl: './order-information.component.scss',
})
export class OrderInformationComponent implements OnChanges, OnDestroy {
  readonly service = inject(OrderInformationService);
  readonly languageService = inject(LanguageService);
  private readonly translate = inject(TranslateService);
  orderId = input<string | null>(null);
  admin = input(false);
  review = input(false);
  compact = input(false);
  models = input<InformationModel[]>([]);
  read = output<void>();
  readonly information = signal<Information | null>(null);
  readonly expanded = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly confirmation = signal(false);
  readonly previews = signal<Record<string, string>>({});
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private destroyed = false;
  private generation = 0;
  private pendingUrls = new Map<File, string>();
  constructor() {
    effect(() => {
      const files = this.service.attachments();
      if (this.browser && !this.orderId() && this.review()) void this.loadPreviews(files);
    });
  }
  orderText = ''; orderModel = ''; orderFiles: File[] = [];
  get text(): string { return this.orderId() ? this.orderText : this.service.text(); }
  set text(value: string) { if (this.orderId()) this.orderText = value; else this.service.text.set(value); }
  get model(): string { return this.orderId() ? this.orderModel : this.service.model(); }
  set model(value: string) { if (this.orderId()) this.orderModel = value; else { this.service.model.set(value); this.service.modelName.set(this.models().find(m => m.value === value)?.label || ''); } }
  get files(): File[] { return this.orderId() ? this.orderFiles : this.service.files(); }
  get attachments(): InformationFile[] { return this.orderId() ? [] : this.service.attachments(); }
  get disabled(): boolean { return this.saving() || (!!this.orderId() && !this.information()); }
  get modelOptions(): InformationModel[] {
    return [{ label: this.translate.instant('INFORMATION.ALL_MODELS'), value: '' }, ...this.models()];
  }
  get missingModel(): boolean { return !!this.model && !this.models().some(m => m.value === this.model); }
  get summary(): string {
    const notes = this.orderId() ? this.information()?.entries.filter(e => e.text).length || 0 : this.text.trim() ? 1 : 0;
    const files = this.orderId() ? this.information()?.entries.reduce((sum, e) => sum + e.attachments.length, 0) || 0 : this.files.length + this.attachments.length;
    return this.translate.instant('INFORMATION.SUMMARY', { notes: `${notes} ${this.translate.instant(notes === 1 ? 'INFORMATION.NOTE' : 'INFORMATION.NOTES')}`, files: `${files} ${this.translate.instant(files === 1 ? 'INFORMATION.FILE' : 'INFORMATION.FILES')}` });
  }
  async ngOnChanges(changes: SimpleChanges): Promise<void> {
    if (!this.browser || (!changes['orderId'] && !changes['admin'])) return;
    const id = this.orderId(); const generation = ++this.generation;
    this.information.set(null); this.orderText = ''; this.orderModel = ''; this.orderFiles = [];
    this.confirmation.set(false); this.expanded.set(false); this.error.set('');
    if (!id) return;
    try {
      const value = await this.service.getOrder(id, this.admin());
      if (generation !== this.generation || this.destroyed) return;
      this.information.set(value); await this.loadPreviews(value.entries.flatMap(e => e.attachments));
    } catch { if (generation === this.generation) this.error.set('INFORMATION.ACCESS_ERROR'); }
  }
  async toggle(): Promise<void> {
    this.expanded.update(value => !value);
    if (!this.orderId() && this.expanded()) await this.loadPreviews(this.attachments);
  }
  choose(event: Event): void {
    const input = event.target as HTMLInputElement;
    const next = Array.from(input.files || []); input.value = '';
    const existing = this.orderId() ? this.information()?.entries.flatMap(e => e.attachments) || [] : this.attachments;
    if (next.some(f => !['image/jpeg', 'image/png', 'application/pdf'].includes(f.type) || f.size === 0 || f.size > 10 * 1024 * 1024)
      || existing.length + this.files.length + next.length > 10
      || [...existing, ...this.files, ...next].reduce((sum, f) => sum + f.size, 0) > 30 * 1024 * 1024) {
      this.error.set('INFORMATION.LIMITS'); return;
    }
    const combined = [...this.files, ...next];
    if (this.orderId()) this.orderFiles = combined; else this.service.files.set(combined);
    this.error.set('');
  }
  preview(file: File): string {
    if (!this.pendingUrls.has(file)) this.pendingUrls.set(file, URL.createObjectURL(file));
    return this.pendingUrls.get(file)!;
  }
  removeFile(file: File): void {
    const next = this.files.filter(f => f !== file);
    if (this.orderId()) this.orderFiles = next; else this.service.files.set(next);
    const url = this.pendingUrls.get(file); if (url) URL.revokeObjectURL(url); this.pendingUrls.delete(file);
  }
  removeAttachment(id: string): void { this.service.attachments.update(files => files.filter(f => f.id !== id)); }
  async save(): Promise<void> {
    if (this.missingModel) { this.error.set('INFORMATION.MODEL_REMOVED'); return; }
    if (this.text.length > 5000 || this.disabled) { this.error.set('INFORMATION.TEXT_LIMIT'); return; }
    this.saving.set(true); this.error.set('');
    try {
      const id = this.orderId();
      if (id) {
        const value = await this.service.append(id, this.text, this.model, this.models().find(m => m.value === this.model)?.label || '', this.files);
        this.information.set(value); this.orderText = ''; this.orderModel = ''; this.orderFiles = [];
        await this.loadPreviews(value.entries.flatMap(e => e.attachments));
      } else { await this.service.saveDraft(); }
      this.expanded.set(false); this.confirmation.set(true);
    } catch { this.error.set('INFORMATION.SAVE_ERROR'); }
    finally { this.saving.set(false); }
  }
  async markRead(): Promise<void> {
    const id = this.orderId(); if (!id) return;
    this.saving.set(true);
    try {
      this.information.set({ ...await this.service.markRead(id, this.information()?.entries.filter(e => !e.readAt).map(e => e.id) || []), customerToken: this.information()?.customerToken }); this.read.emit();
    } catch { this.error.set('INFORMATION.SAVE_ERROR'); }
    finally { this.saving.set(false); }
  }
  customerLink(): string {
    const token = this.information()?.customerToken;
    const lang = this.translate.getCurrentLang() || 'it';
    return this.browser && token ? `${location.origin}/${lang}/co/${this.orderId()}#informationKey=${token}` : '';
  }
  modelLabel(entry: import('./order-information.service').InformationEntry): string {
    return this.models().find(model => model.value === entry.modelKey)?.label || entry.model || this.translate.instant('INFORMATION.ALL_MODELS');
  }
  hasUnread(): boolean { return this.information()?.entries.some(e => !e.readAt) || false; }
  async download(file: InformationFile): Promise<void> {
    try {
      const blob = await this.service.blob(file, this.orderId(), this.admin());
      const url = URL.createObjectURL(blob); const link = document.createElement('a');
      link.href = url; link.download = file.name; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch { this.error.set('INFORMATION.ACCESS_ERROR'); }
  }
  private async loadPreviews(files: InformationFile[]): Promise<void> {
    const generation = this.generation;
    for (const file of files.filter(f => f.mime.startsWith('image/'))) {
      if (this.previews()[file.id]) continue;
      try {
        const blob = await this.service.blob(file, this.orderId(), this.admin());
        if (this.destroyed || generation !== this.generation) return;
        this.previews.update(value => ({ ...value, [file.id]: URL.createObjectURL(blob) }));
      } catch { this.error.set('INFORMATION.ACCESS_ERROR'); }
    }
  }
  ngOnDestroy(): void {
    this.destroyed = true;
    Object.values(this.previews()).forEach(url => URL.revokeObjectURL(url));
    this.pendingUrls.forEach(url => URL.revokeObjectURL(url));
  }
}
