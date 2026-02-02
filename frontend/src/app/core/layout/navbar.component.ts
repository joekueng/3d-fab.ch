import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageService } from '../services/language.service';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, TranslateModule],
  template: `
    <header class="navbar">
      <div class="container navbar-inner">
        <a routerLink="/" class="brand">Print<span class="highlight">Calc</span></a>
        
        <nav class="nav-links">
          <a routerLink="/cal" routerLinkActive="active" [routerLinkActiveOptions]="{exact: false}">{{ 'NAV.CALCULATOR' | translate }}</a>
          <a routerLink="/shop" routerLinkActive="active">{{ 'NAV.SHOP' | translate }}</a>
          <a routerLink="/about" routerLinkActive="active">{{ 'NAV.ABOUT' | translate }}</a>
        </nav>

        <div class="actions">
          <button class="lang-switch" (click)="toggleLang()">
            {{ langService.currentLang() === 'it' ? 'EN' : 'IT' }}
          </button>
          
          <div class="icon-placeholder">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
          </div>
        </div>
      </div>
    </header>
  `,
  styles: [`
    .navbar {
      height: 64px;
      border-bottom: 1px solid var(--color-border);
      background-color: var(--color-bg-card);
      position: sticky;
      top: 0;
      z-index: 100;
      display: flex;
      align-items: center;
    }
    .navbar-inner {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .brand {
      font-size: 1.25rem;
      font-weight: 700;
      color: var(--color-text);
      text-decoration: none;
    }
    .highlight { color: var(--color-brand); }
    
    .nav-links {
      display: flex;
      gap: var(--space-6);
      
      a {
        color: var(--color-text-muted);
        font-weight: 500;
        text-decoration: none;
        transition: color 0.2s;
        
        &:hover, &.active {
          color: var(--color-brand);
        }
      }
    }

    .actions {
      display: flex;
      align-items: center;
      gap: var(--space-4);
    }

    .lang-switch {
      background: none;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-sm);
      padding: 2px 6px;
      cursor: pointer;
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--color-text-muted);
      &:hover { color: var(--color-text); border-color: var(--color-text); }
    }

    .icon-placeholder {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background-color: var(--color-neutral-100);
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--color-text-muted);
    }
  `]
})
export class NavbarComponent {
  constructor(public langService: LanguageService) {}

  toggleLang() {
    const newLang = this.langService.currentLang() === 'it' ? 'en' : 'it';
    this.langService.switchLang(newLang);
  }
}
