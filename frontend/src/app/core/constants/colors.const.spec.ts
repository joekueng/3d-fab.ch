import {compareColorHex} from './colors.const';

describe('compareColorHex', () => {
  it('orders white, rainbow hues, dark neutrals, and invalid colors', () => {
    const colors = ['#000000', '#0000ff', '#ff0000', '#f5f5f5', '#00ff00', '#ffff00', '#888888', 'invalid', '#ff00ff'];
    expect([...colors].sort(compareColorHex)).toEqual([
      '#f5f5f5', '#ff0000', '#ffff00', '#00ff00', '#0000ff', '#ff00ff', '#888888', '#000000', 'invalid',
    ]);
    expect(colors[0]).toBe('#000000');
  });
});
