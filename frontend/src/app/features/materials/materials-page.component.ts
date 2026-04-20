import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  PublicMediaDisplayImage,
  PublicMediaService,
  PublicMediaUsageCollectionMap,
  buildPublicMediaUsageScopeKey,
} from '../../core/services/public-media.service';
import { LanguageService } from '../../core/services/language.service';

interface MaterialSource {
  label: string;
  kind: 'Wikipedia' | 'Scheda tecnica' | 'Scheda prodotto';
  url: string;
}

interface MaterialMetrics {
  priceChfKg: number;
  densityGcm3: number;
  tensileMpa: number;
  modulusGpa: number;
  elongationPct: number;
  hdtC: number;
  extrusionC: string;
  printability: number;
  layerRangeMm: string;
}

interface MaterialRecord {
  id: string;
  name: string;
  summary: string;
  qualityTips: readonly string[];
  metrics: MaterialMetrics;
  pros: readonly string[];
  cons: readonly string[];
  idealFor: readonly string[];
  sources: readonly MaterialSource[];
}

type RadarAxisId =
  | 'economy'
  | 'printability'
  | 'tensile'
  | 'modulus'
  | 'elongation'
  | 'hdt';

interface RadarAxis {
  id: RadarAxisId;
  label: string;
  chartLabelLines?: readonly string[];
  description: string;
  unit: string;
  lowerIsBetter?: boolean;
  accessor: (material: MaterialRecord) => number;
}

interface RadarPoint {
  axis: RadarAxis;
  score: number;
  rawValue: number;
  x: number;
  y: number;
}

interface RadarSeries {
  material: MaterialRecord;
  color: string;
  fill: string;
  points: string;
  values: readonly RadarPoint[];
}

interface AxisGuide {
  id: RadarAxisId;
  fromX: number;
  fromY: number;
  x: number;
  y: number;
  labelX: number;
  labelY: number;
  labelAnchor: 'start' | 'middle' | 'end';
  labelLines: readonly string[];
}

interface ComparisonRow {
  label: string;
  values: readonly string[];
}

interface CalculatorFact {
  title: string;
  description: string;
  detailsTitle?: string;
  details?: readonly string[];
  ctaLabel?: string;
  path?: string;
}

interface CalculatorParameter {
  title: string;
  availability: 'Base' | 'Avanzata' | 'Base e Avanzata';
  explanation: string;
  calculatorEffect: string;
}

interface QualityVisualGuide {
  id: string;
  category: 'Layer' | 'Ugello' | 'Riempimento';
  title: string;
  objectExample: string;
  bestFor: string;
  tradeoff: string;
  calculatorRead: string;
  usageKey: string;
}

interface QualityVisualCard extends QualityVisualGuide {
  image: PublicMediaDisplayImage | null;
}

interface MaterialSeriesStyle {
  stroke: string;
  fill: string;
}

