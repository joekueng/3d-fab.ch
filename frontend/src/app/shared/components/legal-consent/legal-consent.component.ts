import { Component, inject, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { LanguageService } from '../../../core/services/language.service';
import { AppCheckboxComponent } from '../app-checkbox/app-checkbox.component';

@Component({
  selector: 'app-legal-consent',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, AppCheckboxComponent],
  templateUrl: './legal-consent.component.html',
  styleUrl: './legal-consent.component.scss',
})
export class LegalConsentComponent {
  readonly control = input.required<FormControl>();
  readonly languageService = inject(LanguageService);
}
