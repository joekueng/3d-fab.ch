import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { QuoteEstimatorService } from '../calculator/services/quote-estimator.service';
import { AppInputComponent } from '../../shared/components/app-input/app-input.component';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import {
  AppToggleSelectorComponent,
  ToggleOption,
} from '../../shared/components/app-toggle-selector/app-toggle-selector.component';
import { LanguageService } from '../../core/services/language.service';
import { StlViewerComponent } from '../../shared/components/stl-viewer/stl-viewer.component';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TranslateModule,
    AppInputComponent,
    AppButtonComponent,
    AppCardComponent,
    AppToggleSelectorComponent,
    StlViewerComponent,
  ],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.scss'],
})
export class CheckoutComponent implements OnInit {
  private fb = inject(FormBuilder);
  private quoteService = inject(QuoteEstimatorService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private languageService = inject(LanguageService);

  checkoutForm: FormGroup;
  sessionId: string | null = null;
  loading = false;
  error: string | null = null;
  isSubmitting = signal(false); // Add signal for submit state
  quoteSession = signal<any>(null); // Add signal for session details
  previewFiles = signal<Record<string, File>>({});
  previewLoading = signal<Record<string, boolean>>({});
  previewErrors = signal<Record<string, boolean>>({});
  previewModalOpen = signal(false);
  selectedPreviewFile = signal<File | null>(null);
  selectedPreviewName = signal('');
  selectedPreviewColor = signal('#c9ced6');

  userTypeOptions: ToggleOption[] = [
    { label: 'CONTACT.TYPE_PRIVATE', value: 'PRIVATE' },
    { label: 'CONTACT.TYPE_COMPANY', value: 'BUSINESS' },
  ];

  constructor() {
    this.checkoutForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      phone: ['', Validators.required],
      customerType: ['PRIVATE', Validators.required], // Default to PRIVATE

      shippingSameAsBilling: [true],
      acceptLegal: [false, Validators.requiredTrue],

      billingAddress: this.fb.group({
        firstName: ['', Validators.required],
        lastName: ['', Validators.required],
        companyName: [''],
        referencePerson: [''],
        addressLine1: ['', Validators.required],
        addressLine2: [''],
        zip: ['', Validators.required],
        city: ['', Validators.required],
        countryCode: ['CH', Validators.required],
      }),

      shippingAddress: this.fb.group({
        firstName: [''],
        lastName: [''],
        companyName: [''],
        referencePerson: [''],
        addressLine1: [''],
        addressLine2: [''],
        zip: [''],
        city: [''],
        countryCode: ['CH'],
      }),
    });
  }

  get isCompany(): boolean {
    return this.checkoutForm.get('customerType')?.value === 'BUSINESS';
  }

  setCustomerType(type: string) {
    this.checkoutForm.patchValue({ customerType: type });
    const isCompany = type === 'BUSINESS';

    const billingGroup = this.checkoutForm.get('billingAddress') as FormGroup;
    const companyControl = billingGroup.get('companyName');
    const referenceControl = billingGroup.get('referencePerson');
    const firstNameControl = billingGroup.get('firstName');
    const lastNameControl = billingGroup.get('lastName');

    if (isCompany) {
      companyControl?.setValidators([Validators.required]);
      referenceControl?.setValidators([Validators.required]);
      firstNameControl?.clearValidators();
      lastNameControl?.clearValidators();
    } else {
      companyControl?.clearValidators();
      referenceControl?.clearValidators();
      firstNameControl?.setValidators([Validators.required]);
      lastNameControl?.setValidators([Validators.required]);
    }
    companyControl?.updateValueAndValidity();
    referenceControl?.updateValueAndValidity();
    firstNameControl?.updateValueAndValidity();
    lastNameControl?.updateValueAndValidity();
  }

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      this.sessionId = params['session'];
      if (!this.sessionId) {
        this.error = 'CHECKOUT.ERR_NO_SESSION_START';
        this.router.navigate(['/']); // Redirect if no session
        return;
      }

      this.loadSessionDetails();
    });

    // Toggle shipping validation based on checkbox
    this.checkoutForm
      .get('shippingSameAsBilling')
      ?.valueChanges.subscribe((isSame) => {
        const shippingGroup = this.checkoutForm.get(
          'shippingAddress',
        ) as FormGroup;
        if (isSame) {
          shippingGroup.disable();
        } else {
          shippingGroup.enable();
        }
      });

    // Initial state
    this.checkoutForm.get('shippingAddress')?.disable();
  }

  loadSessionDetails() {
    if (!this.sessionId) return; // Ensure sessionId is present before fetching
    this.quoteService.getQuoteSession(this.sessionId).subscribe({
      next: (session) => {
        this.quoteSession.set(session);
        this.loadStlPreviews(session);
        console.log('Loaded session:', session);
      },
      error: (err) => {
        console.error('Failed to load session', err);
        this.error = 'CHECKOUT.ERR_LOAD_SESSION';
      },
    });
  }

  isCadSession(): boolean {
    return this.quoteSession()?.session?.status === 'CAD_ACTIVE';
  }

  cadRequestId(): string | null {
    return this.quoteSession()?.session?.sourceRequestId ?? null;
  }

  cadHours(): number {
    return this.quoteSession()?.session?.cadHours ?? 0;
  }

  cadTotal(): number {
    return this.quoteSession()?.cadTotalChf ?? 0;
  }

  isStlItem(item: any): boolean {
    const name = String(item?.originalFilename ?? '').toLowerCase();
    return name.endsWith('.stl');
  }

  previewFile(item: any): File | null {
    const id = String(item?.id ?? '');
    if (!id) {
      return null;
    }
    return this.previewFiles()[id] ?? null;
  }

  previewColor(item: any): string {
    const raw = String(item?.colorCode ?? '').trim();
    return raw || '#c9ced6';
  }

  isPreviewLoading(item: any): boolean {
    const id = String(item?.id ?? '');
    if (!id) {
      return false;
    }
    return !!this.previewLoading()[id];
  }

  hasPreviewError(item: any): boolean {
    const id = String(item?.id ?? '');
    if (!id) {
      return false;
    }
    return !!this.previewErrors()[id];
  }

  openPreview(item: any): void {
    const file = this.previewFile(item);
    if (!file) {
      return;
    }
    this.selectedPreviewFile.set(file);
    this.selectedPreviewName.set(String(item?.originalFilename ?? file.name));
    this.selectedPreviewColor.set(this.previewColor(item));
    this.previewModalOpen.set(true);
  }

  closePreview(): void {
    this.previewModalOpen.set(false);
    this.selectedPreviewFile.set(null);
    this.selectedPreviewName.set('');
    this.selectedPreviewColor.set('#c9ced6');
  }

  private loadStlPreviews(session: any): void {
    if (!this.sessionId || !Array.isArray(session?.items)) {
      return;
    }

    for (const item of session.items) {
      if (!this.isStlItem(item)) {
        continue;
      }

      const id = String(item?.id ?? '');
      if (!id || this.previewFiles()[id] || this.previewLoading()[id]) {
        continue;
      }

      this.previewLoading.update((prev) => ({ ...prev, [id]: true }));
      this.previewErrors.update((prev) => ({ ...prev, [id]: false }));

      this.quoteService.getLineItemStlPreview(this.sessionId, id).subscribe({
        next: (blob) => {
          const originalName = String(item?.originalFilename ?? `${id}.stl`);
          const stlName = originalName.toLowerCase().endsWith('.stl')
            ? originalName
            : `${originalName}.stl`;
          const previewFile = new File([blob], stlName, { type: 'model/stl' });
          this.previewFiles.update((prev) => ({ ...prev, [id]: previewFile }));
          this.previewLoading.update((prev) => ({ ...prev, [id]: false }));
        },
        error: () => {
          this.previewErrors.update((prev) => ({ ...prev, [id]: true }));
          this.previewLoading.update((prev) => ({ ...prev, [id]: false }));
        },
      });
    }
  }

  onSubmit() {
    if (this.checkoutForm.invalid) {
      return;
    }

    this.isSubmitting.set(true);
    this.error = null; // Clear previous errors
    const formVal = this.checkoutForm.getRawValue(); // Use getRawValue to include disabled fields

    // Construct request object matching backend DTO based on original form structure
    const orderRequest = {
      customer: {
        email: formVal.email,
        phone: formVal.phone,
        customerType: formVal.customerType,
        // Assuming firstName, lastName, companyName for customer come from billingAddress if not explicitly in contact group
        firstName: formVal.billingAddress.firstName,
        lastName: formVal.billingAddress.lastName,
        companyName: formVal.billingAddress.companyName,
      },
      billingAddress: {
        firstName: formVal.billingAddress.firstName,
        lastName: formVal.billingAddress.lastName,
        companyName: formVal.billingAddress.companyName,
        contactPerson: formVal.billingAddress.referencePerson,
        addressLine1: formVal.billingAddress.addressLine1,
        addressLine2: formVal.billingAddress.addressLine2,
        zip: formVal.billingAddress.zip,
        city: formVal.billingAddress.city,
        countryCode: formVal.billingAddress.countryCode,
      },
      shippingAddress: formVal.shippingSameAsBilling
        ? null
        : {
            firstName: formVal.shippingAddress.firstName,
            lastName: formVal.shippingAddress.lastName,
            companyName: formVal.shippingAddress.companyName,
            contactPerson: formVal.shippingAddress.referencePerson,
            addressLine1: formVal.shippingAddress.addressLine1,
            addressLine2: formVal.shippingAddress.addressLine2,
            zip: formVal.shippingAddress.zip,
            city: formVal.shippingAddress.city,
            countryCode: formVal.shippingAddress.countryCode,
          },
      shippingSameAsBilling: formVal.shippingSameAsBilling,
      language: this.languageService.selectedLang(),
      acceptTerms: formVal.acceptLegal,
      acceptPrivacy: formVal.acceptLegal,
    };

    if (!this.sessionId) {
      this.error = 'CHECKOUT.ERR_NO_SESSION_CREATE_ORDER';
      this.isSubmitting.set(false);
      return;
    }

    this.quoteService.createOrder(this.sessionId, orderRequest).subscribe({
      next: (order) => {
        const orderId = order?.id ?? order?.orderId;
        if (!orderId) {
          this.isSubmitting.set(false);
          this.error = 'CHECKOUT.ERR_CREATE_ORDER';
          return;
        }
        this.router.navigate([
          '/',
          this.languageService.selectedLang(),
          'order',
          orderId,
        ]);
      },
      error: (err) => {
        console.error('Order creation failed', err);
        this.isSubmitting.set(false);
        this.error = 'CHECKOUT.ERR_CREATE_ORDER';
      },
    });
  }
}
