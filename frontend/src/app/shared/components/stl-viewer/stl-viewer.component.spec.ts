import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import * as THREE from 'three';
import { StlViewerComponent } from './stl-viewer.component';

const STL = `solid triangle
facet normal 0 0 1
outer loop
vertex 0 0 0
vertex 10 0 0
vertex 0 10 0
endloop
endfacet
endsolid triangle`;

describe('StlViewerComponent asynchronous loading', () => {
  function setup() {
    TestBed.configureTestingModule({
      imports: [StlViewerComponent],
      providers: [provideZonelessChangeDetection(), provideTranslateService()],
    });
    const fixture = TestBed.createComponent(StlViewerComponent);
    // Exercise the real FileReader, STL parser and view; only GPU setup is stubbed.
    spyOn(
      fixture.componentInstance as unknown as { initThree(): void },
      'initThree',
    ).and.callFake(() => {
      Object.assign(fixture.componentInstance, {
        scene: new THREE.Scene(),
        camera: new THREE.PerspectiveCamera(50, 1, 0.1, 1000),
        controls: {
          target: new THREE.Vector3(),
          update: () => undefined,
          dispose: () => undefined,
        },
      });
    });
    const read = FileReader.prototype.readAsArrayBuffer;
    const readFinished = new Promise<void>((resolve) => {
      spyOn(FileReader.prototype, 'readAsArrayBuffer').and.callFake(function (
        this: FileReader,
        blob: Blob,
      ) {
        this.addEventListener('loadend', () => resolve(), { once: true });
        read.call(this, blob);
      });
    });
    fixture.autoDetectChanges();
    return { fixture, readFinished };
  }

  it('removes the loading overlay and renders dimensions without user interaction', async () => {
    const { fixture, readFinished } = setup();
    fixture.componentRef.setInput('file', new Blob([STL]));
    await fixture.whenStable();
    await readFinished;
    await fixture.whenStable();
    expect(fixture.componentInstance.loadError).toBeFalse();
    expect(fixture.nativeElement.querySelector('.loading-overlay')).toBeNull();
    expect(
      fixture.nativeElement.querySelector('.dims-overlay')?.textContent,
    ).toContain('10 x 10 x 0 mm');
  });

  it('renders a square thumbnail without dimensions and applies the initial zoom', async () => {
    const { fixture, readFinished } = setup();
    fixture.componentRef.setInput('aspectRatio', 1);
    fixture.componentRef.setInput('initialZoom', 1.35);
    fixture.componentRef.setInput('showDimensions', false);
    fixture.componentRef.setInput('file', new Blob([STL]));
    await fixture.whenStable();
    await readFinished;
    await fixture.whenStable();
    const container = fixture.nativeElement.querySelector(
      '.viewer-container',
    ) as HTMLElement;
    expect(container.style.aspectRatio).toBe('1 / 1');
    expect(container.style.height).toBe('auto');
    expect(fixture.nativeElement.querySelector('.dims-overlay')).toBeNull();
    const camera = (
      fixture.componentInstance as unknown as {
        camera: THREE.PerspectiveCamera;
      }
    ).camera;
    const originalFramingDistance =
      (10 / 2 / Math.tan(THREE.MathUtils.degToRad(50) / 2)) * 1.72;
    expect(camera.position.z).toBeCloseTo(
      (originalFramingDistance * 1.08) / 1.35,
      4,
    );
    expect(fixture.componentInstance.loadError).toBeFalse();
  });

  it('renders unavailable previews after parsing rejects the file', async () => {
    spyOn(console, 'warn');
    const { fixture, readFinished } = setup();
    fixture.componentRef.setInput('file', new Blob(['invalid model']));
    await fixture.whenStable();
    await readFinished;
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.loading-overlay')).toBeNull();
    expect(
      fixture.nativeElement.querySelector('.error-overlay'),
    ).not.toBeNull();
  });
});