const MATERIALS: readonly MaterialRecord[] = [
  {
    id: 'tpu-95a-hf',
    name: 'TPU 95A HF',
    summary:
      'Materiale molto flessibile, utile quando serve assorbire urti e vibrazioni.',
    qualityTips: [
      'Riduci velocita e accelerazioni per stabilita estrusione.',
      'Usa layer medio-alto per ridurre artefatti su superfici morbide.',
    ],
    metrics: {
      priceChfKg: 30,
      densityGcm3: 1.22,
      tensileMpa: 27,
      modulusGpa: 0.01,
      elongationPct: 650,
      hdtC: 0,
      extrusionC: '220 - 240',
      printability: 70,
      layerRangeMm: '0.20 - 0.32',
    },
    pros: [
      'Altissima flessibilita e resilienza agli urti.',
      'Buona adesione tra layer su geometrie morbide.',
    ],
    cons: [
      'Sensibile all umidita durante stampa e conservazione.',
      'Rigidita molto bassa e resistenza termica limitata.',
    ],
    idealFor: ['Guarnizioni', 'Bumper', 'Grip', 'Cover antiurto'],
    sources: [
      {
        label: 'Wikipedia - Thermoplastic polyurethane',
        kind: 'Wikipedia',
        url: 'https://en.wikipedia.org/wiki/Thermoplastic_polyurethane',
      },
      {
        label: 'Bambu Lab - TPU 95A HF',
        kind: 'Scheda prodotto',
        url: 'https://eu.store.bambulab.com/it/products/tpu-95a-hf',
      },
      {
        label: 'Ultimaker - TPU 95A',
        kind: 'Scheda tecnica',
        url: 'https://ultimaker.com/materials/s-series-tpu-95a/',
      },
    ],
  },
  {
    id: 'pla-basic',
    name: 'PLA Basic',
    summary:
      'Scelta semplice per prototipi rapidi con buona finitura e stabilita di stampa.',
    qualityTips: [
      'Per qualita visuale usa layer 0.12-0.16 mm.',
      'Per produttivita usa layer 0.20-0.24 mm.',
    ],
    metrics: {
      priceChfKg: 18,
      densityGcm3: 1.24,
      tensileMpa: 35,
      modulusGpa: 2.58,
      elongationPct: 12,
      hdtC: 57,
      extrusionC: '190 - 230',
      printability: 96,
      layerRangeMm: '0.12 - 0.24',
    },
    pros: [
      'Facile da stampare.',
      'Ottima qualita superficiale su pezzi visuali.',
    ],
    cons: [
      'Fragile sotto urto.',
      'Scarsa resistenza al calore e all UV prolungato.',
    ],
    idealFor: ['Mockup', 'Prototipi rapidi', 'Pezzi estetici indoor'],
    sources: [
      {
        label: 'Wikipedia - Polylactic acid',
        kind: 'Wikipedia',
        url: 'https://en.wikipedia.org/wiki/Polylactic_acid',
      },
      {
        label: 'Bambu Lab - PLA Basic',
        kind: 'Scheda prodotto',
        url: 'https://eu.store.bambulab.com/it/products/pla-basic-filament',
      },
      {
        label: 'Ultimaker - PLA',
        kind: 'Scheda tecnica',
        url: 'https://ultimaker.com/materials/pla/',
      },
    ],
  },
  {
    id: 'pla-matte',
    name: 'PLA Matte',
    summary:
      'PLA con resa opaca per priorita estetica su modelli e parti espositive.',
    qualityTips: [
      'Layer 0.16-0.24 mm mantiene effetto opaco uniforme.',
      'Raffreddamento costante aiuta la texture superficiale.',
    ],
    metrics: {
      priceChfKg: 18,
      densityGcm3: 1.31,
      tensileMpa: 30,
      modulusGpa: 1.96,
      elongationPct: 15,
      hdtC: 58,
      extrusionC: '190 - 230',
      printability: 94,
      layerRangeMm: '0.16 - 0.24',
    },
    pros: ['Aspetto opaco pulito.', 'Stampa semplice come il PLA base.'],
    cons: [
      'Proprieta meccaniche limitate per uso strutturale.',
      'Sensibile al calore come altri PLA.',
    ],
    idealFor: ['Oggetti espositivi', 'Design prodotto', 'Mockup estetici'],
    sources: [
      {
        label: 'Wikipedia - Polylactic acid',
        kind: 'Wikipedia',
        url: 'https://en.wikipedia.org/wiki/Polylactic_acid',
      },
      {
        label: 'Bambu Lab - PLA Matte',
        kind: 'Scheda prodotto',
        url: 'https://eu.store.bambulab.com/it/products/pla-matte',
      },
    ],
  },
  {
    id: 'pla-tough-plus',
    name: 'PLA Tough+',
    summary:
      'Compromesso tra facilita del PLA e maggiore tenacita per uso funzionale leggero.',
    qualityTips: [
      'Layer 0.20 mm e un buon compromesso velocita/precisione.',
      'Usa 3-4 perimetri per aumentare robustezza in urto.',
    ],
    metrics: {
      priceChfKg: 22,
      densityGcm3: 1.21,
      tensileMpa: 35,
      modulusGpa: 1.86,
      elongationPct: 9,
      hdtC: 61,
      extrusionC: '220 - 250',
      printability: 89,
      layerRangeMm: '0.16 - 0.24',
    },
    pros: [
      'Maggiore tenacita rispetto al PLA standard.',
      'Buon compromesso per prototipi funzionali.',
    ],
    cons: ['Resistenza termica ancora moderata.', 'Meno rigido del PLA basic.'],
    idealFor: [
      'Pezzi funzionali leggeri',
      'Attrezzi non strutturali',
      'Componenti test',
    ],
    sources: [
      {
        label: 'Wikipedia - Polylactic acid',
        kind: 'Wikipedia',
        url: 'https://en.wikipedia.org/wiki/Polylactic_acid',
      },
      {
        label: 'Bambu Lab - PLA Tough',
        kind: 'Scheda prodotto',
        url: 'https://eu.store.bambulab.com/it/products/pla-tough-upgrade',
      },
    ],
  },
  {
    id: 'asa',
    name: 'ASA',
    summary:
      'Polimero tecnico per esterno, piu stabile agli UV rispetto ad ABS.',
    qualityTips: [
      'Camera chiusa e brim riducono warping.',
      'Layer 0.20-0.28 mm per bilanciare adesione e tempi.',
    ],
    metrics: {
      priceChfKg: 23,
      densityGcm3: 1.07,
      tensileMpa: 45,
      modulusGpa: 2.1,
      elongationPct: 10,
      hdtC: 95,
      extrusionC: '240 - 260',
      printability: 64,
      layerRangeMm: '0.20 - 0.28',
    },
    pros: [
      'Buona resistenza UV e intemperie.',
      'Adatto a componenti outdoor funzionali.',
    ],
    cons: [
      'Richiede controllo termico in stampa.',
      'Puo warpare senza setup adeguato.',
    ],
    idealFor: ['Cover esterne', 'Staffe outdoor', 'Parti esposte al sole'],
    sources: [
      {
        label: 'Wikipedia - Acrylonitrile styrene acrylate',
        kind: 'Wikipedia',
        url: 'https://en.wikipedia.org/wiki/Acrylonitrile_styrene_acrylate',
      },
      {
        label: 'Bambu Lab - ASA',
        kind: 'Scheda prodotto',
        url: 'https://eu.store.bambulab.com/it/products/asa-filament',
      },
      {
        label: 'Ultimaker - ASA',
        kind: 'Scheda tecnica',
        url: 'https://ultimaker.com/materials/method-series-asa/',
      },
    ],
  },
  {
    id: 'pc',
    name: 'PC',
    summary:
      'Materiale tecnico ad alta resistenza meccanica e termica per parti robuste.',
    qualityTips: [
      'Preferibile camera calda e materiale ben asciutto.',
      'Layer 0.20-0.28 mm riduce stress residui rispetto a layer sottili.',
    ],
    metrics: {
      priceChfKg: 39,
      densityGcm3: 1.2,
      tensileMpa: 55,
      modulusGpa: 2.11,
      elongationPct: 3.8,
      hdtC: 112,
      extrusionC: '260 - 280',
      printability: 47,
      layerRangeMm: '0.20 - 0.28',
    },
    pros: [
      'Molto resistente all urto e al calore.',
      'Stabilita dimensionale elevata su pezzi tecnici.',
    ],
    cons: [
      'Stampa impegnativa con alta temperatura.',
      'Setup non ottimale porta facilmente a deformazioni.',
    ],
    idealFor: [
      'Alloggiamenti tecnici',
      'Staffe ad alto carico',
      'Parti vicino a fonti di calore',
    ],
    sources: [
      {
        label: 'Wikipedia - Polycarbonate',
        kind: 'Wikipedia',
        url: 'https://en.wikipedia.org/wiki/Polycarbonate',
      },
      {
        label: 'Bambu Lab - PC',
        kind: 'Scheda prodotto',
        url: 'https://eu.store.bambulab.com/it/products/pc-filament',
      },
      {
        label: 'Ultimaker - PC',
        kind: 'Scheda tecnica',
        url: 'https://ultimaker.com/materials/s-series-pc/',
      },
    ],
  },
  {
    id: 'pa12-cf',
    name: 'PA12-CF',
    summary:
      'Nylon rinforzato carbonio orientato a rigidita e stabilita su parti funzionali.',
    qualityTips: [
      'Materiale e ambiente devono essere asciutti prima della stampa.',
      'Layer 0.20-0.28 mm con ugello temprato e setup consigliato.',
    ],
    metrics: {
      priceChfKg: 50,
      densityGcm3: 1.06,
      tensileMpa: 60,
      modulusGpa: 3.3,
      elongationPct: 16,
      hdtC: 185,
      extrusionC: '260 - 290',
      printability: 42,
      layerRangeMm: '0.20 - 0.28',
    },
    pros: [
      'Ottimo rapporto rigidita/peso.',
      'Buona stabilita dimensionale per fixture tecniche.',
    ],
    cons: [
      'Costo e complessita stampa superiori ai materiali base.',
      'Richiede gestione umidita e ugello adeguato.',
    ],
    idealFor: [
      'Jig e fixture',
      'Parti strutturali leggere',
      'Componenti meccanici tecnici',
    ],
    sources: [
      {
        label: 'Wikipedia - Nylon 12',
        kind: 'Wikipedia',
        url: 'https://en.wikipedia.org/wiki/Nylon_12',
      },
      {
        label: 'Ultimaker - Nylon 12 Carbon Fiber',
        kind: 'Scheda tecnica',
        url: 'https://ultimaker.com/materials/method-series-nylon-12-carbon-fiber/',
      },
      {
        label: 'Scheda prodotto - PA12-CF',
        kind: 'Scheda prodotto',
        url: 'https://www.amazon.de/-/en/ERYONE-Carbon-Filament-Printer-Printers/dp/B0CHDS7YD2/',
      },
    ],
  },
  {
    id: 'pet-cf',
    name: 'PET-CF',
    summary:
      'Materiale tecnico rigido con fibra di carbonio, molto stabile su pezzi di precisione.',
    qualityTips: [
      'Ugello temprato obbligatorio per abrasivita della fibra.',
      'Layer 0.20-0.28 mm bilancia adesione e rigidita finale.',
    ],
    metrics: {
      priceChfKg: 83,
      densityGcm3: 1.29,
      tensileMpa: 74,
      modulusGpa: 4.73,
      elongationPct: 4,
      hdtC: 205,
      extrusionC: '260 - 290',
      printability: 39,
      layerRangeMm: '0.20 - 0.28',
    },
    pros: [
      'Alte prestazioni meccaniche e termiche.',
      'Bassa deformazione su geometrie funzionali.',
    ],
    cons: [
      'Abrasivo: serve ugello temprato.',
      'Costo alto, non ideale per prototipi economici.',
    ],
    idealFor: [
      'Componenti tecnici di precisione',
      'Supporti rigidi',
      'Parti con stabilita termica elevata',
    ],
    sources: [
      {
        label: 'Wikipedia - Polyethylene terephthalate',
        kind: 'Wikipedia',
        url: 'https://en.wikipedia.org/wiki/Polyethylene_terephthalate',
      },
      {
        label: 'Wikipedia - Carbon fiber reinforced polymer',
        kind: 'Wikipedia',
        url: 'https://en.wikipedia.org/wiki/Carbon_fiber_reinforced_polymer',
      },
      {
        label: 'Bambu Lab - PET-CF',
        kind: 'Scheda prodotto',
        url: 'https://eu.store.bambulab.com/it/products/pet-cf',
      },
    ],
  },
];

