export interface ServiceLine {
  readonly lineTotalChf?: number;
  description: string;
  billingType: 'HOURLY' | 'FIXED';
  quantity: number;
  unitPriceChf: number;
}

/** Preview only: the backend remains the source of truth for invoice amounts. */
export function serviceLineTotal(
  quantity: number,
  unitPriceChf: number,
): number {
  return (
    Math.round(
      (Math.round(quantity * 100) * Math.round(unitPriceChf * 100)) / 100,
    ) / 100
  );
}
