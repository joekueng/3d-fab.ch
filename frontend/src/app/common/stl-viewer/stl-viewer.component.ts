import { Component, ElementRef, Input, OnChanges, OnDestroy, OnInit, ViewChild, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import * as THREE from 'three';
import { STLLoader } from 'three/examples/jsm/loaders/STLLoader.js';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';

@Component({
  selector: 'app-stl-viewer',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="viewer-container" #rendererContainer>
      <div *ngIf="isLoading" class="loading-overlay">
        <span class="material-icons spin">autorenew</span>
        <p>Loading 3D Model...</p>
      </div>
      <div class="dimensions-overlay" *ngIf="dimensions">
        <p>Size: {{ dimensions.x | number:'1.1-1' }} x {{ dimensions.y | number:'1.1-1' }} x {{ dimensions.z | number:'1.1-1' }} mm</p>
      </div>
    </div>
  `,
  styles: [`
    .viewer-container {
      width: 100%;
      height: 100%;
      min-height: 300px;
      position: relative;
      background: #0f172a; /* Match app bg approx */
      overflow: hidden;
      border-radius: inherit;
    }
    .loading-overlay {
      position: absolute;
      top: 0; left: 0; right: 0; bottom: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      background: rgba(15, 23, 42, 0.8);
      color: white;
      z-index: 10;
    }
    .spin {
      animation: spin 1s linear infinite;
      font-size: 2rem;
      margin-bottom: 0.5rem;
    }
    @keyframes spin { 100% { transform: rotate(360deg); } }
    
    .dimensions-overlay {
      position: absolute;
      bottom: 10px;
      right: 10px;
      background: rgba(0,0,0,0.6);
      color: white;
      padding: 4px 8px;
      border-radius: 4px;
      font-size: 0.8rem;
      pointer-events: none;
    }
  `]
})
export class StlViewerComponent implements OnInit, OnDestroy, OnChanges {
  @Input() file: File | null = null;
  @ViewChild('rendererContainer', { static: true }) rendererContainer!: ElementRef;

  isLoading = false;
  dimensions: { x: number, y: number, z: number } | null = null;

  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private mesh!: THREE.Mesh;
  private controls!: OrbitControls;
  private animationId: number | null = null;

  ngOnInit() {
    this.initThree();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['file'] && this.file) {
      this.loadSTL(this.file);
    }
  }

  ngOnDestroy() {
    this.stopAnimation();
    if (this.renderer) {
      this.renderer.dispose();
    }
    if (this.mesh) {
      this.mesh.geometry.dispose();
      (this.mesh.material as THREE.Material).dispose();
    }
  }

  private initThree() {
    const container = this.rendererContainer.nativeElement;
    const width = container.clientWidth;
    const height = container.clientHeight;

    // Scene
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x1e293b); // Slate 800

    // Camera
    this.camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000);
    this.camera.position.set(100, 100, 100);

    // Renderer
    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    this.renderer.setSize(width, height);
    this.renderer.setPixelRatio(window.devicePixelRatio);
    container.appendChild(this.renderer.domElement);

    // Controls
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.05;
    this.controls.autoRotate = true;
    this.controls.autoRotateSpeed = 2.0;

    // Lights
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.6);
    this.scene.add(ambientLight);

    const dirLight = new THREE.DirectionalLight(0xffffff, 0.8);
    dirLight.position.set(50, 50, 50);
    this.scene.add(dirLight);
    
    const backLight = new THREE.DirectionalLight(0xffffff, 0.4);
    backLight.position.set(-50, -50, -50);
    this.scene.add(backLight);

    // Grid (Printer Bed attempt)
    const gridHelper = new THREE.GridHelper(256, 20, 0x4f46e5, 0x334155);
    this.scene.add(gridHelper);

    // Resize listener
    const resizeObserver = new ResizeObserver(() => this.onWindowResize());
    resizeObserver.observe(container);

    this.animate();
  }

  private loadSTL(file: File) {
    this.isLoading = true;
    
    // Remove previous mesh
    if (this.mesh) {
        this.scene.remove(this.mesh);
        this.mesh.geometry.dispose();
        (this.mesh.material as THREE.Material).dispose();
    }

    const loader = new STLLoader();
    const reader = new FileReader();

    reader.onload = (event) => {
      const buffer = event.target?.result as ArrayBuffer;
      const geometry = loader.parse(buffer);
      
      geometry.computeBoundingBox();
      const center = new THREE.Vector3();
      geometry.boundingBox?.getCenter(center);
      geometry.center(); // Center geometry
      
      // Calculate dimensions
      const size = new THREE.Vector3();
      geometry.boundingBox?.getSize(size);
      this.dimensions = { x: size.x, y: size.y, z: size.z };

      // Re-position camera based on size
      const maxDim = Math.max(size.x, size.y, size.z);
      this.camera.position.set(maxDim * 1.5, maxDim * 1.5, maxDim * 1.5);
      this.camera.lookAt(0, 0, 0);

      // Material
      const material = new THREE.MeshStandardMaterial({ 
        color: 0x6366f1, // Indigo 500
        roughness: 0.5,
        metalness: 0.1
      });

      this.mesh = new THREE.Mesh(geometry, material);
      this.mesh.rotation.x = -Math.PI / 2; // STL usually needs this
      this.scene.add(this.mesh);
      
      this.isLoading = false;
    };

    reader.readAsArrayBuffer(file);
  }

  private animate() {
    this.animationId = requestAnimationFrame(() => this.animate());
    this.controls.update();
    this.renderer.render(this.scene, this.camera);
  }

  private stopAnimation() {
    if (this.animationId !== null) {
      cancelAnimationFrame(this.animationId);
    }
  }

  private onWindowResize() {
    if (!this.rendererContainer) return;
    const container = this.rendererContainer.nativeElement;
    const width = container.clientWidth;
    const height = container.clientHeight;

    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height);
  }
}