const RADAR_AXES: readonly RadarAxis[] = [
  {
    id: 'economy',
    label: 'Convenienza',
    description:
      'Indice basato sul prezzo al kg: un valore alto indica un costo piu favorevole.',
    unit: 'CHF/kg',
    lowerIsBetter: true,
    accessor: (material) => material.metrics.priceChfKg,
  },
  {
    id: 'printability',
    label: 'Stampabilità',
    description:
      'Indice sintetico che considera facilita di stampa, sensibilita all umidita e stabilita del processo.',
    unit: 'indice',
    accessor: (material) => material.metrics.printability,
  },
  {
    id: 'tensile',
    label: 'Resistenza',
    description: 'Resistenza a trazione del materiale (MPa).',
    unit: 'MPa',
    accessor: (material) => material.metrics.tensileMpa,
  },
  {
    id: 'modulus',
    label: 'Rigidità',
    description: 'Modulo elastico (GPa).',
    unit: 'GPa',
    accessor: (material) => material.metrics.modulusGpa,
  },
  {
    id: 'elongation',
    label: 'Flessibilità',
    description: 'Allungamento a rottura (%).',
    unit: '%',
    accessor: (material) => material.metrics.elongationPct,
  },
  {
    id: 'hdt',
    label: 'Resistenza termica',
    chartLabelLines: ['Resistenza', 'termica'],
    description: 'HDT: temperatura di deformazione del materiale (C).',
    unit: 'C',
    accessor: (material) => material.metrics.hdtC,
  },
];

