import { CommonModule } from '@angular/common';
import { Component, input, output } from '@angular/core';
import {
  ADMIN_LANGUAGE_LABELS,
  ADMIN_LOCALIZED_LANGUAGES,
  AdminLanguageStatus,
  AdminLocalizedLanguage,
} from '../../utils/admin-localization.util';

@Component({
  selector: 'app-admin-language-toolbar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-language-toolbar.component.html',
  styleUrl: './admin-language-toolbar.component.scss',
})
export class AdminLanguageToolbarComponent {
  languages = input<readonly AdminLocalizedLanguage[]>(
    ADMIN_LOCALIZED_LANGUAGES,
  );
  activeLanguage = input<AdminLocalizedLanguage>('it');
  labels = input<Partial<Record<AdminLocalizedLanguage, string>>>(
    ADMIN_LANGUAGE_LABELS,
  );
  states = input<Partial<Record<AdminLocalizedLanguage, AdminLanguageStatus>>>(
    {},
  );
  title = input<string>('Lingua editor');
  description = input<string>('');

  activeLanguageChange = output<AdminLocalizedLanguage>();

  labelFor(language: AdminLocalizedLanguage): string {
    return this.labels()[language] ?? language.toUpperCase();
  }

  stateFor(language: AdminLocalizedLanguage): AdminLanguageStatus {
    return this.states()[language] ?? 'empty';
  }

  statusLabel(language: AdminLocalizedLanguage): string {
    const state = this.stateFor(language);
    if (state === 'complete') {
      return 'OK';
    }
    return state === 'incomplete' ? '...' : 'vuoto';
  }

  selectLanguage(language: AdminLocalizedLanguage): void {
    this.activeLanguageChange.emit(language);
  }
}
