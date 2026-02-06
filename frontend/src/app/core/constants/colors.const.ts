export interface ColorOption {
  label: string;
  value: string;
  hex: string;
  outOfStock?: boolean;
}

export interface ColorCategory {
  name: string; // 'Glossy' | 'Matte'
  colors: ColorOption[];
}

export const PRODUCT_COLORS: ColorCategory[] = [
  {
    name: 'Lucidi', // Glossy
    colors: [
      { label: 'Black', value: 'Black', hex: '#1a1a1a' }, // Not pure black for visibility
      { label: 'White', value: 'White', hex: '#f5f5f5' },
      { label: 'Red', value: 'Red', hex: '#d32f2f', outOfStock: true },
      { label: 'Blue', value: 'Blue', hex: '#1976d2' },
      { label: 'Green', value: 'Green', hex: '#388e3c' },
      { label: 'Yellow', value: 'Yellow', hex: '#fbc02d' }
    ]
  },
  {
    name: 'Opachi', // Matte
    colors: [
      { label: 'Matte Black', value: 'Matte Black', hex: '#2c2c2c' }, // Lighter charcoal for matte
      { label: 'Matte White', value: 'Matte White', hex: '#e0e0e0' },
      { label: 'Matte Gray', value: 'Matte Gray', hex: '#757575' }
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