const CALCULATOR_FACTS: readonly CalculatorFact[] = [
  {
    title: 'Cosa restituisce il calcolatore',
    description:
      'Il calcolatore restituisce un prezzo fisso corretto, il tempo di stampa e il consumo materiale a partire dal file e dalle impostazioni selezionate.',
    details: [
      'Utile per confrontare rapidamente materiali, qualita e configurazioni prima dell ordine.',
      'Se cambi materiale, layer, riempimento o supporti, cambia anche il prezzo mostrato.',
    ],
  },
  {
    title: 'Modalita Base',
    description:
      'Pensata per file gia pronti: scegli materiale e qualita, senza entrare nei dettagli tecnici della stampa.',
    detailsTitle: 'Preset disponibili:',
    details: [
      'Draft = 0.28 mm, 15% grid.',
      'Standard = 0.20 mm, 15% grid.',
      'High Definition = 0.12 mm, 20% gyroid.',
    ],
    ctaLabel: 'Apri Base',
    path: '/calculator/basic#calculator-workspace',
  },
  {
    title: 'Modalita Avanzata',
    description:
      'Pensata per un prezzo corretto in base alle impostazioni reali: controlli materiale, ugello, altezza layer, riempimento, pattern e supporti.',
    details: [
      'Le combinazioni disponibili dipendono dall ugello selezionato e dai profili macchina attivi.',
    ],
    ctaLabel: 'Apri Avanzata',
    path: '/calculator/advanced#calculator-workspace',
  },
];

