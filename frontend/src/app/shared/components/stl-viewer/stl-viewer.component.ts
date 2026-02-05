import { Component, ElementRef, Input, OnChanges, OnDestroy, OnInit, ViewChild, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import * as THREE from 'three';
// @ts-ignore
import { STLLoader } from 'three/examples/jsm/loaders/STLLoader.js';
// @ts-ignore
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';

@Component({
  selector: 'app-stl-viewer',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="viewer-container" #rendererContainer>
      @if (loading) {
        <div class="loading-overlay">
          <div class="spinner"></div>
          <span>Loading 3D Model...</span>
        </div>
      }
      @if (file && !loading) {
        <div class="dims-overlay">
           {{ dimensions.x }} x {{ dimensions.y }} x {{ dimensions.z }} mm
        </div>
      }
    </div>
  `,
  styles: [`
    .viewer-container {
      width: 100%;
      height: 300px;
      background: var(--color-neutral-50);
      border-radius: var(--radius-lg);
      border: 1px solid var(--color-border);
      overflow: hidden;
      position: relative;
    }
    .loading-overlay {
      position: absolute;
      inset: 0;
      background: rgba(255, 255, 255, 0.8);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      z-index: 10;
      color: var(--color-text-muted);
    }
    .spinner {
      width: 32px;
      height: 32px;
      border: 3px solid var(--color-neutral-200);
      border-top-color: var(--color-brand);
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
    .dims-overlay {
        position: absolute;
        bottom: 8px;
        right: 8px;
        background: rgba(0,0,0,0.6);
        color: white;
        padding: 4px 8px;
        border-radius: 4px;
        font-size: 0.75rem;
        font-family: monospace;
        pointer-events: none;
    }
  `]
})
export class StlViewerComponent implements OnInit, OnDestroy, OnChanges {
  @Input() file: File | null = null;
  @ViewChild('rendererContainer', { static: true }) rendererContainer!: ElementRef;

  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private controls!: OrbitControls;
  private animationId: number | null = null;
  private currentMesh: THREE.Mesh | null = null;

  loading = false;

  ngOnInit() {
    this.initThree();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['file'] && this.file) {
      this.loadFile(this.file);
    }
  }

  ngOnDestroy() {
    if (this.animationId) cancelAnimationFrame(this.animationId);
    if (this.renderer) this.renderer.dispose();
  }

  private initThree() {
    const width = this.rendererContainer.nativeElement.clientWidth;
    const height = this.rendererContainer.nativeElement.clientHeight;

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0xf7f6f2); // Neutral-50

    // Lights
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.6);
    this.scene.add(ambientLight);

    const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8);
    directionalLight.position.set(1, 1, 1);
    this.scene.add(directionalLight);

    // Camera
    this.camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 1000);
    this.camera.position.z = 100;

    // Renderer
    this.renderer = new THREE.WebGLRenderer({ antialias: true });
    this.renderer.setSize(width, height);
    this.rendererContainer.nativeElement.appendChild(this.renderer.domElement);

    // Controls
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;

    this.animate();

    // Handle resize
    const resizeObserver = new ResizeObserver(() => {
        if (!this.rendererContainer) return;
        const w = this.rendererContainer.nativeElement.clientWidth;
        const h = this.rendererContainer.nativeElement.clientHeight;
        this.camera.aspect = w / h;
        this.camera.updateProjectionMatrix();
        this.renderer.setSize(w, h);
    });
    resizeObserver.observe(this.rendererContainer.nativeElement);
  }

  dimensions = { x: 0, y: 0, z: 0 };

  private loadFile(file: File) {
    this.loading = true;
    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const loader = new STLLoader();
        const geometry = loader.parse(event.target?.result as ArrayBuffer);
        
        if (this.currentMesh) {
          this.scene.remove(this.currentMesh);
          this.currentMesh.geometry.dispose();
        }

        const material = new THREE.MeshPhongMaterial({ 
          color: 0xFACF0A, // Brand color
          specular: 0x111111,
          shininess: 200 
        });
        
        this.currentMesh = new THREE.Mesh(geometry, material);
        
        // Center geometry
        geometry.computeBoundingBox();
        geometry.center();

        // Get Dimensions
        const boundingBox = geometry.boundingBox!;
        const size = new THREE.Vector3();
        boundingBox.getSize(size);
        
        this.dimensions = {
            x: Math.round(size.x * 10) / 10,
            y: Math.round(size.y * 10) / 10,
            z: Math.round(size.z * 10) / 10
        };

        // Rotate to stand upright (usually necessary for STLs)
        this.currentMesh.rotation.x = -Math.PI / 2;

        this.scene.add(this.currentMesh);

        // Adjust camera to fit object
        const maxDim = Math.max(size.x, size.y, size.z);
        const fov = this.camera.fov * (Math.PI / 180);
        
        // Calculate distance towards camera (z-axis)
        let cameraZ = Math.abs(maxDim / 2 / Math.tan(fov / 2));
        cameraZ *= 1.5; // Tighter zoom (reduced from 2.5)
        
        this.camera.position.z = cameraZ;
        this.camera.updateProjectionMatrix();
        this.controls.update();
        
      } catch (err) {
        console.error('Error loading STL:', err);
      } finally {
        this.loading = false;
      }
    };
    reader.readAsArrayBuffer(file);
  }

  private animate() {
    this.animationId = requestAnimationFrame(() => this.animate());
    if (this.controls) this.controls.update();
    if (this.renderer && this.scene && this.camera) {
        this.renderer.render(this.scene, this.camera);
    }
  }
}
