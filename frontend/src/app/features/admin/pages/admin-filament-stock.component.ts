import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AdminFilamentMaterialType,
  AdminFilamentVariant,
  AdminOperationsService,
  AdminUpsertFilamentMaterialTypePayload,
  AdminUpsertFilamentVariantPayload,
} from '../services/admin-operations.service';
import { forkJoin } from 'rxjs';
import { getColorHex } from '../../../core/constants/colors.const';
import { AppButtonComponent } from '../../../shared/components/app-button/app-button.component';
import { AppCheckboxComponent } from '../../../shared/components/app-checkbox/app-checkbox.component';
import { AppInputComponent } from '../../../shared/components/app-input/app-input.component';
import { AppSelectComponent } from '../../../shared/components/app-select/app-select.component';

@Component({
  selector: 'app-admin-filament-stock',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    AppButtonComponent,
    AppCheckboxComponent,
    AppInputComponent,
    AppSelectComponent,
  ],
  templateUrl: './admin-filament-stock.component.html',
  styleUrl: './admin-filament-stock.component.scss',
})
export class AdminFilamentStockComponent implements OnInit {
  private readonly adminOperationsService = inject(AdminOperationsService);
  readonly finishTypeOptions = [
    { label: 'GLOSSY', value: 'GLOSSY' },
    { label: 'MATTE', value: 'MATTE' },
    { label: 'MARBLE', value: 'MARBLE' },
    { label: 'SILK', value: 'SILK' },
    { label: 'TRANSLUCENT', value: 'TRANSLUCENT' },
    { label: 'SPECIAL', value: 'SPECIAL' },
  ];

  materials: AdminFilamentMaterialType[] = [];
  variants: AdminFilamentVariant[] = [];
  loading = false;
  quickInsertCollapsed = false;
  materialsCollapsed = true;
  creatingMaterial = false;
  creatingVariant = false;
  savingMaterialIds = new Set<number>();
  savingVariantIds = new Set<number>();
  deletingVariantIds = new Set<number>();
  expandedVariantIds = new Set<number>();
  variantToDelete: AdminFilamentVariant | null = null;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  newMaterial: AdminUpsertFilamentMaterialTypePayload = {
    materialCode: '',
    isFlexible: false,
    isTechnical: false,
    technicalTypeLabel: '',
  };

