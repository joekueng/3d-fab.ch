import {
  Component,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  inject,
  output,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TranslatePipe} from '@ngx-translate/core';
import {Subscription, take} from 'rxjs';
import {AppButtonComponent} from '../../../../shared/components/app-button/app-button.component';
import {AppCheckboxComponent} from '../../../../shared/components/app-checkbox/app-checkbox.component';
import {AppSelectComponent} from '../../../../shared/components/app-select/app-select.component';
import {
  ADMIN_LANGUAGE_LABELS,
  ADMIN_LOCALIZED_LANGUAGES,
  AdminLocalizedLanguage,
} from '../../../../shared/utils/admin-localization.util';
import {
  AdminLocalizedTextFieldPayload,
  AdminTranslateLocalizedTextResponse,
  AdminTranslationService,
} from '../../services/admin-translation.service';

@Component({
  selector: 'app-admin-translate-action',
  standalone: true,
  imports: [
    FormsModule,
    TranslatePipe,
    AppButtonComponent,
    AppCheckboxComponent,
    AppSelectComponent,
  ],
  templateUrl: './admin-translate-action.component.html',
  styleUrl: './admin-translate-action.component.scss',
})
export class AdminTranslateActionComponent implements OnChanges, OnDestroy {
  // Use a draft's identity, such as its names/translations map, to cancel on record changes.
  @Input({required: true}) target!: object;
  @Input({required: true}) context = '';
  @Input({required: true}) fields: Record<
    string,
    AdminLocalizedTextFieldPayload
  > = {};
  @Input() sourceLanguage: AdminLocalizedLanguage = 'it';
  @Input() disabled = false;
  readonly sourceLanguageChange = output<AdminLocalizedLanguage>();
  readonly translated = output<AdminTranslateLocalizedTextResponse>();
  readonly busy = signal(false);
  readonly status = signal<'updated' | 'empty' | 'error' | null>(null);
  overwriteExisting = false;
  readonly languageOptions = ADMIN_LOCALIZED_LANGUAGES.map((value) => ({
    value,
    label: ADMIN_LANGUAGE_LABELS[value],
  }));
  private readonly service = inject(AdminTranslationService);
  private request?: Subscription;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['target']) {
      this.request?.unsubscribe();
      this.busy.set(false);
      this.status.set(null);
      this.overwriteExisting = false;
    }
  }

  ngOnDestroy(): void {
    this.request?.unsubscribe();
  }

  selectSource(language: AdminLocalizedLanguage): void {
    this.sourceLanguage = language;
    this.sourceLanguageChange.emit(language);
  }

  canTranslate(): boolean {
    const fields = Object.values(this.fields);
    const sourceLanguage = this.sourceLanguage;
    return (
      !this.disabled &&
      !this.busy() &&
      fields.length > 0 &&
      fields.every(
        (field) =>
          field.required === false || !!field.values[sourceLanguage].trim(),
      ) &&
      fields.some((field) => !!field.values[sourceLanguage].trim())
    );
  }

  translate(): void {
    if (!this.canTranslate()) return;
    const sourceLanguage = this.sourceLanguage;
    const payload = {
      context: this.context,
      sourceLanguage,
      overwriteExisting: this.overwriteExisting,
      fields: Object.fromEntries(
        Object.entries(this.fields).map(([key, field]) => [
          key,
          {
            // Populated optional fields must also trigger translation when required fields are already complete.
            required:
              field.required !== false || !!field.values[sourceLanguage].trim(),
            values: {...field.values},
          },
        ]),
      ),
    };
    this.busy.set(true);
    this.status.set(null);
    this.request = this.service
      .translateLocalizedText(payload)
      .pipe(take(1))
      .subscribe({
        next: (response) => {
          const fields: AdminTranslateLocalizedTextResponse['fields'] = {};
          for (const [key, requested] of Object.entries(payload.fields)) {
            const current = this.fields[key];
            // Keep edits made while translating, including changes to the source.
            if (
              !current ||
              current.values[payload.sourceLanguage] !==
              requested.values[payload.sourceLanguage]
            )
              continue;
            for (const language of response.targetLanguages) {
              if (language === payload.sourceLanguage) continue;
              const incoming = response.fields[key]?.[language]?.trim();
              if (
                !incoming ||
                current.values[language] !== requested.values[language] ||
                (!payload.overwriteExisting && current.values[language].trim())
              )
                continue;
              (fields[key] ??= {})[language] = incoming;
            }
          }
          this.busy.set(false);
          this.translated.emit({...response, fields});
          this.status.set(Object.keys(fields).length ? 'updated' : 'empty');
        },
        error: () => {
          this.busy.set(false);
          this.status.set('error');
        },
      });
  }
}