const CALCULATOR_PARAMETERS: readonly CalculatorParameter[] = [
  {
    title: 'Materiale',
    availability: 'Base e Avanzata',
    explanation:
      'Il materiale puo essere scelto tra le varianti attive disponibili, ciascuna associata ai colori ordinabili.',
    calculatorEffect:
      'Influisce sul costo del materiale e sul prezzo finale mostrato.',
  },
  {
    title: 'Qualita',
    availability: 'Base',
    explanation:
      'In Base non imposti layer e riempimento singolarmente: scegli un preset predefinito.',
    calculatorEffect:
      'Definisce automaticamente altezza layer, pattern di riempimento e densita.',
  },
  {
    title: 'Diametro ugello',
    availability: 'Avanzata',
    explanation:
      'Gli ugelli disponibili dipendono dalla macchina e dalla configurazione attiva. Ugelli diversi possono comportare anche costi di cambio.',
    calculatorEffect:
      'Influisce sulle altezze layer selezionabili e puo incidere sul costo di preparazione.',
  },
  {
    title: 'Altezza layer',
    availability: 'Avanzata',
    explanation: "Le altezze layer selezionabili dipendono dall'ugello scelto.",
    calculatorEffect:
      'Incide direttamente su tempi di stampa e definizione del pezzo.',
  },
  {
    title: 'Riempimento',
    availability: 'Avanzata',
    explanation:
      'La percentuale di infill si imposta manualmente per adattare il pezzo alla robustezza desiderata.',
    calculatorEffect:
      'Aumenta o riduce peso e tempo in base alla densita scelta.',
  },
  {
    title: 'Pattern riempimento',
    availability: 'Avanzata',
    explanation: 'Sono disponibili pattern come grid, gyroid e cubic.',
    calculatorEffect:
      'Influisce sulla struttura interna del pezzo e puo modificare tempi, consumo materiale e comportamento meccanico.',
  },
  {
    title: 'Supporti',
    availability: 'Avanzata',
    explanation:
      'Puoi attivarli o disattivarli direttamente in base alla geometria del file.',
    calculatorEffect:
      'Cambia il percorso di stampa e puo aumentare tempo e materiale.',
  },
];

