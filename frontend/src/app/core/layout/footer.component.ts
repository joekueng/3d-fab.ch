import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [TranslateModule, RouterLink],
  template: `
    <footer class="footer">
      <div class="container footer-inner">
        <div class="col">
          <span class="brand">PrintCalc</span>
          <p class="copyright">&copy; 2026 Print Calculator Inc.</p>
        </div>
        
        <div class="col links">
          <a routerLink="/privacy">{{ 'FOOTER.PRIVACY' | translate }}</a>
          <a routerLink="/terms">{{ 'FOOTER.TERMS' | translate }}</a>
          <a routerLink="/about">{{ 'FOOTER.CONTACT' | translate }}</a>
        </div>

        <div class="col social">
           <!-- Social Placeholders -->
           <div class="social-icon"></div>
           <div class="social-icon"></div>
           <div class="social-icon"></div>
        </div>
      </div>
    </footer>
  `,
  styles: [`
    .footer {
      background-color: var(--color-neutral-900);
      color: var(--color-neutral-300);
      padding: var(--space-8) 0;
      margin-top: auto; /* Push to bottom if content is short */
    }
    .footer-inner {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .brand { font-weight: 700; color: white; display: block; margin-bottom: var(--space-2); }
    .copyright { font-size: 0.875rem; color: var(--color-secondary-500); margin: 0; }
    
    .links {
      display: flex;
      gap: var(--space-6);
      a {
        color: var(--color-neutral-300);
        font-size: 0.875rem;
        &:hover { color: white; text-decoration: underline; }
      }
    }

    .social { display: flex; gap: var(--space-3); }
    .social-icon {
      width: 24px; height: 24px;
      background-color: var(--color-neutral-800);
      border-radius: 50%;
    }
  `]
})
export class FooterComponent {}
