import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AdminFilamentMaterialType,
  AdminFilamentVariant,
  AdminOperationsService,
  AdminUpsertFilamentMaterialTypePayload,
  AdminUpsertFilamentVariantPayload
} from '../services/admin-operations.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-admin-filament-stock',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-filament-stock.component.html',
  styleUrl: './admin-filament-stock.component.scss'
})
export class AdminFilamentStockComponent implements OnInit {
  private readonly adminOperationsService = inject(AdminOperationsService);

  materials: AdminFilamentMaterialType[] = [];
  variants: AdminFilamentVariant[] = [];
  loading = false;
  materialsCollapsed = true;
  creatingMaterial = false;
  creatingVariant = false;
  savingMaterialIds = new Set<number>();
  savingVariantIds = new Set<number>();
  errorMessage: string | null = null;
  successMessage: string | null = null;

  newMaterial: AdminUpsertFilamentMaterialTypePayload = {
    materialCode: '',
    isFlexible: false,
    isTechnical: false,
    technicalTypeLabel: ''
  };

  newVariant: AdminUpsertFilamentVariantPayload = {
    materialTypeId: 0,
    variantDisplayName: '',
    colorName: '',
    isMatte: false,
    isSpecial: false,
    costChfPerKg: 0,
    stockSpools: 0,
    spoolNetKg: 1,
    isActive: true
  };

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.errorMessage = null;
    this.successMessage = null;

