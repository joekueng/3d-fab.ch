import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageService } from '../services/language.service';
import {routes} from '../../app.routes';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, TranslateModule],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss'],
})
export class NavbarComponent {
  isMenuOpen = false;
  readonly languageOptions: Array<{
    value: 'it' | 'en' | 'de' | 'fr';
    label: string;
  }> = [
    { value: 'it', label: 'IT' },
    { value: 'en', label: 'EN' },
    { value: 'de', label: 'DE' },
    { value: 'fr', label: 'FR' },
  ];

  constructor(public langService: LanguageService) {}

  onLanguageChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const lang = select.value as 'it' | 'en' | 'de' | 'fr';
    this.langService.switchLang(lang);
  }

  toggleMenu() {
    this.isMenuOpen = !this.isMenuOpen;
  }

  closeMenu() {
    this.isMenuOpen = false;
  }

  protected readonly routes = routes;
}
