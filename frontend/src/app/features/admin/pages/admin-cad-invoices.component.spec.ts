import { serviceLineTotal } from '../../../shared/models/service-line';
import { parseDecimalInput } from './admin-cad-invoices.component';

describe('parseDecimalInput', () => {
  it('accepts both decimal points and decimal commas', () => {
    expect(parseDecimalInput('1.5')).toBe(1.5);
    expect(parseDecimalInput('1,5')).toBe(1.5);
  });

  it('rejects malformed decimal values', () => {
    expect(parseDecimalInput('1.2.3')).toBeNaN();
    expect(parseDecimalInput('')).toBeNaN();
  });
});

describe('service invoice preview rounding', () => {
  it('rounds hourly amounts to cents using decimal quantities and rates', () => {
    expect(serviceLineTotal(1.25, 85.5)).toBe(106.88);
    expect(serviceLineTotal(0.01, 100.5)).toBe(1.01);
    expect(serviceLineTotal(1, 50)).toBe(50);
  });
});
