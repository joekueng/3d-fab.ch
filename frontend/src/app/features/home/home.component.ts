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
    <main class="home-page">
      <section class="hero">
        <div class="container hero-grid">
          <div class="hero-copy">
            <p class="eyebrow">Stampa 3D tecnica per aziende, freelance e maker</p>
            <h1 class="hero-title">
              Prezzo e tempi in pochi secondi.<br>
              Dal file 3D al pezzo finito.
            </h1>
            <p class="hero-subtitle">
              Lavoriamo con trasparenza su costi, qualità e tempi. Produciamo prototipi, pezzi personalizzati
              e piccole serie con supporto tecnico reale.
            </p>
            <div class="hero-actions">
              <app-button variant="primary" routerLink="/about">Parla con noi</app-button>
              <app-button variant="outline" routerLink="/shop">Vai allo shop</app-button>
            </div>
            <div class="hero-badges">
              <span>Trasparenza su prezzo, qualità e tempi</span>
              <span>Prototipi e piccole serie fino a 500 pz</span>
              <span>Progettazione CAD e post-processing su richiesta</span>
            </div>
          </div>

          <div class="hero-panel">
            <app-card class="focus-card">
              <p class="quote-eyebrow">Approccio consulenziale</p>
              <h3 class="quote-title">Dall'idea al file 3D</h3>
              <p class="text-muted">
                Se non hai il modello, ti supportiamo nella progettazione e nella scelta delle soluzioni
                più adatte al tuo prodotto.
              </p>
              <ul class="focus-list">
                <li>Consulenza tecnica rapida</li>
                <li>Ottimizzazione file per stampa</li>
                <li>Verifica fattibilità e costi</li>
              </ul>
              <app-button variant="outline" [fullWidth]="true" routerLink="/about">Richiedi consulenza</app-button>
            </app-card>
          </div>
        </div>
      </section>

      <section class="section calculator">
        <div class="container calculator-grid">
          <div class="calculator-copy">
            <h2 class="section-title">Preventivo immediato</h2>
            <p class="section-subtitle">
              Carica il file 3D e ottieni subito costo e tempo di stampa. Nessuna registrazione.
            </p>
            <ul class="calculator-list">
              <li>Formati supportati: STL, 3MF, STEP, OBJ</li>
              <li>Materiali disponibili: PLA, PETG, TPU</li>
              <li>Qualità: bozza, standard, alta definizione</li>
            </ul>
          </div>
          <app-card class="quote-card">
            <div class="quote-header">
              <div>
                <p class="quote-eyebrow">Calcolo automatico</p>
                <h3 class="quote-title">Prezzo e tempi in un click</h3>
              </div>
              <span class="quote-tag">Senza registrazione</span>
            </div>
            <ul class="quote-steps">
              <li>Carica il file 3D</li>
              <li>Scegli materiale e qualità</li>
              <li>Ricevi subito costo e tempo</li>
            </ul>
            <div class="quote-meta">
              <div>
                <span class="meta-label">Modalità</span>
                <span class="meta-value">Rapida / Avanzata</span>
              </div>
              <div>
                <span class="meta-label">Output</span>
                <span class="meta-value">Ordina o richiedi consulenza</span>
              </div>
            </div>
            <div class="quote-actions">
              <app-button variant="primary" [fullWidth]="true" routerLink="/cal">Apri calcolatore</app-button>
              <app-button variant="outline" [fullWidth]="true" routerLink="/about">Parla con noi</app-button>
            </div>
          </app-card>
        </div>
      </section>

      <section class="section capabilities">
        <div class="container">
          <div class="section-head">
            <h2 class="section-title">Cosa puoi ottenere</h2>
            <p class="section-subtitle">
              Produzione su misura per prototipi, piccole serie e pezzi personalizzati.
            </p>
          </div>
          <div class="cap-cards">
            <app-card>
              <h3>Prototipazione veloce</h3>
              <p class="text-muted">Valida idee e funzioni in pochi giorni con preventivo immediato.</p>
            </app-card>
            <app-card>
              <h3>Pezzi personalizzati</h3>
              <p class="text-muted">Componenti unici o in mini serie per clienti, macchine e prodotti.</p>
            </app-card>
            <app-card>
              <h3>Piccole serie</h3>
              <p class="text-muted">Produzione controllata fino a 500 pezzi con qualità costante.</p>
            </app-card>
            <app-card>
              <h3>Consulenza e CAD</h3>
              <p class="text-muted">Supporto tecnico per progettazione, modifiche e ottimizzazione.</p>
            </app-card>
          </div>
        </div>
      </section>

      <section class="section shop">
        <div class="container split">
          <div class="shop-copy">
            <h2 class="section-title">Shop di soluzioni tecniche pronte</h2>
            <p>
              Prodotti selezionati, testati in laboratorio e pronti all'uso. Risolvono problemi reali con
              funzionalità concrete.
            </p>
            <ul class="shop-list">
              <li>Accessori funzionali per officine e laboratori</li>
              <li>Ricambi e componenti difficili da reperire</li>
              <li>Supporti e organizzatori per migliorare i flussi di lavoro</li>
            </ul>
            <div class="shop-actions">
              <app-button variant="primary" routerLink="/shop">Scopri i prodotti</app-button>
              <app-button variant="outline" routerLink="/about">Richiedi una soluzione</app-button>
            </div>
          </div>
          <div class="shop-cards">
            <app-card>
              <h3>Best seller tecnici</h3>
              <p class="text-muted">Soluzioni provate sul campo e già pronte alla spedizione.</p>
            </app-card>
            <app-card>
              <h3>Kit pronti all'uso</h3>
              <p class="text-muted">Componenti compatibili e facili da montare senza sorprese.</p>
            </app-card>
            <app-card>
              <h3>Su richiesta</h3>
              <p class="text-muted">Non trovi quello che serve? Lo progettiamo e lo produciamo per te.</p>
            </app-card>
          </div>
        </div>
      </section>

      <section class="section about">
        <div class="container about-grid">
          <div class="about-copy">
            <h2 class="section-title">Su di noi</h2>
            <p>
              3D fab è un laboratorio tecnico di stampa 3D. Seguiamo progetti dalla consulenza iniziale
              alla produzione, con tempi chiari e supporto diretto.
            </p>
            <p class="text-muted">
              Qui puoi inserire descrizioni più dettagliate del team, del laboratorio e dei progetti in corso.
            </p>
            <app-button variant="outline" routerLink="/about">Contattaci</app-button>
          </div>
          <div class="about-media">
            <div class="media-grid">
              <div class="media-tile">
                <div class="media-photo"></div>
                <p>Foto laboratorio / stampanti</p>
              </div>
              <div class="media-tile">
                <div class="media-photo"></div>
                <p>Dettagli qualità e finiture</p>
              </div>
              <div class="media-tile">
                <div class="media-photo"></div>
                <p>Team, prototipi o casi studio</p>
              </div>
            </div>
            <app-card class="about-note">
              <h3>Spazio per descrizioni</h3>
              <p class="text-muted">
                Inserisci qui testi più lunghi, riferimenti a clienti o processi interni.
              </p>
            </app-card>
          </div>
        </div>
      </section>
    </main>
  `,
  styles: [`
    .home-page {
      background: var(--color-bg);
    }

    .hero {
      position: relative;
      padding: 6rem 0 5rem;
      overflow: hidden;
      background: var(--color-bg);
    }
    .hero::before {
      content: '';
      position: absolute;
      width: 420px;
      height: 420px;
      right: -120px;
      top: -160px;
      background: radial-gradient(circle at 30% 30%, rgba(0, 0, 0, 0.03), transparent 70%);
      opacity: 0.8;
      z-index: 0;
      animation: floatGlow 12s ease-in-out infinite;
    }
    .hero::after {
      content: '';
      position: absolute;
      inset: 0;
      background-image:
        linear-gradient(rgba(16, 24, 32, 0.05) 1px, transparent 1px),
        linear-gradient(90deg, rgba(16, 24, 32, 0.05) 1px, transparent 1px);
      background-size: 32px 32px;
      opacity: 0.25;
      z-index: 0;
      pointer-events: none;
    }
    .hero-grid {
      display: grid;
      gap: var(--space-12);
      align-items: center;
      position: relative;
      z-index: 1;
    }
    .hero-copy { animation: fadeUp 0.8s ease both; }
    .hero-panel { animation: fadeUp 0.8s ease 0.15s both; }

    .eyebrow {
      text-transform: uppercase;
      letter-spacing: 0.12em;
      font-size: 0.75rem;
      color: var(--color-secondary-600);
      margin-bottom: var(--space-3);
      font-weight: 600;
    }
    .hero-title {
      font-size: clamp(2.5rem, 2.4vw + 1.8rem, 4rem);
      font-weight: 700;
      line-height: 1.05;
      letter-spacing: -0.02em;
      margin-bottom: var(--space-4);
    }
    .hero-subtitle {
      font-size: 1.2rem;
      color: var(--color-text-muted);
      max-width: 560px;
    }
    .hero-actions {
      display: flex;
      gap: var(--space-4);
      flex-wrap: wrap;
      margin: var(--space-6) 0 var(--space-4);
    }
    .hero-badges {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
    }
    .hero-badges span {
      display: inline-flex;
      padding: 0.35rem 0.75rem;
      border-radius: 999px;
      background: var(--color-neutral-100);
      color: var(--color-neutral-900);
      font-size: 0.85rem;
      font-weight: 600;
      border: 1px solid var(--color-border);
    }

    .quote-card {
      display: block;
    }
    .focus-card {
      display: grid;
      gap: var(--space-4);
    }
    .focus-list {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: var(--space-2);
      color: var(--color-text-muted);
    }
    .focus-list li::before {
      content: '•';
      color: var(--color-brand);
      margin-right: var(--space-2);
    }
    .focus-list li {
      display: flex;
      align-items: baseline;
      gap: var(--space-2);
    }
    .quote-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: var(--space-4);
      margin-bottom: var(--space-4);
    }
    .quote-eyebrow {
      text-transform: uppercase;
      font-size: 0.7rem;
      letter-spacing: 0.12em;
      color: var(--color-secondary-600);
      margin: 0 0 var(--space-2);
    }
    .quote-title { margin: 0; font-size: 1.35rem; }
    .quote-tag {
      background: var(--color-neutral-100);
      border: 1px solid var(--color-border);
      border-radius: 999px;
      padding: 0.35rem 0.75rem;
      font-size: 0.8rem;
      font-weight: 600;
    }
    .quote-steps {
      list-style: none;
      padding: 0;
      margin: 0 0 var(--space-5);
      display: grid;
      gap: var(--space-2);
    }
    .quote-steps li {
      position: relative;
      padding-left: 1.5rem;
      color: var(--color-text-muted);
    }
    .quote-steps li::before {
      content: '';
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--color-brand);
      position: absolute;
      left: 0.25rem;
      top: 0.5rem;
    }
    .quote-meta {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--space-4);
      margin-bottom: var(--space-5);
    }
    .meta-label {
      display: block;
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--color-secondary-600);
      margin-bottom: var(--space-1);
    }
    .meta-value { font-weight: 600; }
    .quote-actions { display: grid; gap: var(--space-3); }

    .section { padding: 5.5rem 0; position: relative; }
    .section-head { margin-bottom: var(--space-8); }
    .section-title { font-size: clamp(2rem, 1.8vw + 1.2rem, 2.8rem); margin-bottom: var(--space-3); }
    .section-subtitle { color: var(--color-text-muted); max-width: 620px; }
    .text-muted { color: var(--color-text-muted); }

    .calculator {
      background: var(--color-neutral-50);
      border-top: 1px solid var(--color-border);
      border-bottom: 1px solid var(--color-border);
    }
    .calculator-grid {
      display: grid;
      gap: var(--space-10);
      align-items: center;
    }
    .calculator-list {
      padding-left: var(--space-4);
      color: var(--color-text-muted);
      margin: var(--space-6) 0 0;
    }
    .cap-cards {
      display: grid;
      gap: var(--space-4);
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    }

    .shop {
      background: var(--color-neutral-50);
    }
    .split {
      display: grid;
      gap: var(--space-10);
      align-items: center;
    }
    .shop-list {
      padding-left: var(--space-4);
      color: var(--color-text-muted);
      margin-bottom: var(--space-6);
    }
    .shop-actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-3);
    }
    .shop-cards {
      display: grid;
      gap: var(--space-4);
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    }

    .about {
      background: var(--color-neutral-50);
      border-top: 1px solid var(--color-border);
    }
    .about-grid {
      display: grid;
      gap: var(--space-10);
      align-items: center;
    }
    .about-media {
      display: grid;
      gap: var(--space-4);
    }
    .media-grid {
      display: grid;
      gap: var(--space-4);
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    }
    .media-tile {
      display: grid;
      gap: var(--space-2);
    }
    .media-photo {
      width: 100%;
      aspect-ratio: 4 / 3;
      border-radius: var(--radius-lg);
      background: var(--color-neutral-100);
      border: 1px solid var(--color-border);
    }
    .media-tile p {
      margin: 0;
      color: var(--color-text-muted);
      font-size: 0.9rem;
    }
    .about-note {
      padding: var(--space-5);
    }

    @media (min-width: 960px) {
      .hero-grid { grid-template-columns: 1.1fr 0.9fr; }
      .calculator-grid { grid-template-columns: 1.1fr 0.9fr; }
      .split { grid-template-columns: 1.1fr 0.9fr; }
      .about-grid { grid-template-columns: 1.1fr 0.9fr; }
    }

    @media (max-width: 640px) {
      .hero-actions { flex-direction: column; align-items: stretch; }
      .quote-meta { grid-template-columns: 1fr; }
    }

    @keyframes fadeUp {
      from { opacity: 0; transform: translateY(18px); }
      to { opacity: 1; transform: translateY(0); }
    }
    @keyframes floatGlow {
      0%, 100% { transform: translateY(0); }
      50% { transform: translateY(20px); }
    }

    @media (prefers-reduced-motion: reduce) {
      .hero-copy, .hero-panel { animation: none; }
      .hero::before { animation: none; }
    }
  `]
})
export class HomeComponent {}
