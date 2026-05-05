import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { AdminLanguageToolbarComponent } from './admin-language-toolbar.component';

describe('AdminLanguageToolbarComponent', () => {
  let fixture: ComponentFixture<AdminLanguageToolbarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminLanguageToolbarComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminLanguageToolbarComponent);
    fixture.componentRef.setInput('languages', ['it', 'en', 'de']);
    fixture.componentRef.setInput('activeLanguage', 'it');
    fixture.componentRef.setInput('states', {
      it: 'complete',
      en: 'incomplete',
      de: 'empty',
    });
    fixture.detectChanges();
  });

  it('renders language labels with status text', () => {
    const text = fixture.nativeElement.textContent;

    expect(text).toContain('IT');
    expect(text).toContain('OK');
    expect(text).toContain('EN');
    expect(text).toContain('...');
    expect(text).toContain('DE');
    expect(text).toContain('vuoto');
  });

  it('emits the selected language', () => {
    let selectedLanguage = '';
    fixture.componentInstance.activeLanguageChange.subscribe((language) => {
      selectedLanguage = language;
    });

    const buttons = fixture.debugElement.queryAll(By.css('button'));
    buttons[1].nativeElement.click();

    expect(selectedLanguage).toBe('en');
  });
});
