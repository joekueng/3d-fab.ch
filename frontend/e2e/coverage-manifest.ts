/** Route decisions are checked against Angular route declarations by check:e2e-routes. */
export interface RouteCoverage {
  source: 'app' | 'about' | 'calculator' | 'contact' | 'legal' | 'shop' | 'admin';
  path: string;
  suite: 'smoke' | 'fullstack';
  scenario: string;
  status: 'covered' | 'partial' | 'planned';
}

export const scenarioCatalog: Record<string, {
  spec: string;
  priority: 'critical' | 'standard';
  fixture: 'public' | 'synthetic-stack' | 'order' | 'cad' | 'slicer';
}> = {
  'SMOKE-001': { spec: 'smoke/public-health.spec.ts', priority: 'critical', fixture: 'public' },
  'SMOKE-002': { spec: 'smoke/public-health.spec.ts', priority: 'standard', fixture: 'public' },
  'SMOKE-003': { spec: 'smoke/public-health.spec.ts', priority: 'critical', fixture: 'public' },
  'PUBLIC-001': { spec: 'public/navigation.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
  'PUBLIC-002': { spec: 'public/navigation.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
  'PUBLIC-003': { spec: 'public/navigation.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
  'PUBLIC-004': { spec: 'public/contact-submission.spec.ts', priority: 'critical', fixture: 'synthetic-stack' },
  'RENDER-001': { spec: 'rendering/ssr.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
  'RENDER-002': { spec: 'rendering/ssr.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
  'RENDER-003': { spec: 'rendering/ssr.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
  'RENDER-004': { spec: 'rendering/ssr.spec.ts', priority: 'critical', fixture: 'synthetic-stack' },
  'RENDER-005': { spec: 'rendering/ssr.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
  'RENDER-006': { spec: 'rendering/ssr.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
  'RENDER-007': { spec: 'rendering/accessibility.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
  'RENDER-008': { spec: 'rendering/media-boundary.spec.ts', priority: 'critical', fixture: 'synthetic-stack' },
  'CALC-001': { spec: 'calculator/quote.spec.ts', priority: 'critical', fixture: 'synthetic-stack' },
  'CALC-002': { spec: 'calculator/quote.spec.ts', priority: 'critical', fixture: 'slicer' },
  'SHOP-001': { spec: 'shop/catalog-cart.spec.ts', priority: 'critical', fixture: 'synthetic-stack' },
  'SHOP-002': { spec: 'shop/catalog-cart.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
  'SHOP-003': { spec: 'shop/seeded-price.spec.ts', priority: 'critical', fixture: 'synthetic-stack' },
  'CHECKOUT-001': { spec: 'checkout/shop-checkout.spec.ts', priority: 'critical', fixture: 'synthetic-stack' },
  'CAD-001': { spec: 'checkout/cad-checkout.spec.ts', priority: 'critical', fixture: 'cad' },
  'ORDER-001': { spec: 'orders/tracking.spec.ts', priority: 'critical', fixture: 'order' },
  'ORDER-002': { spec: 'orders/tracking.spec.ts', priority: 'standard', fixture: 'order' },
  'ORDER-003': { spec: 'orders/admin-confirmation.spec.ts', priority: 'critical', fixture: 'order' },
  'ADMIN-001': { spec: 'admin/admin-auth.spec.ts', priority: 'critical', fixture: 'synthetic-stack' },
  'ADMIN-002': { spec: 'admin/admin-auth.spec.ts', priority: 'critical', fixture: 'synthetic-stack' },
  'ADMIN-003': { spec: 'admin/admin-auth.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
  'ADMIN-QR-001': { spec: 'admin/qr.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
  'ADMIN-FILAMENT-001': { spec: 'admin/filament-stock.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
  'UI-001': { spec: 'ui-states/catalog-failure.spec.ts', priority: 'standard', fixture: 'synthetic-stack' },
};

export const routeCoverage: RouteCoverage[] = [
  { source: 'app', path: '', suite: 'smoke', scenario: 'SMOKE-001', status: 'covered' },
  { source: 'app', path: 'calculator', suite: 'fullstack', scenario: 'CALC-001', status: 'partial' },
  { source: 'app', path: 'shop', suite: 'fullstack', scenario: 'SHOP-001', status: 'partial' },
  { source: 'app', path: 'about', suite: 'smoke', scenario: 'SMOKE-002', status: 'covered' },
  { source: 'app', path: 'materials', suite: 'smoke', scenario: 'SMOKE-002', status: 'partial' },
  { source: 'app', path: 'contact', suite: 'fullstack', scenario: 'PUBLIC-003', status: 'partial' },
  { source: 'app', path: 'checkout/cad', suite: 'fullstack', scenario: 'CAD-001', status: 'partial' },
  { source: 'app', path: 'checkout', suite: 'fullstack', scenario: 'CHECKOUT-001', status: 'partial' },
  { source: 'app', path: 'order/:orderId', suite: 'fullstack', scenario: 'ORDER-001', status: 'partial' },
  { source: 'app', path: 'co/:orderId', suite: 'fullstack', scenario: 'ORDER-002', status: 'partial' },
  { source: 'app', path: 'admin', suite: 'fullstack', scenario: 'ADMIN-002', status: 'partial' },
  { source: 'app', path: '**', suite: 'fullstack', scenario: 'RENDER-005', status: 'covered' },
  { source: 'app', path: ':lang/calculator/animation-test', suite: 'fullstack', scenario: 'RENDER-006', status: 'partial' },
  { source: 'app', path: 'calculator/animation-test', suite: 'fullstack', scenario: 'RENDER-006', status: 'partial' },
  { source: 'app', path: ':lang', suite: 'smoke', scenario: 'SMOKE-002', status: 'covered' },
  { source: 'about', path: '', suite: 'smoke', scenario: 'SMOKE-002', status: 'covered' },
  { source: 'calculator', path: '', suite: 'fullstack', scenario: 'CALC-001', status: 'covered' },
  { source: 'calculator', path: 'animation-test', suite: 'fullstack', scenario: 'RENDER-006', status: 'partial' },
  { source: 'calculator', path: 'basic', suite: 'fullstack', scenario: 'CALC-001', status: 'partial' },
  { source: 'calculator', path: 'advanced', suite: 'fullstack', scenario: 'CALC-001', status: 'partial' },
  { source: 'contact', path: '', suite: 'fullstack', scenario: 'PUBLIC-003', status: 'partial' },
  { source: 'legal', path: 'privacy', suite: 'smoke', scenario: 'SMOKE-002', status: 'covered' },
  { source: 'legal', path: 'terms', suite: 'smoke', scenario: 'SMOKE-002', status: 'covered' },
  { source: 'shop', path: '', suite: 'fullstack', scenario: 'SHOP-001', status: 'partial' },
  { source: 'shop', path: 'p/:productSlug', suite: 'fullstack', scenario: 'SHOP-002', status: 'partial' },
  { source: 'shop', path: ':categorySlug/:productSlug', suite: 'fullstack', scenario: 'SHOP-002', status: 'partial' },
  { source: 'shop', path: ':categorySlug', suite: 'fullstack', scenario: 'SHOP-002', status: 'partial' },
  { source: 'admin', path: '', suite: 'fullstack', scenario: 'ADMIN-002', status: 'covered' },
  { source: 'admin', path: 'login', suite: 'fullstack', scenario: 'ADMIN-002', status: 'partial' },
  { source: 'admin', path: 'orders', suite: 'fullstack', scenario: 'ADMIN-002', status: 'partial' },
  { source: 'admin', path: 'filament-stock', suite: 'fullstack', scenario: 'ADMIN-002', status: 'partial' },
  { source: 'admin', path: 'contact-requests', suite: 'fullstack', scenario: 'ADMIN-002', status: 'partial' },
  { source: 'admin', path: 'sessions', suite: 'fullstack', scenario: 'ADMIN-002', status: 'partial' },
  { source: 'admin', path: 'cad-invoices', suite: 'fullstack', scenario: 'ADMIN-002', status: 'partial' },
  { source: 'admin', path: 'qr', suite: 'fullstack', scenario: 'ADMIN-QR-001', status: 'partial' },
  { source: 'admin', path: 'home-media', suite: 'fullstack', scenario: 'ADMIN-002', status: 'covered' },
  { source: 'admin', path: 'media', suite: 'fullstack', scenario: 'ADMIN-002', status: 'partial' },
  { source: 'admin', path: 'home-projects', suite: 'fullstack', scenario: 'ADMIN-002', status: 'partial' },
  { source: 'admin', path: 'shop', suite: 'fullstack', scenario: 'ADMIN-002', status: 'partial' },
  { source: 'admin', path: 'linkedin', suite: 'fullstack', scenario: 'ADMIN-002', status: 'partial' },
];

export const plannedWorkflows = [
  'Contact attachment scanning, request variants and attachment notifications',
  'Calculator error/rate-limit/recovery and private draft information',
  'Mixed checkout, repeated payment reporting and deeper PDF/ZIP content checks',
  'Admin CRUD and error recovery beyond QR creation',
  'QR scan statistics, media conversion and external provider failures',
  'Cross-browser execution, keyboard/mobile interactions and visual baselines',
] as const;
