import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { RouterLink } from '@angular/router';
import { LanguageService } from '../../../core/services/language.service';
import {
  AppToggleSelectorComponent,
  ToggleOption,
} from '../app-toggle-selector/app-toggle-selector.component';

@Component({
  selector: 'app-locations',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    RouterLink,
    AppToggleSelectorComponent,
  ],
  templateUrl: './app-locations.component.html',
  styleUrl: './app-locations.component.scss',
})
export class AppLocationsComponent {
  readonly languageService = inject(LanguageService);
  selectedLocation: 'ticino' | 'bienne' = 'ticino';

  locationOptions: ToggleOption[] = [
    { label: 'LOCATIONS.TICINO', value: 'ticino' },
    { label: 'LOCATIONS.BIENNE', value: 'bienne' },
  ];

  selectLocation(location: any) {
    this.selectedLocation = location;
  }
}
