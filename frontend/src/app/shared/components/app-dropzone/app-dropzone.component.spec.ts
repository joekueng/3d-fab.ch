import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { AppDropzoneComponent } from './app-dropzone.component';

describe('AppDropzoneComponent', () => {
  it('uses one file flow for selection and drop, supports reselection and delegates previews', async () => {
    await TestBed.configureTestingModule({
      imports: [AppDropzoneComponent, TranslateModule.forRoot()],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppDropzoneComponent);
    fixture.componentRef.setInput('showFileNames', false);
    fixture.componentRef.setInput('accept', '.pdf');
    const selected = jasmine.createSpy('files');
    fixture.componentInstance.filesDropped.subscribe(selected);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const input = element.querySelector<HTMLInputElement>('input')!;
    const transfer = new DataTransfer();
    const file = new File(['fixture'], 'request.pdf', {
      type: 'application/pdf',
    });
    transfer.items.add(file);
    input.files = transfer.files;
    input.dispatchEvent(new Event('change'));
    expect(selected).toHaveBeenCalledWith([file]);
    expect(input.value).toBe('');
    const dropzone = element.querySelector<HTMLElement>('.dropzone')!;
    const dropped = new DataTransfer();
    dropped.items.add(file);
    dropzone.dispatchEvent(new DragEvent('drop', { dataTransfer: dropped }));
    expect(selected).toHaveBeenCalledTimes(2);
    fixture.detectChanges();
    expect(element.querySelector('.file-badges')).toBeNull();
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    dropzone.dispatchEvent(new DragEvent('drop', { dataTransfer: dropped }));
    expect(selected).toHaveBeenCalledTimes(2);
    expect(input.disabled).toBeTrue();
  });
});
