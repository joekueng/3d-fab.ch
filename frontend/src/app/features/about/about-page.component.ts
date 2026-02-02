import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { ContactFormComponent } from './components/contact-form/contact-form.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';

@Component({
  selector: 'app-about-page',
  standalone: true,
  imports: [TranslateModule, ContactFormComponent, AppCardComponent],
  template: `
    <div class="container hero">
      <h1>{{ 'ABOUT.TITLE' | translate }}</h1>
    </div>

    <div class="container content">
      <div class="info">
        <h2>Our Mission</h2>
        <p>
          We make high-quality 3D printing accessible to everyone. 
          Whether you are a hobbyist or an industrial designer, 
          PrintCalc provides instant quotes and professional production.
        </p>

        <h3>How it Works</h3>
        <ol class="steps">
          <li>Upload your STL file</li>
          <li>Choose material & quality</li>
          <li>Get instant quote</li>
          <li>We print & ship</li>
        </ol>
      </div>

      <div class="contact">
        <app-card>
          <h2>{{ 'ABOUT.CONTACT_US' | translate }}</h2>
          <app-contact-form></app-contact-form>
        </app-card>
      </div>
    </div>
  `,
  styles: [`
    .hero { padding: var(--space-8) 0; text-align: center; }
    .content {
      display: grid;
      gap: var(--space-12);
      @media(min-width: 768px) { grid-template-columns: 1fr 1fr; }
    }
    .steps {
      padding-left: var(--space-4);
      li { margin-bottom: var(--space-2); color: var(--color-text-muted); }
    }
  `]
})
export class AboutPageComponent {}
