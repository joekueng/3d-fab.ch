import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MaterialsPageComponent } from './materials-page.component';
import { PublicMediaService } from '../../core/services/public-media.service';
import { LanguageService } from '../../core/services/language.service';

describe('MaterialsPageComponent', () => {
  let fixture: ComponentFixture<MaterialsPageComponent>;
  let component: MaterialsPageComponent;

  const publicMediaService = jasmine.createSpyObj<PublicMediaService>(
    'PublicMediaService',
    ['getUsageCollections', 'pickPrimaryUsage', 'toDisplayImage'],
  );
  const languageService = jasmine.createSpyObj<LanguageService>(
    'LanguageService',
    ['localizedPath'],
  );

  beforeEach(async () => {
    publicMediaService.getUsageCollections.and.returnValue(of({}));
    languageService.localizedPath.and.callFake((path: string) => `/it${path}`);

    await TestBed.configureTestingModule({
      imports: [MaterialsPageComponent],
      providers: [
        { provide: PublicMediaService, useValue: publicMediaService },
        { provide: LanguageService, useValue: languageService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MaterialsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders the selector below the radar without a duplicate legend', () => {
    const host = fixture.nativeElement as HTMLElement;
    const heroTitle = host.querySelector('.ui-simple-hero__title');
    const chartCard = host.querySelector(
      '.materials-panel--chart',
    ) as HTMLElement;
    const selectorPanel = chartCard.querySelector(
      '.selector-panel',
    ) as HTMLElement;

    expect(heroTitle?.textContent?.trim()).toBe('Qualita e Materiali');
    expect(chartCard).toBeTruthy();
    expect(selectorPanel).toBeTruthy();
    expect(chartCard.querySelector('.chart-legend')).toBeNull();
    expect(host.querySelectorAll('.material-card').length).toBe(0);
  });

  it('updates radar and table content when a material is toggled', () => {
    component.toggleMaterial('pla-matte');
    fixture.detectChanges();

    expect(
      component.selectedMaterials().map((material) => material.id),
    ).toEqual(['pla-basic', 'asa', 'pet-cf', 'pla-matte']);

    const tableHeaders = Array.from(
      fixture.nativeElement.querySelectorAll(
        'thead th',
      ) as NodeListOf<HTMLTableCellElement>,
    ).map((cell) => cell.textContent?.trim());

    expect(tableHeaders).toContain('PLA Matte');
    expect(
      fixture.nativeElement.querySelectorAll('.selector-chip.is-selected')
        .length,
    ).toBe(4);
  });

  it('keeps selected chip and radar colors aligned by selection order', () => {
    component.toggleMaterial('pla-matte');
    component.toggleMaterial('tpu-95a-hf');
    fixture.detectChanges();

    expect(component.legendDotColor('pla-basic')).toBe('#c23b22');
    expect(component.legendDotColor('asa')).toBe('#2663d3');
    expect(component.legendDotColor('pet-cf')).toBe('#0f8f6f');
    expect(component.legendDotColor('pla-matte')).toBe('#8a44c9');
    expect(component.radarSeries().map((series) => series.color)).toEqual([
      '#c23b22',
      '#2663d3',
      '#0f8f6f',
      '#8a44c9',
      '#c77510',
    ]);
  });

  it('renders the calculator guide with localized links and real-calculator copy', () => {
    const host = fixture.nativeElement as HTMLElement;
    const heroLink = host.querySelector(
      '.materials-inline-link',
    ) as HTMLAnchorElement;
    const calculatorSection = host.querySelector(
      '.materials-section--muted',
    ) as HTMLElement;
    const factCards = Array.from(
      calculatorSection.querySelectorAll('.calculator-fact-card'),
    ) as HTMLElement[];
    const factLinks = Array.from(
      calculatorSection.querySelectorAll('.calculator-link-button'),
    ) as HTMLAnchorElement[];

    expect(calculatorSection.textContent).toContain(
      'Il calcolatore restituisce un prezzo fisso corretto',
    );
    expect(calculatorSection.textContent).toContain(
      'Parametri del calcolatore',
    );
    expect(calculatorSection.querySelector('.calculator-mode-card')).toBeNull();
    expect(factCards.length).toBe(3);
    expect(factCards[1]?.textContent).toContain('Preset disponibili:');
    expect(factCards[1]?.textContent).toContain('Draft = 0.28 mm, 15% grid.');
    expect(factCards[1]?.textContent).not.toContain('Ideale se');
    expect(factCards[2]?.textContent).toContain(
      'profili macchina attivi',
    );
    expect(factCards[2]?.textContent).not.toContain('Ideale se');
    expect(host.textContent).not.toContain('davvero');
    expect(host.textContent).not.toContain('tool online');
    expect(host.textContent).not.toContain('Seleziona fino a');
    expect(host.textContent).not.toContain('raggio minimo visivo');
    expect(host.textContent).not.toContain('usageType');
    expect(heroLink.getAttribute('href')).toBe('#materials-calculator');
    expect(calculatorSection.getAttribute('id')).toBe('materials-calculator');
    expect(factLinks.map((link) => link.getAttribute('href'))).toEqual([
      '/it/calculator/basic#calculator-workspace',
      '/it/calculator/advanced#calculator-workspace',
    ]);
  });
});
