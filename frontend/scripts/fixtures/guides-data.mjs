// Synthetic fixtures only. Never used by the application or for public pricing.
const localized = {
  it: {
    black: "Nero campione",
    white: "Bianco campione",
    blue: "Blu campione",
    title: "Componente di prova",
    alt: "Campione sintetico per test",
    settings: "Ugello 0.4 mm, strato 0.2 mm, riempimento 15%, senza supporti",
    costs: "Stampa, preparazione e IVA incluse; spedizione esclusa",
  },
  en: {
    black: "Black sample",
    white: "White sample",
    blue: "Blue sample",
    title: "Test component",
    alt: "Synthetic sample for tests",
    settings: "0.4 mm nozzle, 0.2 mm layers, 15% infill, no supports",
    costs: "Printing, preparation and VAT included; shipping excluded",
  },
  de: {
    black: "Schwarzes Muster",
    white: "Weisses Muster",
    blue: "Blaues Muster",
    title: "Testbauteil",
    alt: "Synthetisches Testmuster",
    settings: "0.4 mm Düse, 0.2 mm Schichten, 15% Fülldichte, ohne Stützen",
    costs: "Druck, Vorbereitung und MWST enthalten; Versand ausgeschlossen",
  },
  fr: {
    black: "Échantillon noir",
    white: "Échantillon blanc",
    blue: "Échantillon bleu",
    title: "Pièce de test",
    alt: "Échantillon synthétique de test",
    settings: "Buse 0.4 mm, couches 0.2 mm, remplissage 15%, sans supports",
    costs: "Impression, préparation et TVA incluses ; transport exclu",
  },
};
const variant = (id, color, hexColor, finishType, isOutOfStock = false) => ({
  id,
  name: color,
  colorName: color,
  hexColor,
  finishType,
  isOutOfStock,
  stockSpools: isOutOfStock ? 0 : 1,
  stockFilamentGrams: isOutOfStock ? 0 : 1000,
  ...Object.fromEntries(
    Object.entries(localized).map(([lang, texts]) => [
      "colorLabel" + lang[0].toUpperCase() + lang.slice(1),
      texts[color],
    ]),
  ),
});
export const options = {
  materials: [
    {
      code: "PLA",
      label: "PLA",
      isTechnical: false,
      variants: [
        variant(101, "black", "#222222", "MATTE"),
        variant(102, "white", "#eeeeee", "GLOSSY", true),
      ],
    },
    {
      code: "PETG",
      label: "PETG",
      isTechnical: false,
      variants: [variant(201, "blue", "#165dcc", "TRANSLUCENT")],
    },
  ],
  qualities: [{ id: "standard", label: "Standard" }],
  infillPatterns: [{ id: "gyroid", label: "Gyroid" }],
  layerHeights: [{ value: 0.2, label: "0.2 mm" }],
  nozzleDiameters: [{ value: 0.4, label: "0.4 mm" }],
  layerHeightsByNozzle: [],
};
export function mediaUsage(type, key, lang = "it") {
  if (type === "FILAMENT_VARIANT" && key !== "101") return [];
  if (!["FILAMENT_VARIANT", "PRICING_EXAMPLE"].includes(type)) return [];
  const texts = localized[lang] ?? localized.it;
  const pricingExample =
    type === "PRICING_EXAMPLE"
      ? {
          material: "PLA",
          dimensionsMm: "20 × 20 × 10",
          quantity: key === "series" ? 10 : 1,
          priceChf: key === "series" ? 60 : 12.5,
          quotedOn: "2026-10-01",
          verified: true,
          settings: Object.fromEntries(
            Object.entries(localized).map(([lang, text]) => [
              lang,
              text.settings,
            ]),
          ),
          costsIncluded: Object.fromEntries(
            Object.entries(localized).map(([lang, text]) => [lang, text.costs]),
          ),
        }
      : null;
  return Array.from(
    { length: type === "FILAMENT_VARIANT" ? 3 : 1 },
    (_, i) => ({
      mediaAssetId: `synthetic-${type}-${key}-${i}`,
      usageType: type,
      usageKey: key,
      title: texts.title,
      altText: `${texts.alt} ${i + 1}`,
      sortOrder: i,
      isPrimary: i === 1,
      pricingExample,
      card: { jpegUrl: `/__fixtures/sample-${i}.svg` },
      hero: { jpegUrl: `/__fixtures/sample-${i}.svg` },
      thumb: null,
    }),
  );
}
