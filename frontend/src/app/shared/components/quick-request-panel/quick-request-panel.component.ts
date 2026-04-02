import {
  Component,
  OnDestroy,
  PLATFORM_ID,
  inject,
  input,
  signal,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AppButtonComponent } from '../app-button/app-button.component';
import { SuccessStateComponent } from '../success-state/success-state.component';
import { LanguageService } from '../../../core/services/language.service';
import {
  ContactRequestDraftContext,
  ContactRequestDraftService,
} from '../../../core/services/contact-request-draft.service';
import { QuoteRequestService } from '../../../core/services/quote-request.service';

interface FilePreview {
  file: File;
  url?: string;
}

@Component({
  selector: 'app-quick-request-panel',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TranslateModule,
    AppButtonComponent,
    SuccessStateComponent,
  ],
  templateUrl: './quick-request-panel.component.html',
  styleUrl: './quick-request-panel.component.scss',
})
export class QuickRequestPanelComponent implements OnDestroy {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly quoteRequestService = inject(QuoteRequestService);
  private readonly draftService = inject(ContactRequestDraftService);
  readonly languageService = inject(LanguageService);

  readonly eyebrowKey = input<string>('');
  readonly titleKey = input.required<string>();
  readonly descriptionKey = input<string>('');
  readonly draftContext = input<ContactRequestDraftContext | null>(null);

  readonly sent = signal(false);
  readonly submitting = signal(false);
  readonly files = signal<FilePreview[]>([]);
  readonly acceptedFormats = '.jpg,.jpeg,.png,.webp,.gif,.bmp,.svg,.heic,.heif';

  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    message: ['', Validators.required],
    acceptLegal: [false, Validators.requiredTrue],
  });

  constructor() {
    const draft = this.draftService.getDraft();
    if (!draft) {
      return;
    }

    this.form.patchValue({
      email: draft.email,
      message: draft.message,
      acceptLegal: draft.acceptLegal,
    });

    if (draft.files.length > 0) {
      this.setFiles(draft.files);
    }
  }

  ngOnDestroy(): void {
    this.revokeAllPreviewUrls();
  }

  get emailError(): string | null {
    const control = this.form.get('email');
    if (!control || !control.touched || !control.invalid) {
      return null;
    }

    if (control.hasError('required') || control.hasError('email')) {
      return this.translate.instant('SHOP.QUICK_REQUEST_EMAIL_ERROR');
    }

    return null;
  }

  get messageError(): string | null {
    const control = this.form.get('message');
    if (!control || !control.touched || !control.invalid) {
      return null;
    }

    if (control.hasError('required')) {
      return this.translate.instant('SHOP.QUICK_REQUEST_MESSAGE_ERROR');
    }

    return null;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) {
      return;
    }

    this.handleFiles(Array.from(input.files));
    input.value = '';
  }

  removeFile(): void {
    this.revokeAllPreviewUrls();
    this.files.set([]);
  }

  openFullContact(): void {
    this.saveDraft();
    void this.router.navigateByUrl(
      `${this.languageService.localizedPath('/contact')}?prefill=shop-quick`,
    );
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    const formValue = this.form.getRawValue();
    const context = this.draftContext();

    this.quoteRequestService
      .createRequest(
        {
          requestType: 'custom',
          customerType: 'PRIVATE',
          language: this.languageService.selectedLang(),
          email: formValue.email || '',
          message: this.draftService.buildSubmittedMessage(
            formValue.message || '',
            context,
          ),
          acceptTerms: Boolean(formValue.acceptLegal),
          acceptPrivacy: Boolean(formValue.acceptLegal),
        },
        this.files().map((file) => file.file),
      )
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.sent.set(true);
          this.draftService.clearDraft();
        },
        error: (err) => {
          console.error('Quick request submission failed', err);
          this.submitting.set(false);
          alert(this.translate.instant('CONTACT.ERROR_SUBMIT'));
        },
      });
  }

  resetForm(): void {
    this.sent.set(false);
    this.form.reset({
      email: '',
      message: '',
      acceptLegal: false,
    });
    this.removeFile();
  }

  private handleFiles(files: File[]): void {
    const firstFile = files[0];
    if (!firstFile) {
      return;
    }

    if (!this.isAcceptedImage(firstFile)) {
      alert(this.translate.instant('SHOP.QUICK_REQUEST_IMAGE_TYPE_ERROR'));
      return;
    }

    if (files.length > 1) {
      alert(this.translate.instant('SHOP.QUICK_REQUEST_IMAGE_LIMIT_ERROR'));
    }

    this.setFiles([firstFile]);
  }

  private setFiles(files: File[]): void {
    this.revokeAllPreviewUrls();

    this.files.set(
      files.slice(0, 1).map((file) => ({
        file,
        url: this.isBrowser ? URL.createObjectURL(file) : undefined,
      })),
    );
  }

  private saveDraft(): void {
    this.draftService.setDraft({
      email: this.form.get('email')?.value || '',
      message: this.form.get('message')?.value || '',
      acceptLegal: Boolean(this.form.get('acceptLegal')?.value),
      files: this.files().map((file) => file.file),
      context: this.draftContext(),
    });
  }

  private isAcceptedImage(file: File): boolean {
    if (file.type.toLowerCase().startsWith('image/')) {
      return true;
    }

    const extensionIndex = file.name.lastIndexOf('.');
    const extension =
      extensionIndex >= 0
        ? file.name.slice(extensionIndex + 1).toLowerCase()
        : '';

    return [
      'jpg',
      'jpeg',
      'png',
      'webp',
      'gif',
      'bmp',
      'svg',
      'heic',
      'heif',
    ].includes(extension);
  }

  private revokeAllPreviewUrls(): void {
    if (!this.isBrowser) {
      return;
    }

    this.files().forEach((file) => {
      if (file.url?.startsWith('blob:')) {
        URL.revokeObjectURL(file.url);
      }
    });
  }
}