  newVariant: AdminUpsertFilamentVariantPayload = {
    materialTypeId: 0,
    variantDisplayName: '',
    colorName: '',
    colorLabelIt: '',
    colorLabelEn: '',
    colorLabelDe: '',
    colorLabelFr: '',
    colorHex: '',
    finishType: 'GLOSSY',
    brand: '',
    isMatte: false,
    isSpecial: false,
    costChfPerKg: 0,
    stockSpools: 0,
    spoolNetKg: 1,
    isActive: true,
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
      variants: this.adminOperationsService.getFilamentVariants(),
    }).subscribe({
      next: ({ materials, variants }) => {
        this.materials = this.sortMaterials(materials);
        this.variants = this.sortVariants(variants);
        const existingIds = new Set(this.variants.map((v) => v.id));
        this.expandedVariantIds.forEach((id) => {
          if (!existingIds.has(id)) {
            this.expandedVariantIds.delete(id);
          }
        });
        if (!this.newVariant.materialTypeId && this.materials.length > 0) {
          this.newVariant.materialTypeId = this.materials[0].id;
        }
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = this.extractErrorMessage(
          err,
          'Impossibile caricare i filamenti.',
        );
      },
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
        : '',
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
          technicalTypeLabel: '',
        };
        this.creatingMaterial = false;
        this.successMessage = 'Materiale aggiunto.';
      },
      error: (err) => {
        this.creatingMaterial = false;
        this.errorMessage = this.extractErrorMessage(
          err,
          'Creazione materiale non riuscita.',
        );
      },
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
      technicalTypeLabel: material.isTechnical
        ? (material.technicalTypeLabel || '').trim()
        : '',
    };

    this.adminOperationsService
      .updateFilamentMaterial(material.id, payload)
      .subscribe({
        next: (updated) => {
          this.materials = this.sortMaterials(
            this.materials.map((m) => (m.id === updated.id ? updated : m)),
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
              materialTechnicalTypeLabel: updated.technicalTypeLabel,
            };
          });
          this.savingMaterialIds.delete(material.id);
          this.successMessage = 'Materiale aggiornato.';
        },
        error: (err) => {
          this.savingMaterialIds.delete(material.id);
          this.errorMessage = this.extractErrorMessage(
            err,
            'Aggiornamento materiale non riuscito.',
          );
        },
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
          materialTypeId:
            this.newVariant.materialTypeId || this.materials[0]?.id || 0,
          variantDisplayName: '',
          colorName: '',
          colorLabelIt: '',
          colorLabelEn: '',
          colorLabelDe: '',
          colorLabelFr: '',
          colorHex: '',
          finishType: 'GLOSSY',
          brand: '',
          isMatte: false,
          isSpecial: false,
          costChfPerKg: 0,
          stockSpools: 0,
          spoolNetKg: 1,
          isActive: true,
        };
        this.creatingVariant = false;
        this.successMessage = 'Variante aggiunta.';
      },
      error: (err) => {
        this.creatingVariant = false;
        this.errorMessage = this.extractErrorMessage(
          err,
          'Creazione variante non riuscita.',
        );
      },
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
    this.adminOperationsService
      .updateFilamentVariant(variant.id, payload)
      .subscribe({
        next: (updated) => {
          this.variants = this.sortVariants(
            this.variants.map((v) => (v.id === updated.id ? updated : v)),
          );
          this.savingVariantIds.delete(variant.id);
          this.successMessage = 'Variante aggiornata.';
        },
        error: (err) => {
          this.savingVariantIds.delete(variant.id);
          this.errorMessage = this.extractErrorMessage(
            err,
            'Aggiornamento variante non riuscito.',
          );
        },
      });
  }

  isLowStock(variant: AdminFilamentVariant): boolean {
    return (
      this.computeStockFilamentGrams(variant.stockSpools, variant.spoolNetKg) <
      1000
    );
  }

  computeStockKg(stockSpools?: number, spoolNetKg?: number): number {
    const spools = Number(stockSpools ?? 0);
    const netKg = Number(spoolNetKg ?? 0);

    if (
      !Number.isFinite(spools) ||
      !Number.isFinite(netKg) ||
      spools < 0 ||
      netKg < 0
    ) {
      return 0;
    }
    return spools * netKg;
  }

  computeStockFilamentGrams(stockSpools?: number, spoolNetKg?: number): number {
    return this.computeStockKg(stockSpools, spoolNetKg) * 1000;
  }

  trackById(index: number, item: { id: number }): number {
    return item.id;
  }

  isVariantExpanded(variantId: number): boolean {
    return this.expandedVariantIds.has(variantId);
  }

  toggleVariantExpanded(variantId: number): void {
    if (this.expandedVariantIds.has(variantId)) {
      this.expandedVariantIds.delete(variantId);
      return;
    }
    this.expandedVariantIds.add(variantId);
  }

  getVariantColorHex(variant: AdminFilamentVariant): string {
    if (variant.colorHex && variant.colorHex.trim().length > 0) {
      return variant.colorHex;
    }
    return getColorHex(variant.colorName || '');
  }

  openDeleteVariant(variant: AdminFilamentVariant): void {
    this.variantToDelete = variant;
  }

  closeDeleteVariantDialog(): void {
    this.variantToDelete = null;
  }

  confirmDeleteVariant(): void {
    const variant = this.variantToDelete;
    if (!variant || this.deletingVariantIds.has(variant.id)) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;
    this.deletingVariantIds.add(variant.id);

    this.adminOperationsService.deleteFilamentVariant(variant.id).subscribe({
      next: () => {
        this.variants = this.variants.filter((v) => v.id !== variant.id);
        this.expandedVariantIds.delete(variant.id);
        this.deletingVariantIds.delete(variant.id);
        this.variantToDelete = null;
        this.successMessage = 'Variante eliminata.';
      },
      error: (err) => {
        this.deletingVariantIds.delete(variant.id);
        this.errorMessage = this.extractErrorMessage(
          err,
          'Eliminazione variante non riuscita.',
        );
      },
    });
  }

  toggleMaterialsCollapsed(): void {
    this.materialsCollapsed = !this.materialsCollapsed;
  }

  toggleQuickInsertCollapsed(): void {
    this.quickInsertCollapsed = !this.quickInsertCollapsed;
  }

  materialTypeOptions(): { label: string; value: number }[] {
    return this.materials.map((material) => ({
      label: material.materialCode,
      value: material.id,
    }));
  }

  private toVariantPayload(
    source: AdminUpsertFilamentVariantPayload | AdminFilamentVariant,
  ): AdminUpsertFilamentVariantPayload {
    return {
      materialTypeId: Number(source.materialTypeId),
      variantDisplayName: (source.variantDisplayName || '').trim(),
      colorName: (source.colorName || '').trim(),
      colorLabelIt: (source.colorLabelIt || '').trim() || undefined,
      colorLabelEn: (source.colorLabelEn || '').trim() || undefined,
      colorLabelDe: (source.colorLabelDe || '').trim() || undefined,
      colorLabelFr: (source.colorLabelFr || '').trim() || undefined,
      colorHex: (source.colorHex || '').trim() || undefined,
      finishType: (source.finishType || 'GLOSSY').trim().toUpperCase(),
      brand: (source.brand || '').trim() || undefined,
      isMatte: !!source.isMatte,
      isSpecial: !!source.isSpecial,
      costChfPerKg: Number(source.costChfPerKg ?? 0),
      stockSpools: Number(source.stockSpools ?? 0),
      spoolNetKg: Number(source.spoolNetKg ?? 0),
      isActive: source.isActive !== false,
    };
  }

  private sortMaterials(
    materials: AdminFilamentMaterialType[],
  ): AdminFilamentMaterialType[] {
    return [...materials].sort((a, b) =>
      a.materialCode.localeCompare(b.materialCode),
    );
  }

  private sortVariants(
    variants: AdminFilamentVariant[],
  ): AdminFilamentVariant[] {
    return [...variants].sort((a, b) => {
      const byMaterial = (a.materialCode || '').localeCompare(
        b.materialCode || '',
      );
      if (byMaterial !== 0) {
        return byMaterial;
      }
      return (a.variantDisplayName || '').localeCompare(
        b.variantDisplayName || '',
      );
    });
  }

  private extractErrorMessage(error: unknown, fallback: string): string {
    const err = error as { error?: { message?: string } };
    return err?.error?.message || fallback;
  }
}
