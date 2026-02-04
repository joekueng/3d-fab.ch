import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ContactFormComponent } from './components/contact-form/contact-form.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';

@Component({
  selector: 'app-contact-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, ContactFormComponent, AppCardComponent],
  template: `
    <section class="contact-hero">
      <div class="container">
        <h1>{{ 'CONTACT.TITLE' | translate }}</h1>
        <p class="subtitle">Siamo qui per aiutarti. Compila il modulo sottostante per qualsiasi richiesta.</p>
      </div>
    </section>

    <div class="container content">
      <app-card>
        <app-contact-form></app-contact-form>
      </app-card>
    </div>
  `,
  styles: [`
    .contact-hero {
      padding: 3rem 0 2rem;
      background: var(--color-bg);
      text-align: center;
    }
    .subtitle {
      color: var(--color-text-muted);
      max-width: 640px;
      margin: var(--space-3) auto 0;
    }
    .content {
      padding: 2rem 0 5rem;
      max-width: 800px;
    }
  `]
})
export class ContactPageComponent {}