const QUALITY_VISUAL_GUIDES: readonly QualityVisualGuide[] = [
  {
    id: 'layer-012',
    category: 'Layer',
    title: 'Layer 0.12 mm',
    objectExample: 'Miniatura o testo piccolo con dettagli fini.',
    bestFor: 'Massimo dettaglio superficiale.',
    tradeoff: 'Tempo di stampa alto.',
    calculatorRead: 'Piu dettaglio visivo, ma tempi di stampa piu lunghi.',
    usageKey: 'guide-layer-012',
  },
  {
    id: 'layer-020',
    category: 'Layer',
    title: 'Layer 0.20 mm',
    objectExample: 'Pezzo funzionale standard o cover tecnica.',
    bestFor: 'Compromesso qualita/tempo.',
    tradeoff: 'Dettaglio inferiore al 0.12 mm.',
    calculatorRead: 'Configurazione bilanciata, adatta a una stampa standard.',
    usageKey: 'guide-layer-020',
  },
  {
    id: 'layer-028',
    category: 'Layer',
    title: 'Layer 0.28 mm',
    objectExample: 'Staffa di test o prototipo rapido.',
    bestFor: 'Riduzione tempi e pezzi voluminosi.',
    tradeoff: 'Superficie piu visibile a scalini.',
    calculatorRead: 'Stampa piu rapida, con finitura meno fine.',
    usageKey: 'guide-layer-028',
  },
  {
    id: 'nozzle-025',
    category: 'Ugello',
    title: 'Ugello 0.25 mm',
    objectExample: 'Scritta piccola o geometria sottile.',
    bestFor: 'Dettagli molto piccoli.',
    tradeoff: 'Tempi piu lunghi e portata ridotta.',
    calculatorRead:
      'Favorisce dettagli molto fini, ma con minore produttivita.',
    usageKey: 'guide-nozzle-025',
  },
  {
    id: 'nozzle-060',
    category: 'Ugello',
    title: 'Ugello 0.60 mm',
    objectExample: 'Parti robuste, supporti, staffe.',
    bestFor: 'Resistenza e velocita su pezzi funzionali.',
    tradeoff: 'Dettaglio fine ridotto.',
    calculatorRead:
      'Favorisce configurazioni piu produttive e meno orientate al dettaglio fine.',
    usageKey: 'guide-nozzle-060',
  },
  {
    id: 'infill-15',
    category: 'Riempimento',
    title: 'Infill 15% + 2/3 perimetri',
    objectExample: 'Oggetto estetico o mockup leggero.',
    bestFor: 'Ridurre peso e tempo.',
    tradeoff: 'Resistenza strutturale limitata.',
    calculatorRead: 'Riduce materiale e tende a contenere i tempi di stampa.',
    usageKey: 'guide-infill-15',
  },
  {
    id: 'infill-40',
    category: 'Riempimento',
    title: 'Infill 40% + 4 perimetri',
    objectExample: 'Componente con carico meccanico.',
    bestFor: 'Migliore resistenza funzionale.',
    tradeoff: 'Peso e tempo di stampa superiori.',
    calculatorRead:
      'Aumenta materiale e tempo di stampa, avvicinandosi a un pezzo piu pieno.',
    usageKey: 'guide-infill-40',
  },
];

const SERIES_STYLES = [
  { stroke: '#c23b22', fill: 'rgba(194, 59, 34, 0.22)' },
  { stroke: '#2663d3', fill: 'rgba(38, 99, 211, 0.20)' },
  { stroke: '#0f8f6f', fill: 'rgba(15, 143, 111, 0.19)' },
  { stroke: '#8a44c9', fill: 'rgba(138, 68, 201, 0.18)' },
  { stroke: '#c77510', fill: 'rgba(199, 117, 16, 0.17)' },
  { stroke: '#125067', fill: 'rgba(18, 80, 103, 0.16)' },
  { stroke: '#8f2f5f', fill: 'rgba(143, 47, 95, 0.16)' },
  { stroke: '#3b7f1f', fill: 'rgba(59, 127, 31, 0.16)' },
] as const;

const MATERIALS_BY_ID = new Map(
  MATERIALS.map((material) => [material.id, material] as const),
);

const CHART_SIZE = 460;
const CHART_CENTER = CHART_SIZE / 2;
const CHART_RADIUS = 156;
const CHART_LEVELS = 5;
const CHART_INNER_RATIO = 0.09;
const EMPTY_MEDIA_COLLECTIONS: PublicMediaUsageCollectionMap = {};
const MAX_COMPARE_COUNT = 6;

@Component({
  selector: 'app-materials-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './materials-page.component.html',
  styleUrl: './materials-page.component.scss',
})
export class MaterialsPageComponent {
  private readonly publicMediaService = inject(PublicMediaService);
  readonly languageService = inject(LanguageService);

  readonly materials = MATERIALS;
  readonly radarAxes = RADAR_AXES;
  readonly maxCompareCount = MAX_COMPARE_COUNT;
  readonly calculatorFacts = CALCULATOR_FACTS;
  readonly calculatorParameters = CALCULATOR_PARAMETERS;

  readonly selectedMaterialIds = signal<string[]>([
    'pla-basic',
    'asa',
    'pet-cf',
  ]);
  readonly hoveredMaterialId = signal<string | null>(null);

  private readonly pageMediaRequests = QUALITY_VISUAL_GUIDES.map((guide) => ({
    usageType: 'MATERIALS_PAGE' as const,
    usageKey: guide.usageKey,
  }));

