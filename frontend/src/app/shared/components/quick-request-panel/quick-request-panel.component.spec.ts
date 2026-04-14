import { PLATFORM_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { LanguageService } from '../../../core/services/language.service';
import {
  ContactRequestDraftService,
  ContactRequestDraft,
} from '../../../core/services/contact-request-draft.service';
import { QuoteRequestService } from '../../../core/services/quote-request.service';
import { QuickRequestPanelComponent } from './quick-request-panel.component';

describe('QuickRequestPanelComponent', () => {
  let fixture: ComponentFixture<QuickRequestPanelComponent>;

  const quoteRequestService = jasmine.createSpyObj<QuoteRequestService>(
    'QuoteRequestService',
    ['createRequest'],
  );
  const draftService = jasmine.createSpyObj<ContactRequestDraftService>(
    'ContactRequestDraftService',
    ['getDraft', 'setDraft', 'buildSubmittedMessage', 'clearDraft'],
  );
  const languageService = jasmine.createSpyObj<LanguageService>(
    'LanguageService',
    ['localizedPath', 'selectedLang'],
  );

  beforeEach(async () => {
    quoteRequestService.createRequest.and.returnValue(of(null));
    draftService.getDraft.and.returnValue(null as ContactRequestDraft | null);
    draftService.buildSubmittedMessage.and.callFake(
      (message: string) => message,
    );
    languageService.localizedPath.and.callFake((path: string) => `/it${path}`);
    languageService.selectedLang.and.returnValue('it');

    await TestBed.configureTestingModule({
      imports: [QuickRequestPanelComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: QuoteRequestService, useValue: quoteRequestService },
        { provide: ContactRequestDraftService, useValue: draftService },
        { provide: LanguageService, useValue: languageService },
        { provide: PLATFORM_ID, useValue: 'browser' },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QuickRequestPanelComponent);
    fixture.componentRef.setInput('eyebrowKey', 'SHOP.CUSTOM_PART_FOOTER_TITLE');
    fixture.componentRef.setInput('titleKey', 'SHOP.CUSTOM_PART_FOOTER_TEXT');
    fixture.componentRef.setInput('descriptionKey', 'SHOP.QUICK_REQUEST_HELP');
    fixture.detectChanges();
  });

  it('renders the shared feature panel shell around the quick request form', () => {
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('app-feature-panel')).toBeTruthy();
    expect(host.querySelector('.quick-request__form')).toBeTruthy();
    expect(host.textContent).toContain('SHOP.CUSTOM_PART_FOOTER_TEXT');
  });
});