    forkJoin({
      materials: this.adminOperationsService.getFilamentMaterials(),
      variants: this.adminOperationsService.getFilamentVariants()
    }).subscribe({
      next: ({ materials, variants }) => {
        this.materials = this.sortMaterials(materials);
        this.variants = this.sortVariants(variants);
        if (!this.newVariant.materialTypeId && this.materials.length > 0) {
          this.newVariant.materialTypeId = this.materials[0].id;
        }
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = this.extractErrorMessage(err, 'Impossibile caricare i filamenti.');
      }
    });
  }

  createMaterial(): void {
    if (this.creatingMaterial) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;
    this.creatingMaterial = true;

    const payload: AdminUpsertFilamentMaterialTypePayload = {
      materialCode: (this.newMaterial.materialCode || '').trim(),
      isFlexible: !!this.newMaterial.isFlexible,
      isTechnical: !!this.newMaterial.isTechnical,
      technicalTypeLabel: this.newMaterial.isTechnical
        ? (this.newMaterial.technicalTypeLabel || '').trim()
        : ''
    };

    this.adminOperationsService.createFilamentMaterial(payload).subscribe({
      next: (created) => {
        this.materials = this.sortMaterials([...this.materials, created]);
        if (!this.newVariant.materialTypeId) {
          this.newVariant.materialTypeId = created.id;
        }
        this.newMaterial = {
          materialCode: '',
          isFlexible: false,
          isTechnical: false,
          technicalTypeLabel: ''
        };
        this.creatingMaterial = false;
        this.successMessage = 'Materiale aggiunto.';
      },
      error: (err) => {
        this.creatingMaterial = false;
        this.errorMessage = this.extractErrorMessage(err, 'Creazione materiale non riuscita.');
      }
    });
  }

  saveMaterial(material: AdminFilamentMaterialType): void {
    if (this.savingMaterialIds.has(material.id)) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;
    this.savingMaterialIds.add(material.id);

    const payload: AdminUpsertFilamentMaterialTypePayload = {
      materialCode: (material.materialCode || '').trim(),
      isFlexible: !!material.isFlexible,
      isTechnical: !!material.isTechnical,
      technicalTypeLabel: material.isTechnical ? (material.technicalTypeLabel || '').trim() : ''
    };

    this.adminOperationsService.updateFilamentMaterial(material.id, payload).subscribe({
      next: (updated) => {
        this.materials = this.sortMaterials(
          this.materials.map((m) => (m.id === updated.id ? updated : m))
        );
        this.variants = this.variants.map((variant) => {
          if (variant.materialTypeId !== updated.id) {
            return variant;
          }
          return {
            ...variant,
            materialCode: updated.materialCode,
            materialIsFlexible: updated.isFlexible,
            materialIsTechnical: updated.isTechnical,
            materialTechnicalTypeLabel: updated.technicalTypeLabel
          };
        });
        this.savingMaterialIds.delete(material.id);
        this.successMessage = 'Materiale aggiornato.';
      },
      error: (err) => {
        this.savingMaterialIds.delete(material.id);
        this.errorMessage = this.extractErrorMessage(err, 'Aggiornamento materiale non riuscito.');
      }
    });
  }

  createVariant(): void {
    if (this.creatingVariant) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;
    this.creatingVariant = true;

    const payload = this.toVariantPayload(this.newVariant);
    this.adminOperationsService.createFilamentVariant(payload).subscribe({
      next: (created) => {
        this.variants = this.sortVariants([...this.variants, created]);
        this.newVariant = {
          materialTypeId: this.newVariant.materialTypeId || this.materials[0]?.id || 0,
          variantDisplayName: '',
          colorName: '',
          isMatte: false,
          isSpecial: false,
          costChfPerKg: 0,
          stockSpools: 0,
          spoolNetKg: 1,
          isActive: true
        };
        this.creatingVariant = false;
        this.successMessage = 'Variante aggiunta.';
      },
      error: (err) => {
        this.creatingVariant = false;
        this.errorMessage = this.extractErrorMessage(err, 'Creazione variante non riuscita.');
      }
    });
  }

  saveVariant(variant: AdminFilamentVariant): void {
    if (this.savingVariantIds.has(variant.id)) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;
    this.savingVariantIds.add(variant.id);

    const payload = this.toVariantPayload(variant);
    this.adminOperationsService.updateFilamentVariant(variant.id, payload).subscribe({
      next: (updated) => {
        this.variants = this.sortVariants(
          this.variants.map((v) => (v.id === updated.id ? updated : v))
        );
        this.savingVariantIds.delete(variant.id);
        this.successMessage = 'Variante aggiornata.';
      },
      error: (err) => {
        this.savingVariantIds.delete(variant.id);
        this.errorMessage = this.extractErrorMessage(err, 'Aggiornamento variante non riuscito.');
      }
    });
  }

  isLowStock(variant: AdminFilamentVariant): boolean {
    return this.computeStockKg(variant.stockSpools, variant.spoolNetKg) < 1;
  }

  computeStockKg(stockSpools?: number, spoolNetKg?: number): number {
    const spools = Number(stockSpools ?? 0);
    const netKg = Number(spoolNetKg ?? 0);

    if (!Number.isFinite(spools) || !Number.isFinite(netKg) || spools < 0 || netKg < 0) {
      return 0;
    }
    return spools * netKg;
  }

  trackById(index: number, item: { id: number }): number {
    return item.id;
  }

  toggleMaterialsCollapsed(): void {
    this.materialsCollapsed = !this.materialsCollapsed;
  }

  private toVariantPayload(source: AdminUpsertFilamentVariantPayload | AdminFilamentVariant): AdminUpsertFilamentVariantPayload {
    return {
      materialTypeId: Number(source.materialTypeId),
      variantDisplayName: (source.variantDisplayName || '').trim(),
      colorName: (source.colorName || '').trim(),
      isMatte: !!source.isMatte,
      isSpecial: !!source.isSpecial,
      costChfPerKg: Number(source.costChfPerKg ?? 0),
      stockSpools: Number(source.stockSpools ?? 0),
      spoolNetKg: Number(source.spoolNetKg ?? 0),
      isActive: source.isActive !== false
    };
  }

  private sortMaterials(materials: AdminFilamentMaterialType[]): AdminFilamentMaterialType[] {
    return [...materials].sort((a, b) => a.materialCode.localeCompare(b.materialCode));
  }

  private sortVariants(variants: AdminFilamentVariant[]): AdminFilamentVariant[] {
    return [...variants].sort((a, b) => {
      const byMaterial = (a.materialCode || '').localeCompare(b.materialCode || '');
      if (byMaterial !== 0) {
        return byMaterial;
      }
      return (a.variantDisplayName || '').localeCompare(b.variantDisplayName || '');
    });
  }

  private extractErrorMessage(error: unknown, fallback: string): string {
    const err = error as { error?: { message?: string } };
    return err?.error?.message || fallback;
  }
}
