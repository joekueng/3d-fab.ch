import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppAlertComponent } from './app-alert.component';

describe('AppAlertComponent', () => {
  let fixture: ComponentFixture<AppAlertComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppAlertComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AppAlertComponent);
  });

  it('renders errors as a semantic banner without emoji decoration', () => {
    fixture.componentRef.setInput('type', 'error');
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('.alert');
    expect(alert.getAttribute('role')).toBe('alert');
    expect(alert.querySelector('.icon')).toBeNull();
  });

  it('uses status semantics for non-error messages', () => {
    fixture.componentRef.setInput('type', 'warning');
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('.alert').getAttribute('role'),
    ).toBe('status');
  });
});
