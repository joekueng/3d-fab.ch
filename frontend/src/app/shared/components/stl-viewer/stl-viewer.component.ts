import {
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  ViewChild,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import * as THREE from 'three';
// @ts-ignore
import { STLLoader } from 'three/examples/jsm/loaders/STLLoader.js';
// @ts-ignore
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';

@Component({
  selector: 'app-stl-viewer',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './stl-viewer.component.html',
  styleUrl: './stl-viewer.component.scss',
})
export class StlViewerComponent implements OnInit, OnDestroy, OnChanges {
  @Input() file: File | null = null;
  @Input() color: string = '#facf0a'; // Default Brand Color
  @Input() height = 300;
  @Input() borderRadius = 'var(--radius-lg)';

  @ViewChild('rendererContainer', { static: true })
  rendererContainer!: ElementRef;

  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private controls!: OrbitControls;
  private animationId: number | null = null;
  private currentMesh: THREE.Mesh | null = null;
  private autoRotate = true;

  loading = false;

  ngOnInit() {
    this.initThree();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['file'] && this.file) {
      this.loadFile(this.file);
    }

    if (changes['color'] && this.currentMesh && !changes['file']) {
      this.applyColorStyle(this.color);
    }
  }

  ngOnDestroy() {
    if (this.animationId) cancelAnimationFrame(this.animationId);
    this.clearCurrentMesh();
    if (this.controls) this.controls.dispose();
    if (this.renderer) this.renderer.dispose();
  }

  private initThree() {
    const width = this.rendererContainer.nativeElement.clientWidth;
    const height = this.rendererContainer.nativeElement.clientHeight;

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0xf4f8fc);

    // Lights
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.75);
    this.scene.add(ambientLight);

    const hemiLight = new THREE.HemisphereLight(0xf8fbff, 0xc8d3df, 0.95);
    hemiLight.position.set(0, 30, 0);
    this.scene.add(hemiLight);

    const directionalLight1 = new THREE.DirectionalLight(0xffffff, 1.35);
    directionalLight1.position.set(6, 8, 6);
    this.scene.add(directionalLight1);

    const directionalLight2 = new THREE.DirectionalLight(0xe8f0ff, 0.85);
    directionalLight2.position.set(-7, 4, -5);
    this.scene.add(directionalLight2);

    const directionalLight3 = new THREE.DirectionalLight(0xffffff, 0.55);
    directionalLight3.position.set(0, 5, -9);
    this.scene.add(directionalLight3);

    // Camera
    this.camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 1000);
    this.camera.position.z = 100;

    // Renderer
    this.renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: false,
      powerPreference: 'high-performance',
    });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.2;
    this.renderer.setSize(width, height);
    this.rendererContainer.nativeElement.appendChild(this.renderer.domElement);

    // Controls
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.06;
    this.controls.enablePan = false;
    this.controls.minDistance = 10;
    this.controls.maxDistance = 600;
    this.controls.addEventListener('start', () => {
      this.autoRotate = false;
    });

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
    this.autoRotate = true;
    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const loader = new STLLoader();
        const geometry = loader.parse(event.target?.result as ArrayBuffer);

        this.clearCurrentMesh();

        geometry.computeVertexNormals();

        const material = new THREE.MeshStandardMaterial({
          color: this.color,
          roughness: 0.42,
          metalness: 0.05,
          emissive: 0x000000,
          emissiveIntensity: 0,
        });

        this.currentMesh = new THREE.Mesh(geometry, material);
        this.applyColorStyle(this.color);

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
          z: Math.round(size.z * 10) / 10,
        };

        // Rotate to stand upright (usually necessary for STLs)
        this.currentMesh.rotation.x = -Math.PI / 2;

        this.scene.add(this.currentMesh);

        // Adjust camera to fit object and keep it visually centered
        const maxDim = Math.max(size.x, size.y, size.z);
        const fov = this.camera.fov * (Math.PI / 180);

        // Calculate distance towards camera (z-axis)
        let cameraZ = Math.abs(maxDim / 2 / Math.tan(fov / 2));
        cameraZ *= 1.72;

        this.camera.position.set(
          cameraZ * 0.68,
          cameraZ * 0.62,
          cameraZ * 1.08,
        );
        this.controls.target.set(0, 0, 0);
        this.camera.lookAt(0, 0, 0);
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

    if (this.currentMesh && this.autoRotate) {
      this.currentMesh.rotation.z += 0.0025;
    }

    if (this.controls) this.controls.update();
    if (this.renderer && this.scene && this.camera) {
      this.renderer.render(this.scene, this.camera);
    }
  }

  private clearCurrentMesh() {
    if (!this.currentMesh) {
      return;
    }

    this.scene.remove(this.currentMesh);
    this.currentMesh.geometry.dispose();

    const meshMaterial = this.currentMesh.material;
    if (Array.isArray(meshMaterial)) {
      meshMaterial.forEach((m) => m.dispose());
    } else {
      meshMaterial.dispose();
    }

    this.currentMesh = null;
  }

  private applyColorStyle(color: string) {
    if (!this.currentMesh) {
      return;
    }

    const darkColor = this.isDarkColor(color);
    const meshMaterial = this.currentMesh.material;

    if (meshMaterial instanceof THREE.MeshStandardMaterial) {
      meshMaterial.color.set(color);
      if (darkColor) {
        meshMaterial.emissive.set(0x2a2f36);
        meshMaterial.emissiveIntensity = 0.28;
        meshMaterial.roughness = 0.5;
        meshMaterial.metalness = 0.03;
      } else {
        meshMaterial.emissive.set(0x000000);
        meshMaterial.emissiveIntensity = 0;
        meshMaterial.roughness = 0.42;
        meshMaterial.metalness = 0.05;
      }
      meshMaterial.needsUpdate = true;
    }
  }

  private isDarkColor(color: string): boolean {
    const c = new THREE.Color(color);
    const luminance = 0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b;
    return luminance < 0.22;
  }
}
