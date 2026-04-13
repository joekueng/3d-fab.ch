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

  it('renders the selector below the radar and removes material cards', () => {
    const host = fixture.nativeElement as HTMLElement;
    const chartCard = host.querySelector('.chart-card') as HTMLElement;
    const legend = chartCard.querySelector('.chart-legend') as HTMLElement;
    const selectorPanel = chartCard.querySelector(
      '.selector-panel',
    ) as HTMLElement;

    expect(chartCard).toBeTruthy();
    expect(legend).toBeTruthy();
    expect(selectorPanel).toBeTruthy();
    expect(legend.nextElementSibling).toBe(selectorPanel);
    expect(host.querySelector('.materials-section')).toBeNull();
    expect(host.querySelectorAll('.material-card').length).toBe(0);
  });

  it('updates radar and table content when a material is toggled', () => {
    component.toggleMaterial('pla-matte');
    fixture.detectChanges();

    expect(
      component.selectedMaterials().map((material) => material.id),
    ).toEqual(['pla-basic', 'pla-matte', 'asa', 'pet-cf']);

    const tableHeaders = Array.from(
      fixture.nativeElement.querySelectorAll(
        'thead th',
      ) as NodeListOf<HTMLTableCellElement>,
    ).map((cell) => cell.textContent?.trim());

    expect(tableHeaders).toContain('PLA Matte');
    expect(fixture.nativeElement.querySelectorAll('.legend-item').length).toBe(
      4,
    );
  });

  it('renders the calculator guide with localized links and real-calculator copy', () => {
    const host = fixture.nativeElement as HTMLElement;
    const calculatorSection = host.querySelector(
      '.calculator-section',
    ) as HTMLElement;
    const modeLinks = Array.from(
      calculatorSection.querySelectorAll('.calculator-mode-link'),
    ) as HTMLAnchorElement[];

    expect(calculatorSection.textContent).toContain(
      'controlli che incidono davvero sul preventivo',
    );
    expect(calculatorSection.textContent).toContain(
      'Parametri che il calcolatore usa davvero',
    );
    expect(calculatorSection.textContent).not.toContain('Cosa non fa oggi');
    expect(modeLinks.map((link) => link.getAttribute('href'))).toEqual([
      '/it/calculator/basic',
      '/it/calculator/advanced',
    ]);
  });
});
