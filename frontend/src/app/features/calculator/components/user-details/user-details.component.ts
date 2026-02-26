import { Component, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AppCardComponent } from '../../../../shared/components/app-card/app-card.component';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { QuoteResult } from '../../services/quote-estimator.service';

@Component({
  selector: 'app-user-details',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslateModule, AppCardComponent, AppInputComponent, AppButtonComponent],
  templateUrl: './user-details.component.html',
  styleUrl: './user-details.component.scss'
})
export class UserDetailsComponent {
  quote = input<QuoteResult>();
  submitOrder = output<any>();
  cancel = output<void>();

  form: FormGroup;
  submitting = signal(false);

  constructor(private fb: FormBuilder) {
    this.form = this.fb.group({
      name: ['', Validators.required],
      surname: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phone: ['', Validators.required],
      address: ['', Validators.required],
      zip: ['', Validators.required],
      city: ['', Validators.required],
      acceptLegal: [false, Validators.requiredTrue]
    });
  }

  onSubmit() {
    if (this.form.valid) {
      this.submitting.set(true);
      
      const orderData = {
        customer: this.form.value,
        quote: this.quote()
      };

      // Simulate API delay
      setTimeout(() => {
        this.submitOrder.emit(orderData);
        this.submitting.set(false);
      }, 1000);
    } else {
      this.form.markAllAsTouched();
    }
  }

  onCancel() {
    this.cancel.emit();
  }
}
