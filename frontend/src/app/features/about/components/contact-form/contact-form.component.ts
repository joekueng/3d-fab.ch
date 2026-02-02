import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';

@Component({
  selector: 'app-contact-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslateModule, AppInputComponent, AppButtonComponent],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()">
      <app-input formControlName="name" label="Name" placeholder="Your Name"></app-input>
      <app-input formControlName="email" type="email" label="Email" placeholder="your@email.com"></app-input>
      
      <div class="form-group">
        <label>Message</label>
        <textarea formControlName="message" class="form-control" rows="4"></textarea>
      </div>

      <div class="actions">
        <app-button type="submit" [disabled]="form.invalid || sent()">
          {{ sent() ? 'Sent!' : ('ABOUT.SEND' | translate) }}
        </app-button>
      </div>
    </form>
  `,
  styles: [`
    .form-group { display: flex; flex-direction: column; margin-bottom: var(--space-4); }
    label { font-size: 0.875rem; font-weight: 500; margin-bottom: var(--space-2); color: var(--color-text); }
    .form-control {
      padding: 0.5rem 0.75rem;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      width: 100%;
      background: var(--color-bg-card);
      color: var(--color-text);
      font-family: inherit;
      &:focus { outline: none; border-color: var(--color-brand); }
    }
  `]
})
export class ContactFormComponent {
  form: FormGroup;
  sent = signal(false);

  constructor(private fb: FormBuilder) {
    this.form = this.fb.group({
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      message: ['', Validators.required]
    });
  }

  onSubmit() {
    if (this.form.valid) {
      // Mock submit
      this.sent.set(true);
      setTimeout(() => {
        this.sent.set(false);
        this.form.reset();
      }, 3000);
    }
  }
}
