import { AdminFilamentVariant } from '../services/admin-operations.service';
import { ProductMaterialFormState } from './admin-shop.types';

export function getStockMaterialCodes(
  stockFilamentVariants: AdminFilamentVariant[],
): string[] {
  return Array.from(
    new Set(
      stockFilamentVariants.map((variant) =>
        variant.materialCode.trim().toUpperCase(),
      ),
    ),
  ).sort((left, right) => left.localeCompare(right));
}

export function getStockVariantsForMaterial(
  stockFilamentVariants: AdminFilamentVariant[],
  materialCode: string,
): AdminFilamentVariant[] {
  const targetMaterialCode = materialCode.trim().toUpperCase();
  const seenKeys = new Set<string>();

  return stockFilamentVariants
    .filter(
      (variant) =>
        variant.materialCode.trim().toUpperCase() === targetMaterialCode,
    )
    .sort((left, right) => {
      const leftName = `${left.colorName} ${left.variantDisplayName}`.trim();
      const rightName = `${right.colorName} ${right.variantDisplayName}`.trim();
      return leftName.localeCompare(rightName);
    })
    .filter((variant) => {
      const key = stockVariantKey(
        targetMaterialCode,
        variant.colorName,
        variant.colorHex,
      );
      if (seenKeys.has(key)) {
        return false;
      }
      seenKeys.add(key);
      return true;
    });
}

export function resolveStockMaterialDefaultColorKey(
  stockFilamentVariants: AdminFilamentVariant[],
  materialCode: string,
  preferredKey?: string | null,
): string {
  const normalizedMaterialCode = materialCode.trim().toUpperCase();
  const stockVariants = getStockVariantsForMaterial(
    stockFilamentVariants,
    normalizedMaterialCode,
  );
  if (stockVariants.length === 0) {
    return '';
  }

  const normalizedPreferredKey = (preferredKey ?? '').trim();
  if (
    normalizedPreferredKey &&
    stockVariants.some(
      (variant) =>
        stockVariantKey(
          normalizedMaterialCode,
          variant.colorName,
          variant.colorHex,
        ) === normalizedPreferredKey,
    )
  ) {
    return normalizedPreferredKey;
  }

  const firstVariant = stockVariants[0];
  return stockVariantKey(
    normalizedMaterialCode,
    firstVariant.colorName,
    firstVariant.colorHex,
  );
}

export function stockVariantLabel(variant: AdminFilamentVariant): string {
  const colorName = (variant.colorLabelIt || variant.colorName).trim();
  const variantDisplayName = variant.variantDisplayName.trim();
  if (
    variantDisplayName &&
    variantDisplayName.toLowerCase() !== colorName.toLowerCase()
  ) {
    return `${colorName} (${variantDisplayName})`;
  }
  return colorName;
}

export function getNextAvailableMaterialCode(
  stockFilamentVariants: AdminFilamentVariant[],
  materials: ProductMaterialFormState[],
): string | null {
  const selectedCodes = new Set(
    materials
      .map((material) => material.materialCode.trim().toUpperCase())
      .filter(Boolean),
  );

  return (
    getStockMaterialCodes(stockFilamentVariants).find(
      (materialCode) => !selectedCodes.has(materialCode),
    ) ?? null
  );
}

export function filterStockedFilamentVariants(
  filamentVariants: AdminFilamentVariant[],
): AdminFilamentVariant[] {
  return filamentVariants.filter(
    (variant) =>
      variant.isActive &&
      Number(variant.stockFilamentGrams ?? 0) > 0 &&
      !!variant.materialCode?.trim() &&
      !!variant.colorName?.trim(),
  );
}

export function stockVariantKey(
  materialCode: string | null | undefined,
  colorName: string | null | undefined,
  colorHex: string | null | undefined,
): string {
  return [
    (materialCode ?? '').trim().toUpperCase(),
    (colorName ?? '').trim().toLowerCase(),
    (colorHex ?? '').trim().toUpperCase(),
  ].join('|');
}