  private readonly mediaByUsage = toSignal(
    this.publicMediaService.getUsageCollections(this.pageMediaRequests),
    { initialValue: EMPTY_MEDIA_COLLECTIONS },
  );

  readonly selectedCount = computed(() => this.selectedMaterialIds().length);

  readonly selectedMaterialStyles = computed<
    ReadonlyMap<string, MaterialSeriesStyle>
  >(() => {
    const styles = new Map<string, MaterialSeriesStyle>();
    this.selectedMaterialIds().forEach((materialId, index) => {
      styles.set(materialId, SERIES_STYLES[index % SERIES_STYLES.length]);
    });
    return styles;
  });

  readonly selectedMaterials = computed(() => {
    return this.selectedMaterialIds()
      .map((materialId) => MATERIALS_BY_ID.get(materialId))
      .filter((material): material is MaterialRecord => Boolean(material));
  });

  readonly qualityVisualCards = computed<readonly QualityVisualCard[]>(() =>
    QUALITY_VISUAL_GUIDES.map((guide) => ({
      ...guide,
      image: this.resolveUsageImage(guide.usageKey),
    })),
  );

  readonly ringPolygons = computed(() => {
    const polygons: string[] = [];
    for (let level = 1; level <= CHART_LEVELS; level += 1) {
      polygons.push(this.polygonPoints(this.visualRatio(level / CHART_LEVELS)));
    }
    return polygons;
  });

  readonly axisGuides = computed<readonly AxisGuide[]>(() =>
    RADAR_AXES.map((axis, index) => {
      const inner = this.pointForRatio(index, CHART_INNER_RATIO);
      const outer = this.pointForRatio(index, 1);
      const label = this.pointForRatio(index, 1.18);
      const labelLines = axis.chartLabelLines ?? [axis.label];
      const anchor: 'start' | 'middle' | 'end' =
        Math.abs(label.x - CHART_CENTER) < 12
          ? 'middle'
          : label.x > CHART_CENTER
            ? 'start'
            : 'end';

      return {
        id: axis.id,
        fromX: inner.x,
        fromY: inner.y,
        x: outer.x,
        y: outer.y,
        labelX: label.x,
        labelY: label.y - (labelLines.length - 1) * 6,
        labelAnchor: anchor,
        labelLines,
      };
    }),
  );

  readonly radarSeries = computed<readonly RadarSeries[]>(() =>
    this.selectedMaterials().map((material) => {
      const style =
        this.selectedMaterialStyles().get(material.id) ?? SERIES_STYLES[0];
      const values: RadarPoint[] = RADAR_AXES.map((axis, axisIndex) => {
        const { rawValue, score } = this.axisScore(material, axis);
        const point = this.pointForScore(axisIndex, score);
        return {
          axis,
          score,
          rawValue,
          x: point.x,
          y: point.y,
        };
      });

      return {
        material,
        color: style.stroke,
        fill: style.fill,
        points: values.map((value) => `${value.x},${value.y}`).join(' '),
        values,
      };
    }),
  );

  readonly comparisonRows = computed<readonly ComparisonRow[]>(() => {
    const selected = this.selectedMaterials();
    const format = (value: number, fractionDigits: number): string =>
      value.toFixed(fractionDigits);

    return [
      {
        label: 'Stampabilita [indice 0-100]',
        values: selected.map((material) =>
          format(material.metrics.printability, 0),
        ),
      },
      {
        label: 'Layer consigliato [mm]',
        values: selected.map((material) => material.metrics.layerRangeMm),
      },
      {
        label: 'Prezzo [CHF/kg]',
        values: selected.map((material) =>
          format(material.metrics.priceChfKg, 0),
        ),
      },
      {
        label: 'Densita [g/cm3]',
        values: selected.map((material) =>
          format(material.metrics.densityGcm3, 2),
        ),
      },
      {
        label: 'Resistenza a trazione [MPa]',
        values: selected.map((material) =>
          format(material.metrics.tensileMpa, 0),
        ),
      },
      {
        label: 'Modulo elastico [GPa]',
        values: selected.map((material) =>
          format(material.metrics.modulusGpa, 2),
        ),
      },
      {
        label: 'Allungamento a rottura [%]',
        values: selected.map((material) =>
          format(material.metrics.elongationPct, 1),
        ),
      },
      {
        label: 'Temperatura di deformazione [C]',
        values: selected.map((material) => format(material.metrics.hdtC, 0)),
      },
      {
        label: 'Temperatura estrusione [C]',
        values: selected.map((material) => material.metrics.extrusionC),
      },
    ];
  });

