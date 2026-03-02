export interface ColorOption {
  label: string;
  value: string;
  hex: string;
  variantId?: number;
  outOfStock?: boolean;
}

export interface ColorCategory {
  name: string; // 'Glossy' | 'Matte'
  colors: ColorOption[];
}

export const PRODUCT_COLORS: ColorCategory[] = [
  {
    name: 'COLOR.CATEGORY_GLOSSY',
    colors: [
      { label: 'COLOR.NAME.BLACK', value: 'Black', hex: '#1a1a1a' }, // Not pure black for visibility
      { label: 'COLOR.NAME.WHITE', value: 'White', hex: '#f5f5f5' },
      { label: 'COLOR.NAME.RED', value: 'Red', hex: '#d32f2f', outOfStock: true },
      { label: 'COLOR.NAME.BLUE', value: 'Blue', hex: '#1976d2' },
      { label: 'COLOR.NAME.GREEN', value: 'Green', hex: '#388e3c' },
      { label: 'COLOR.NAME.YELLOW', value: 'Yellow', hex: '#fbc02d' }
    ]
  },
  {
    name: 'COLOR.CATEGORY_MATTE',
    colors: [
      { label: 'COLOR.NAME.MATTE_BLACK', value: 'Matte Black', hex: '#2c2c2c' }, // Lighter charcoal for matte
      { label: 'COLOR.NAME.MATTE_WHITE', value: 'Matte White', hex: '#e0e0e0' },
      { label: 'COLOR.NAME.MATTE_GRAY', value: 'Matte Gray', hex: '#757575' }
    ]
  }
];

export function getColorHex(value: string): string {
    for (const cat of PRODUCT_COLORS) {
        const found = cat.colors.find(c => c.value === value);
        if (found) return found.hex;
    }
    return '#facf0a'; // Default Brand Color if not found
}
