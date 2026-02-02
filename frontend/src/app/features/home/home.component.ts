import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule, AppButtonComponent, AppCardComponent],
  template: `
    <!-- Hero Section -->
    <section class="hero-section">
      <div class="container hero-content">
        <h1 class="hero-title">
          La Stampa 3D <br>
          <span class="highlight">Professionale e Veloce</span>
        </h1>
        <p class="hero-subtitle">
          Carica il tuo file STL, ottieni un preventivo istantaneo e ricevi i tuoi pezzi in pochi giorni.
          Qualità industriale per prototipi e produzione.
        </p>
        <div class="hero-actions">
          <app-button variant="primary" routerLink="/cal">
            Inizia Preventivo
          </app-button>
          <app-button variant="outline" routerLink="/about">
            Come Funziona
          </app-button>
        </div>
      </div>
    </section>

    <!-- Features / Steps -->
    <section class="section bg-neutral">
      <div class="container">
        <h2 class="section-title text-center">Dal File all'Oggetto in 3 Step</h2>
        
        <div class="grid-3">
          <app-card class="feature-card">
            <div class="step-number">1</div>
            <h3>Carica STL</h3>
            <p class="text-muted">Trascina il tuo modello 3D nel nostro calcolatore intelligente.</p>
          </app-card>

          <app-card class="feature-card">
            <div class="step-number">2</div>
            <h3>Configura</h3>
            <p class="text-muted">Scegli materiale (PLA, PETG, TPU) e qualità di stampa.</p>
          </app-card>

          <app-card class="feature-card">
            <div class="step-number">3</div>
            <h3>Ricevi</h3>
            <p class="text-muted">Produciamo e spediamo in 24/48 ore con corriere espresso.</p>
          </app-card>
        </div>
      </div>
    </section>

    <!-- Shop Teaser -->
    <section class="section">
      <div class="container split-layout">
        <div class="text-content">
          <h2 class="section-title">Non solo Servizio Stampa</h2>
          <p>
            Hai già una stampante? Forniamo i migliori filamenti e accessori testati quotidianamente nella nostra farm.
            Qualità garantita dai nostri esperti.
          </p>
          <app-button variant="secondary" routerLink="/shop">
            Visita lo Shop
          </app-button>
        </div>
        <div class="image-placeholder shop-img">
          <!-- Placeholder visuale per lo shop -->
          <div class="mock-img">🛒</div>
        </div>
      </div>
    </section>

    <!-- Trust / About Teaser -->
    <section class="section bg-dark text-white">
      <div class="container text-center">
        <h2 class="section-title text-white">Perché Sceglierci?</h2>
        <div class="grid-3 stats">
          <div>
            <div class="stat-value">5000+</div>
            <div class="stat-label">Ore di Stampa</div>
          </div>
          <div>
            <div class="stat-value">100%</div>
            <div class="stat-label">Qualità Garantita</div>
          </div>
          <div>
            <div class="stat-value">24h</div>
            <div class="stat-label">Spedizione Rapida</div>
          </div>
        </div>
        <div style="margin-top: 2rem;">
            <app-button variant="outline" routerLink="/about">Chi Siamo</app-button>
        </div>
      </div>
    </section>
  `,
  styles: [`
    .hero-section {
      padding: 8rem 0 6rem;
      background: linear-gradient(to bottom right, var(--color-neutral-50), var(--color-neutral-100));
      text-align: center;
    }
    .hero-content { max-width: 800px; margin: 0 auto; }
    .hero-title {
      font-size: 3rem;
      font-weight: 800;
      margin-bottom: var(--space-4);
      line-height: 1.1;
      letter-spacing: -0.02em;
    }
    .highlight { color: var(--color-brand); }
    .hero-subtitle {
      font-size: 1.25rem;
      color: var(--color-text-muted);
      margin-bottom: var(--space-8);
      max-width: 600px;
      margin-left: auto;
      margin-right: auto;
    }
    .hero-actions {
      display: flex;
      gap: var(--space-4);
      justify-content: center;
    }

    .section { padding: 6rem 0; }
    .bg-neutral { background-color: var(--color-neutral-50); }
    .bg-dark { background-color: var(--color-neutral-900); }
    .text-white { color: white !important; }

    .section-title { font-size: 2rem; margin-bottom: var(--space-8); font-weight: 700; }
    .text-center { text-align: center; }
    .text-muted { color: var(--color-text-muted); }

    .grid-3 {
      display: grid;
      gap: var(--space-8);
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    }

    .step-number {
      font-size: 3rem;
      font-weight: 900;
      color: var(--color-neutral-200);
      margin-bottom: var(--space-2);
      line-height: 1;
    }

    .split-layout {
      display: grid;
      gap: var(--space-12);
      align-items: center;
      @media(min-width: 768px) { grid-template-columns: 1fr 1fr; }
    }

    .mock-img {
      width: 100%;
      height: 300px;
      background-color: var(--color-neutral-100);
      border-radius: var(--radius-lg);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 5rem;
    }

    .stat-value { font-size: 2.5rem; font-weight: 700; color: var(--color-brand); }
    .stat-label { color: var(--color-neutral-300); }
  `]
})
export class HomeComponent {}
