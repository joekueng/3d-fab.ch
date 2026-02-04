import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';


@Component({
  selector: 'app-about-page',
  standalone: true,
  imports: [TranslateModule],
  template: `
    <section class="about-hero">
      <div class="container">
        <p class="eyebrow">{{ 'ABOUT.EYEBROW' | translate }}</p>
        <h1>{{ 'ABOUT.TITLE' | translate }}</h1>
        <p class="subtitle">{{ 'ABOUT.SUBTITLE' | translate }}</p>
      </div>
    </section>

    <div class="container content">
      <div class="info">
        <h2>{{ 'ABOUT.HOW_TITLE' | translate }}</h2>
        <p>{{ 'ABOUT.HOW_TEXT' | translate }}</p>

        <div class="pill-list">
          <span>{{ 'ABOUT.PILL_1' | translate }}</span>
          <span>{{ 'ABOUT.PILL_2' | translate }}</span>
          <span>{{ 'ABOUT.PILL_3' | translate }}</span>
        </div>

        <h3>{{ 'ABOUT.SERVICES_TITLE' | translate }}</h3>
        <ul class="steps">
          <li>{{ 'ABOUT.SERVICE_1' | translate }}</li>
          <li>{{ 'ABOUT.SERVICE_2' | translate }}</li>
          <li>{{ 'ABOUT.SERVICE_3' | translate }}</li>
          <li>{{ 'ABOUT.SERVICE_4' | translate }}</li>
        </ul>

        <h3>{{ 'ABOUT.TARGET_TITLE' | translate }}</h3>
        <p class="text-muted">{{ 'ABOUT.TARGET_TEXT' | translate }}</p>

        <h3>{{ 'ABOUT.TEAM_TITLE' | translate }}</h3>
        <div class="team-grid">
          <div class="team-member">
            <div class="placeholder-img"></div>
            <p>Member 1</p>
          </div>
          <div class="team-member">
            <div class="placeholder-img"></div>
            <p>Member 2</p>
          </div>
          <div class="team-member">
            <div class="placeholder-img"></div>
            <p>Member 3</p>
          </div>
        </div>
      </div>


    </div>
  `,
  styles: [`
    .about-hero {
      padding: 5rem 0 3.5rem;
      background: var(--color-bg);
      text-align: center;
    }
    .eyebrow {
      text-transform: uppercase;
      letter-spacing: 0.12em;
      font-size: 0.75rem;
      color: var(--color-secondary-600);
      font-weight: 600;
      margin-bottom: var(--space-3);
    }
    .subtitle {
      color: var(--color-text-muted);
      max-width: 640px;
      margin: var(--space-3) auto 0;
    }
    .content {
      display: grid;
      gap: var(--space-12);
      padding: 3rem 0 5rem;
      @media(min-width: 768px) { grid-template-columns: 1fr 1fr; }
    }
    .steps {
      padding-left: var(--space-4);
      li { margin-bottom: var(--space-2); color: var(--color-text-muted); }
    }
    .pill-list {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      margin: var(--space-4) 0 var(--space-6);
    }
    .pill-list span {
      padding: 0.35rem 0.75rem;
      border-radius: 999px;
      background: var(--color-neutral-100);
      border: 1px solid var(--color-border);
      font-size: 0.85rem;
      font-weight: 600;
    }
    .text-muted { color: var(--color-text-muted); }
    .team-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
      gap: var(--space-4);
      margin-top: var(--space-4);
    }
    .team-member {
      text-align: center;
    }
    .placeholder-img {
      width: 100%;
      aspect-ratio: 1;
      background: var(--color-neutral-100);
      border-radius: var(--radius-md);
      margin-bottom: var(--space-2);
    }
  `]
})
export class AboutPageComponent {}
