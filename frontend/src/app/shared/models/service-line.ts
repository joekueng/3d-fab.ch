export interface ServiceLine {
  description: string;
  billingType: 'HOURLY' | 'FIXED';
  quantity: number;
  unitPriceChf: number;
}
