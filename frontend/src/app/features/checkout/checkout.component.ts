import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { QuoteEstimatorService } from '../calculator/services/quote-estimator.service';
import { AppInputComponent } from '../../shared/components/app-input/app-input.component';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [
    CommonModule, 
    ReactiveFormsModule, 
    AppInputComponent,
    AppButtonComponent
  ],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.scss']
})
export class CheckoutComponent implements OnInit {
  private fb = inject(FormBuilder);
  private quoteService = inject(QuoteEstimatorService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  checkoutForm: FormGroup;
  sessionId: string | null = null;
  loading = false;
  error: string | null = null;
  isSubmitting = signal(false); // Add signal for submit state
  quoteSession = signal<any>(null); // Add signal for session details

  constructor() {
    this.checkoutForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      phone: ['', Validators.required],
      customerType: ['PRIVATE', Validators.required], // Default to PRIVATE
      
      shippingSameAsBilling: [true],
      
      billingAddress: this.fb.group({
        firstName: ['', Validators.required],
        lastName: ['', Validators.required],
        companyName: [''], 
        addressLine1: ['', Validators.required],
        addressLine2: [''],
        zip: ['', Validators.required],
        city: ['', Validators.required],
        countryCode: ['CH', Validators.required]
      }),
      
      shippingAddress: this.fb.group({
        firstName: [''],
        lastName: [''],
        companyName: [''],
        addressLine1: [''],
        addressLine2: [''],
        zip: [''],
        city: [''],
        countryCode: ['CH']
      })
    });
  }

  get isCompany(): boolean {
    return this.checkoutForm.get('customerType')?.value === 'BUSINESS';
  }

  setCustomerType(isCompany: boolean) {
    const type = isCompany ? 'BUSINESS' : 'PRIVATE';
    this.checkoutForm.patchValue({ customerType: type });
    
    // Update validators based on type
    const billingGroup = this.checkoutForm.get('billingAddress') as FormGroup;
    const companyControl = billingGroup.get('companyName');
    
    if (isCompany) {
      companyControl?.setValidators([Validators.required]);
    } else {
      companyControl?.clearValidators();
    }
    companyControl?.updateValueAndValidity();
  }

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.sessionId = params['session'];
      if (!this.sessionId) {
        this.error = 'No active session found. Please start a new quote.';
        this.router.navigate(['/']); // Redirect if no session
        return;
      }
      
      this.loadSessionDetails();
    });

    // Toggle shipping validation based on checkbox
    this.checkoutForm.get('shippingSameAsBilling')?.valueChanges.subscribe(isSame => {
      const shippingGroup = this.checkoutForm.get('shippingAddress') as FormGroup;
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
              console.log('Loaded session:', session);
          },
          error: (err) => {
            console.error('Failed to load session', err);
            this.error = 'Failed to load session details. Please try again.';
          }
      });
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
        companyName: formVal.billingAddress.companyName
      },
      billingAddress: {
        firstName: formVal.billingAddress.firstName,
        lastName: formVal.billingAddress.lastName,
        companyName: formVal.billingAddress.companyName,
        addressLine1: formVal.billingAddress.addressLine1,
        addressLine2: formVal.billingAddress.addressLine2,
        zip: formVal.billingAddress.zip,
        city: formVal.billingAddress.city,
        countryCode: formVal.billingAddress.countryCode
      },
      shippingAddress: formVal.shippingSameAsBilling ? null : {
        firstName: formVal.shippingAddress.firstName,
        lastName: formVal.shippingAddress.lastName,
        companyName: formVal.shippingAddress.companyName,
        addressLine1: formVal.shippingAddress.addressLine1,
        addressLine2: formVal.shippingAddress.addressLine2,
        zip: formVal.shippingAddress.zip,
        city: formVal.shippingAddress.city,
        countryCode: formVal.shippingAddress.countryCode
      },
      shippingSameAsBilling: formVal.shippingSameAsBilling
    };

    if (!this.sessionId) {
      this.error = 'No active session found. Cannot create order.';
      this.isSubmitting.set(false);
      return;
    }

    this.quoteService.createOrder(this.sessionId, orderRequest).subscribe({
      next: (order) => {
        console.log('Order created', order);
        this.router.navigate(['/payment', order.id]);
      },
      error: (err) => {
        console.error('Order creation failed', err);
        this.isSubmitting.set(false);
        this.error = 'Failed to create order. Please try again.';
      }
    });
  }
}