  readonly allSources = computed<readonly MaterialSource[]>(() => {
    const unique = new Map<string, MaterialSource>();
    MATERIALS.forEach((material) => {
      material.sources.forEach((source) => {
        unique.set(source.url, source);
      });
    });
    return Array.from(unique.values());
  });

  isSelected(materialId: string): boolean {
    return this.selectedMaterialIds().includes(materialId);
  }

  canSelect(_materialId: string): boolean {
    return true;
  }

  toggleMaterial(materialId: string): void {
    const selected = this.selectedMaterialIds();

    if (selected.includes(materialId)) {
      const next = selected.filter((id) => id !== materialId);
      this.selectedMaterialIds.set(next.length > 0 ? next : [materialId]);
      return;
    }

    const next = [...selected, materialId];
    while (next.length > MAX_COMPARE_COUNT) {
      next.shift();
    }
    this.selectedMaterialIds.set(next);
  }

  setHoveredMaterial(materialId: string | null): void {
    this.hoveredMaterialId.set(materialId);
  }

  legendDotColor(materialId: string): string {
    return this.selectedMaterialStyles().get(materialId)?.stroke ?? '#9aa2ad';
  }

  legendFillColor(materialId: string): string {
    return this.selectedMaterialStyles().get(materialId)?.fill ?? '#ffffff';
  }

  trackMaterial(_index: number, material: MaterialRecord): string {
    return material.id;
  }

  trackSource(_index: number, source: MaterialSource): string {
    return source.url;
  }

  trackCalculatorFact(_index: number, fact: CalculatorFact): string {
    return fact.title;
  }

  trackCalculatorParameter(
    _index: number,
    parameter: CalculatorParameter,
  ): string {
    return parameter.title;
  }

  trackVisualGuide(_index: number, guide: QualityVisualCard): string {
    return guide.id;
  }

  localizedPath(path: string): string {
    return this.languageService.localizedPath(path);
  }

  private resolveUsageImage(
    usageKeyRaw: string,
  ): PublicMediaDisplayImage | null {
    const usageKey = buildPublicMediaUsageScopeKey(
      'MATERIALS_PAGE',
      usageKeyRaw,
    );
    const usageItems = this.mediaByUsage()[usageKey] ?? [];
    const primary = this.publicMediaService.pickPrimaryUsage(usageItems);
    return primary
      ? this.publicMediaService.toDisplayImage(primary, 'card')
      : null;
  }

  private axisScore(
    material: MaterialRecord,
    axis: RadarAxis,
  ): {
    rawValue: number;
    score: number;
  } {
    const rawValue = axis.accessor(material);
    const axisValues = MATERIALS.map(axis.accessor);
    const min = Math.min(...axisValues);
    const max = Math.max(...axisValues);

    if (max === min) {
      return { rawValue, score: 100 };
    }

    const normalized = ((rawValue - min) / (max - min)) * 100;
    const score = axis.lowerIsBetter ? 100 - normalized : normalized;
    return { rawValue, score: Math.max(0, Math.min(100, score)) };
  }

  private polygonPoints(ratio: number): string {
    return RADAR_AXES.map((_, index) => {
      const point = this.pointForRatio(index, ratio);
      return `${point.x},${point.y}`;
    }).join(' ');
  }

  private pointForScore(
    axisIndex: number,
    score: number,
  ): {
    x: number;
    y: number;
  } {
    return this.pointForRatio(axisIndex, this.visualRatio(score / 100));
  }

  private pointForRatio(
    axisIndex: number,
    ratio: number,
  ): {
    x: number;
    y: number;
  } {
    const angle = -Math.PI / 2 + (axisIndex * 2 * Math.PI) / RADAR_AXES.length;
    return {
      x: CHART_CENTER + Math.cos(angle) * CHART_RADIUS * ratio,
      y: CHART_CENTER + Math.sin(angle) * CHART_RADIUS * ratio,
    };
  }

  private visualRatio(normalizedRatio: number): number {
    const clampedRatio = this.clamp(normalizedRatio, 0, 1);
    return CHART_INNER_RATIO + clampedRatio * (1 - CHART_INNER_RATIO);
  }

  private clamp(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), max);
  }

  protected readonly chartSize = CHART_SIZE;
}
