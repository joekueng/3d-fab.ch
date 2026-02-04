import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';


@Component({
  selector: 'app-about-page',
  standalone: true,
  imports: [TranslateModule],
  template: `
    <section class="about-section">
      <div class="container split-layout">
        
        <!-- Left Column: Content -->
        <div class="text-content">
          <p class="eyebrow">{{ 'ABOUT.EYEBROW' | translate }}</p>
          <h1>{{ 'ABOUT.TITLE' | translate }}</h1>
          <p class="subtitle">{{ 'ABOUT.SUBTITLE' | translate }}</p>
          
          <div class="divider"></div>

          <p class="description">{{ 'ABOUT.HOW_TEXT' | translate }}</p>

          <div class="tags-container">
            <span class="tag">{{ 'ABOUT.PILL_1' | translate }}</span>
            <span class="tag">{{ 'ABOUT.PILL_2' | translate }}</span>
            <span class="tag">{{ 'ABOUT.PILL_3' | translate }}</span>
            <span class="tag">{{ 'ABOUT.SERVICE_1' | translate }}</span>
            <span class="tag">{{ 'ABOUT.SERVICE_2' | translate }}</span>
          </div>
        </div>

        <!-- Right Column: Visuals -->
        <div class="visual-content">
          <div class="photo-card card-1">
            <div class="placeholder-img"></div>
            <div class="member-info">
              <span class="member-name">Member 1</span>
              <span class="member-role">Founder</span>
            </div>
          </div>
          <div class="photo-card card-2">
            <div class="placeholder-img"></div>
            <div class="member-info">
              <span class="member-name">Member 2</span>
              <span class="member-role">Co-Founder</span>
            </div>
          </div>
        </div>

      </div>
    </section>
  `,
  styles: [`
    .about-section {
      padding: 6rem 0;
      background: var(--color-bg);
      min-height: 80vh;
      display: flex;
      align-items: center;
    }

    .split-layout {
      display: grid;
      grid-template-columns: 1fr;
      gap: 4rem;
      align-items: center;
      text-align: center; /* Center on mobile */
      
      @media(min-width: 992px) {
        grid-template-columns: 1fr 1fr;
        gap: 6rem;
        text-align: left; /* Reset to left on desktop */
      }
    }

    /* Left Column */
    .text-content {
      /* text-align: left; Removed to inherit from parent */
    }

    .eyebrow {
      text-transform: uppercase;
      letter-spacing: 0.15em;
      font-size: 0.875rem;
      color: var(--color-primary-500);
      font-weight: 700;
      margin-bottom: var(--space-2);
      display: block;
    }

    h1 {
      font-size: 3rem;
      line-height: 1.1;
      margin-bottom: var(--space-4);
      color: var(--color-text-main);
    }

    .subtitle {
      font-size: 1.25rem;
      color: var(--color-text-muted);
      margin-bottom: var(--space-6);
      font-weight: 300;
    }

    .divider {
      height: 4px;
      width: 60px;
      background: var(--color-primary-500);
      border-radius: 2px;
      margin-bottom: var(--space-6);
      /* Center divider on mobile */
      margin-left: auto;
      margin-right: auto;

      @media(min-width: 992px) {
        margin-left: 0;
        margin-right: 0;
      }
    }

    .description {
      font-size: 1.1rem;
      line-height: 1.7;
      color: var(--color-text-main);
      margin-bottom: var(--space-8);
    }

    .tags-container {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      justify-content: center; /* Center tags on mobile */
      
      @media(min-width: 992px) {
        justify-content: flex-start;
      }
    }

    .tag {
      padding: 0.5rem 1rem;
      border-radius: 99px;
      background: var(--color-surface-card);
      border: 1px solid var(--color-border);
      color: var(--color-text-main);
      font-weight: 500;
      font-size: 0.9rem;
      box-shadow: var(--shadow-sm);
      transition: all 0.2s ease;
    }

    .tag:hover {
      transform: translateY(-2px);
      border-color: var(--color-primary-500);
      color: var(--color-primary-500);
      box-shadow: var(--shadow-md);
    }

    /* Right Column */
    .visual-content {
      display: flex;
      justify-content: center;
      gap: 2rem;
      align-items: center;
      
      /* Mobile: Stacked */
      flex-direction: column;
      
      /* Desktop: Side-by-side */
      @media(min-width: 768px) {
        flex-direction: row;
        flex-wrap: wrap; /* Wrap if necessary but try row */
        align-items: flex-start;
      }
    }

    .photo-card {
      background: var(--color-surface-card);
      padding: 1rem;
      border-radius: var(--radius-lg);
      box-shadow: var(--shadow-lg);
      width: 260px;
      position: relative;
    }

    .placeholder-img {
      width: 100%;
      aspect-ratio: 3/4;
      background: linear-gradient(45deg, var(--color-neutral-200), var(--color-neutral-100));
      border-radius: var(--radius-md);
      margin-bottom: 1rem;
    }

    .member-info {
      text-align: center;
    }

    .member-name {
      display: block;
      font-weight: 700;
      color: var(--color-text-main);
      font-size: 1.1rem;
    }

    .member-role {
      display: block;
      font-size: 0.85rem;
      color: var(--color-text-muted);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      margin-top: 0.25rem;
    }
  `]
})
export class AboutPageComponent {}

