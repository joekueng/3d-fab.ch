import { Component, input } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AppSelectComponent } from '../../../../shared/components/app-select/app-select.component';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { AppCheckboxComponent } from '../../../../shared/components/app-checkbox/app-checkbox.component';
import { SimpleOption } from '../../services/quote-estimator.service';

@Component({
  selector: 'app-print-settings',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    TranslateModule,
    AppSelectComponent,
    AppInputComponent,
    AppCheckboxComponent,
  ],
  templateUrl: './print-settings.component.html',
  styleUrl: './print-settings.component.scss',
})
export class PrintSettingsComponent {
  readonly form = input.required<FormGroup>();
  readonly title = input.required<string>();
  readonly materials = input.required<SimpleOption[]>();
  readonly nozzles = input.required<SimpleOption[]>();
  readonly patterns = input.required<SimpleOption[]>();
  readonly layers = input.required<SimpleOption[]>();
}
