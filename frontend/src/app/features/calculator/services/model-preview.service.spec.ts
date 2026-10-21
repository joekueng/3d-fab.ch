import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { ModelPreviewService } from './model-preview.service';

describe('ModelPreviewService', () => {
  it('uploads the original model and returns the generated STL blob', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(ModelPreviewService);
    const http = TestBed.inject(HttpTestingController);
    const original = new File(['project'], 'part.3mf');
    const preview = new Blob(['solid preview'], { type: 'model/stl' });
    let result: Blob | undefined;

    service.createStlPreview(original).subscribe((blob) => (result = blob));
    const request = http.expectOne(`${environment.apiUrl}/api/model-previews`);
    expect(request.request.method).toBe('POST');
    expect(request.request.responseType).toBe('blob');
    expect((request.request.body as FormData).get('file')).toBe(original);
    request.flush(preview);
    expect(result).toBe(preview);
    http.verify();
  });
});
