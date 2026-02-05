import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageService } from '../services/language.service';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, TranslateModule],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss']
})
export class NavbarComponent {
  isMenuOpen = false;

  constructor(public langService: LanguageService) {}

  toggleLang() {
    const newLang = this.langService.currentLang() === 'it' ? 'en' : 'it';
    this.langService.switchLang(newLang);
  }

  toggleMenu() {
    this.isMenuOpen = !this.isMenuOpen;
  }

  closeMenu() {
    this.isMenuOpen = false;
  }
}
