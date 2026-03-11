import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import {
  AdminShopCategory,
  AdminShopProduct,
  AdminShopProductModel,
  AdminShopProductVariant,
  AdminShopService,
  AdminUpsertShopCategoryPayload,
  AdminUpsertShopProductPayload,
  AdminUpsertShopProductVariantPayload,
  AdminPublicMediaUsage,
} from '../services/admin-shop.service';
import {
  AdminFilamentVariant,
  AdminOperationsService,
} from '../services/admin-operations.service';
import {
  AdminMediaLanguage,
  AdminMediaTranslation,
} from '../services/admin-media.service';
import { environment } from '../../../../environments/environment';

type ShopLanguage = 'it' | 'en' | 'de' | 'fr';
type ProductMode = 'create' | 'edit';
type ProductStatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE' | 'FEATURED';

interface CategoryFormState {
  id: string | null;
  parentCategoryId: string | null;
  slug: string;
  name: string;
  description: string;
  seoTitle: string;
  seoDescription: string;
  ogTitle: string;
  ogDescription: string;
  indexable: boolean;
  isActive: boolean;
  sortOrder: number;
}

interface ProductMaterialFormState {
  materialCode: string;
  priceChf: string;
  isDefault: boolean;
  isActive: boolean;
  sortOrder: number;
}

interface ProductFormState {
  categoryId: string;
  slug: string;
  names: Record<ShopLanguage, string>;
  excerpts: Record<ShopLanguage, string>;
  descriptions: Record<ShopLanguage, string>;
  seoTitles: Record<ShopLanguage, string>;
  seoDescriptions: Record<ShopLanguage, string>;
  indexable: boolean;
  isFeatured: boolean;
  isActive: boolean;
  sortOrder: number;
  materials: ProductMaterialFormState[];
}

interface ProductImageItem {
  usageId: string;
  mediaAssetId: string;
  previewUrl: string | null;
  sortOrder: number;
  draftSortOrder: number;
  isPrimary: boolean;
  createdAt: string;
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>;
  title: string;
  altText: string;
}

interface ProductImageUploadState {
  file: File | null;
  previewUrl: string | null;
  activeLanguage: AdminMediaLanguage;
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>;
  sortOrder: number;
  isPrimary: boolean;
  saving: boolean;
}

const SHOP_LANGUAGES: readonly ShopLanguage[] = ['it', 'en', 'de', 'fr'];
const MEDIA_LANGUAGES: readonly AdminMediaLanguage[] = ['it', 'en', 'de', 'fr'];
const LANGUAGE_LABELS: Readonly<Record<ShopLanguage, string>> = {
  it: 'IT',
  en: 'EN',
  de: 'DE',
  fr: 'FR',
};
const PRODUCT_STATUS_FILTERS: readonly ProductStatusFilter[] = [
  'ALL',
  'ACTIVE',
  'INACTIVE',
  'FEATURED',
];
const MAX_MODEL_FILE_SIZE_BYTES = 100 * 1024 * 1024;
const SHOP_LIST_PANEL_WIDTH_STORAGE_KEY = 'admin-shop-list-panel-width';
const MIN_LIST_PANEL_WIDTH_PERCENT = 32;
const MAX_LIST_PANEL_WIDTH_PERCENT = 68;
const RICH_TEXT_ALLOWED_TAGS = new Set([
  'P',
  'DIV',
  'BR',
  'STRONG',
  'B',
  'EM',
  'I',
  'U',
  'UL',
  'OL',
  'LI',
  'A',
]);

@Component({
  selector: 'app-admin-shop',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-shop.component.html',
  styleUrl: './admin-shop.component.scss',
})
export class AdminShopComponent implements OnInit, OnDestroy {
  private readonly adminShopService = inject(AdminShopService);
  private readonly adminOperationsService = inject(AdminOperationsService);
  private descriptionEditorElement: HTMLDivElement | null = null;
  @ViewChild('workspaceRef')
  private readonly workspaceRef?: ElementRef<HTMLDivElement>;
  @ViewChild('descriptionEditorRef')
  set descriptionEditorRef(value: ElementRef<HTMLDivElement> | undefined) {
    this.descriptionEditorElement = value?.nativeElement ?? null;
    this.renderActiveDescriptionInEditor();
  }

  readonly shopLanguages = SHOP_LANGUAGES;
  readonly mediaLanguages = MEDIA_LANGUAGES;
  readonly languageLabels = LANGUAGE_LABELS;
  readonly productStatusFilters = PRODUCT_STATUS_FILTERS;
  readonly maxModelFileSizeMb = Math.round(
    MAX_MODEL_FILE_SIZE_BYTES / (1024 * 1024),
  );

  listPanelWidthPercent = 53;
  categories: AdminShopCategory[] = [];
  products: AdminShopProduct[] = [];
  stockFilamentVariants: AdminFilamentVariant[] = [];
  filteredProducts: AdminShopProduct[] = [];
  selectedProduct: AdminShopProduct | null = null;
  selectedProductId: string | null = null;
  productImages: ProductImageItem[] = [];

  loading = false;
  detailLoading = false;
  savingProduct = false;
  deletingProduct = false;
  savingCategory = false;
  deletingCategory = false;
  uploadingModel = false;
  deletingModel = false;
  imageActionIds = new Set<string>();
  isResizingPanels = false;

  productMode: ProductMode = 'create';
  productSearchTerm = '';
  categoryFilter = 'ALL';
  productStatusFilter: ProductStatusFilter = 'ALL';
  showCategoryManager = false;
  activeContentLanguage: ShopLanguage = 'it';

  errorMessage: string | null = null;
  successMessage: string | null = null;

  readonly categoryForm: CategoryFormState = this.createEmptyCategoryForm();
  readonly productForm: ProductFormState = this.createEmptyProductForm();
  imageUploadState: ProductImageUploadState =
    this.createEmptyImageUploadState();
  modelUploadFile: File | null = null;

  ngOnInit(): void {
    this.restoreListPanelWidth();
    this.loadWorkspace();
  }

  ngOnDestroy(): void {
    this.revokeImagePreviewUrl(this.imageUploadState.previewUrl);
    document.body.style.removeProperty('cursor');
  }

  @HostListener('window:pointermove', ['$event'])
  onWindowPointerMove(event: PointerEvent): void {
    if (!this.isResizingPanels) {
      return;
    }
    this.updateListPanelWidthFromPointer(event.clientX);
  }

  @HostListener('window:pointerup')
  @HostListener('window:pointercancel')
  onWindowPointerUp(): void {
    if (!this.isResizingPanels) {
      return;
    }
    this.isResizingPanels = false;
    document.body.style.cursor = '';
    this.persistListPanelWidth();
  }

  loadWorkspace(preferredProductId?: string): void {
    this.loading = true;
    this.errorMessage = null;

    forkJoin({
      categories: this.adminShopService.getCategories(),
      products: this.adminShopService.getProducts(),
      filamentVariants: this.adminOperationsService.getFilamentVariants(),
    }).subscribe({
      next: ({ categories, products, filamentVariants }) => {
        this.categories = categories;
        this.products = products;
        this.stockFilamentVariants =
          this.filterStockedFilamentVariants(filamentVariants);
        this.applyProductFilters();
        this.ensureCategoryFilterStillValid();
        this.loading = false;

        const targetProductId =
          preferredProductId ??
          (this.productMode === 'edit' ? this.selectedProductId : null);
        if (
          targetProductId &&
          products.some((product) => product.id === targetProductId)
        ) {
          this.openProduct(targetProductId);
          return;
        }

        if (this.productMode === 'create') {
          this.selectedProduct = null;
          this.selectedProductId = null;
          this.productImages = [];
          if (this.productForm.materials.length === 0) {
            this.resetProductForm();
          }
          return;
        }

        if (this.filteredProducts.length > 0) {
          this.openProduct(this.filteredProducts[0].id);
        } else if (this.products.length === 0) {
          this.startCreateProduct();
        } else {
          this.selectedProduct = null;
          this.selectedProductId = null;
          this.productImages = [];
        }
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = this.extractErrorMessage(
          error,
          'Impossibile caricare il back-office shop.',
        );
      },
    });
  }

  openProduct(productId: string): void {
    this.productMode = 'edit';
    this.selectedProductId = productId;
    this.detailLoading = true;
    this.errorMessage = null;

    this.adminShopService.getProduct(productId).subscribe({
      next: (product) => {
        this.selectedProduct = product;
        this.productImages = this.buildProductImages(product);
        this.loadProductIntoForm(product);
        this.resetImageUploadState(product);
        this.modelUploadFile = null;
        this.detailLoading = false;
      },
      error: (error) => {
        this.detailLoading = false;
        this.errorMessage = this.extractErrorMessage(
          error,
          'Impossibile caricare il dettaglio prodotto.',
        );
      },
    });
  }

  startCreateProduct(): void {
    this.productMode = 'create';
    this.selectedProduct = null;
    this.selectedProductId = null;
    this.productImages = [];
    this.modelUploadFile = null;
    this.activeContentLanguage = 'it';
    this.resetProductForm();
    this.resetImageUploadState(null);
  }

  saveProduct(): void {
    if (this.savingProduct) {
      return;
    }

    this.syncDescriptionFromEditor(this.descriptionEditorElement, true);

    const validationError = this.validateProductForm();
    if (validationError) {
      this.errorMessage = validationError;
      this.successMessage = null;
      return;
    }

    const payload = this.buildProductPayload();
    this.savingProduct = true;
    this.errorMessage = null;
    this.successMessage = null;

    const request =
      this.productMode === 'create' || !this.selectedProductId
        ? this.adminShopService.createProduct(payload)
        : this.adminShopService.updateProduct(this.selectedProductId, payload);

    request.subscribe({
      next: (product) => {
        this.savingProduct = false;
        this.productMode = 'edit';
        this.selectedProductId = product.id;
        this.successMessage =
          this.selectedProduct != null
            ? 'Prodotto aggiornato.'
            : 'Prodotto creato.';
        this.loadWorkspace(product.id);
      },
      error: (error) => {
        this.savingProduct = false;
        this.errorMessage = this.extractErrorMessage(
          error,
          'Salvataggio prodotto non riuscito.',
        );
      },
    });
  }

  deleteSelectedProduct(): void {
    if (!this.selectedProductId || this.deletingProduct) {
      return;
    }

    if (
      !window.confirm(
        "Eliminare questo prodotto? L'azione non puo essere annullata.",
      )
    ) {
      return;
    }

    this.deletingProduct = true;
    this.errorMessage = null;
    this.successMessage = null;

    this.adminShopService.deleteProduct(this.selectedProductId).subscribe({
      next: () => {
        this.deletingProduct = false;
        this.successMessage = 'Prodotto eliminato.';
        this.startCreateProduct();
        this.loadWorkspace();
      },
      error: (error) => {
        this.deletingProduct = false;
        this.errorMessage = this.extractErrorMessage(
          error,
          'Eliminazione prodotto non riuscita.',
        );
      },
    });
  }

  onProductSearchChange(value: string): void {
    this.productSearchTerm = value;
    this.applyProductFilters();
  }

  onCategoryFilterChange(value: string): void {
    this.categoryFilter = value || 'ALL';
    this.applyProductFilters();
  }

  onProductStatusFilterChange(value: string): void {
    this.productStatusFilter = (value || 'ALL') as ProductStatusFilter;
    this.applyProductFilters();
  }

  startPanelResize(event: PointerEvent): void {
    if (window.innerWidth <= 1060) {
      return;
    }
    event.preventDefault();
    this.isResizingPanels = true;
    document.body.style.cursor = 'col-resize';
    this.updateListPanelWidthFromPointer(event.clientX);
  }

  isSelectedProduct(productId: string): boolean {
    return this.selectedProductId === productId;
  }

  visibleProductCountForCategory(categoryId: string): number {
    return this.products.filter((product) => product.categoryId === categoryId)
      .length;
  }

  categoryOptionLabel(category: AdminShopCategory): string {
    return `${'  '.repeat(Math.max(0, category.depth || 0))}${category.name}`;
  }

  toggleCategoryManager(): void {
    this.showCategoryManager = !this.showCategoryManager;
    if (this.showCategoryManager && !this.categoryForm.id) {
      this.resetCategoryForm();
    }
  }

  editCategory(categoryId: string): void {
    this.showCategoryManager = true;
    this.errorMessage = null;
    this.adminShopService.getCategory(categoryId).subscribe({
      next: (category) => {
        this.loadCategoryIntoForm(category);
      },
      error: (error) => {
        this.errorMessage = this.extractErrorMessage(
          error,
          'Impossibile caricare la categoria.',
        );
      },
    });
  }

  prepareCreateCategory(): void {
    this.resetCategoryForm();
  }

  saveCategory(): void {
    if (this.savingCategory) {
      return;
    }

    const validationError = this.validateCategoryForm();
    if (validationError) {
      this.errorMessage = validationError;
      this.successMessage = null;
      return;
    }

    const payload = this.buildCategoryPayload();
    this.savingCategory = true;
    this.errorMessage = null;
    this.successMessage = null;

    const request = this.categoryForm.id
      ? this.adminShopService.updateCategory(this.categoryForm.id, payload)
      : this.adminShopService.createCategory(payload);

    request.subscribe({
      next: (category) => {
        this.savingCategory = false;
        this.successMessage = this.categoryForm.id
          ? 'Categoria aggiornata.'
          : 'Categoria creata.';
        this.loadCategoryIntoForm(category);
        this.loadWorkspace(this.selectedProductId ?? undefined);
      },
      error: (error) => {
        this.savingCategory = false;
        this.errorMessage = this.extractErrorMessage(
          error,
          'Salvataggio categoria non riuscito.',
        );
      },
    });
  }

  deleteCategory(): void {
    if (!this.categoryForm.id || this.deletingCategory) {
      return;
    }

    if (
      !window.confirm(
        'Eliminare questa categoria? Fallira se contiene sottocategorie o prodotti.',
      )
    ) {
      return;
    }

    this.deletingCategory = true;
    this.errorMessage = null;
    this.successMessage = null;

    this.adminShopService.deleteCategory(this.categoryForm.id).subscribe({
      next: () => {
        this.deletingCategory = false;
        this.successMessage = 'Categoria eliminata.';
        this.resetCategoryForm();
        this.loadWorkspace(this.selectedProductId ?? undefined);
      },
      error: (error) => {
        this.deletingCategory = false;
        this.errorMessage = this.extractErrorMessage(
          error,
          'Eliminazione categoria non riuscita.',
        );
      },
    });
  }

  slugifyProductFromCurrentLanguage(): void {
    const source =
      this.productForm.names[this.activeContentLanguage] ||
      this.productForm.names['it'];
    this.productForm.slug = this.slugify(source);
  }

  slugifyCategoryFromName(): void {
    this.categoryForm.slug = this.slugify(this.categoryForm.name);
  }

  setActiveContentLanguage(language: ShopLanguage): void {
    this.syncDescriptionFromEditor(this.descriptionEditorElement, true);
    this.activeContentLanguage = language;
    this.renderActiveDescriptionInEditor();
  }

  isContentLanguageComplete(language: ShopLanguage): boolean {
    return !!this.productForm.names[language].trim();
  }

  isContentLanguageStarted(language: ShopLanguage): boolean {
    return (
      !!this.productForm.names[language].trim() ||
      !!this.productForm.excerpts[language].trim() ||
      this.hasMeaningfulRichText(this.productForm.descriptions[language])
    );
  }

  isContentLanguageIncomplete(language: ShopLanguage): boolean {
    return (
      this.isContentLanguageStarted(language) &&
      !this.isContentLanguageComplete(language)
    );
  }

  isSeoLanguageComplete(language: ShopLanguage): boolean {
    return (
      !!this.productForm.seoTitles[language].trim() &&
      !!this.productForm.seoDescriptions[language].trim()
    );
  }

  isSeoLanguageStarted(language: ShopLanguage): boolean {
    return (
      !!this.productForm.seoTitles[language].trim() ||
      !!this.productForm.seoDescriptions[language].trim()
    );
  }

  isSeoLanguageIncomplete(language: ShopLanguage): boolean {
    return (
      this.isSeoLanguageStarted(language) &&
      !this.isSeoLanguageComplete(language)
    );
  }

  preventRichTextToolbarMouseDown(event: MouseEvent): void {
    event.preventDefault();
  }

  onDescriptionEditorInput(event: Event): void {
    const editor = event.target as HTMLDivElement | null;
    this.syncDescriptionFromEditor(editor, false);
  }

  onDescriptionEditorBlur(event: Event): void {
    const editor = event.target as HTMLDivElement | null;
    this.syncDescriptionFromEditor(editor, true);
  }

  formatDescription(command: 'bold' | 'italic' | 'underline'): void {
    this.applyDescriptionExecCommand(command);
  }

  formatDescriptionList(type: 'unordered' | 'ordered'): void {
    this.applyDescriptionExecCommand(
      type === 'unordered' ? 'insertUnorderedList' : 'insertOrderedList',
    );
  }

  clearDescriptionFormatting(): void {
    this.applyDescriptionExecCommand('removeFormat');
  }

  addMaterial(): void {
    const nextMaterialCode = this.nextAvailableMaterialCode();
    if (!nextMaterialCode) {
      return;
    }
    const sortOrder = (this.productForm.materials.at(-1)?.sortOrder ?? -1) + 1;
    const firstMaterial = this.productForm.materials.length === 0;
    this.productForm.materials = [
      ...this.productForm.materials,
      this.createEmptyMaterialForm(sortOrder, firstMaterial, nextMaterialCode),
    ];
  }

  removeMaterial(index: number): void {
    if (this.productForm.materials.length <= 1) {
      return;
    }

    const nextMaterials = this.productForm.materials.filter(
      (_, currentIndex) => currentIndex !== index,
    );
    if (!nextMaterials.some((material) => material.isDefault)) {
      nextMaterials[0].isDefault = true;
    }
    this.productForm.materials = nextMaterials;
  }

  setDefaultMaterial(index: number): void {
    this.productForm.materials = this.productForm.materials.map(
      (material, currentIndex) => ({
        ...material,
        isDefault: currentIndex === index,
      }),
    );
  }

  availableMaterialChoices(currentMaterialCode: string): string[] {
    const normalizedCurrentMaterialCode = currentMaterialCode
      .trim()
      .toUpperCase();
    const selectedCodes = new Set(
      this.productForm.materials
        .map((material) => material.materialCode.trim().toUpperCase())
        .filter(Boolean),
    );

    const availableCodes = this.stockMaterialCodes().filter(
      (materialCode) =>
        materialCode === normalizedCurrentMaterialCode ||
        !selectedCodes.has(materialCode),
    );

    if (
      normalizedCurrentMaterialCode &&
      !availableCodes.includes(normalizedCurrentMaterialCode)
    ) {
      return [normalizedCurrentMaterialCode, ...availableCodes];
    }

    return availableCodes;
  }

  materialColorCount(materialCode: string): number {
    return this.stockVariantsForMaterial(materialCode).length;
  }

  materialColorPreview(materialCode: string): string[] {
    return this.stockVariantsForMaterial(materialCode)
      .map((variant) => variant.colorName.trim())
      .filter(Boolean)
      .slice(0, 6);
  }

  onModelFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0] ?? null;
    if (!file) {
      this.modelUploadFile = null;
      return;
    }

    const extension = this.resolveFileExtension(file.name);
    if (!['stl', '3mf'].includes(extension)) {
      this.modelUploadFile = null;
      this.errorMessage = 'Sono ammessi solo file STL o 3MF.';
      return;
    }
    if (file.size > MAX_MODEL_FILE_SIZE_BYTES) {
      this.modelUploadFile = null;
      this.errorMessage = `Il modello supera il limite di ${this.maxModelFileSizeMb} MB.`;
      return;
    }

    this.modelUploadFile = file;
  }

  uploadModel(): void {
    if (
      !this.selectedProductId ||
      !this.modelUploadFile ||
      this.uploadingModel ||
      this.productMode !== 'edit'
    ) {
      return;
    }

    this.uploadingModel = true;
    this.errorMessage = null;
    this.successMessage = null;

    this.adminShopService
      .uploadProductModel(this.selectedProductId, this.modelUploadFile)
      .subscribe({
        next: (product) => {
          this.uploadingModel = false;
          this.modelUploadFile = null;
          this.successMessage = 'Modello 3D aggiornato.';
          this.updateSelectedProduct(product);
          this.loadWorkspace(product.id);
        },
        error: (error) => {
          this.uploadingModel = false;
          this.errorMessage = this.extractErrorMessage(
            error,
            'Upload modello 3D non riuscito.',
          );
        },
      });
  }

  deleteModel(): void {
    if (
      !this.selectedProductId ||
      this.deletingModel ||
      !this.selectedProduct?.model3d
    ) {
      return;
    }

    if (
      !window.confirm('Rimuovere il modello 3D associato a questo prodotto?')
    ) {
      return;
    }

    this.deletingModel = true;
    this.errorMessage = null;
    this.successMessage = null;

    this.adminShopService.deleteProductModel(this.selectedProductId).subscribe({
      next: () => {
        this.deletingModel = false;
        this.modelUploadFile = null;
        this.successMessage = 'Modello 3D rimosso.';
        this.loadWorkspace(this.selectedProductId ?? undefined);
      },
      error: (error) => {
        this.deletingModel = false;
        this.errorMessage = this.extractErrorMessage(
          error,
          'Rimozione modello 3D non riuscita.',
        );
      },
    });
  }

  getProductModelUrl(model: AdminShopProductModel | null): string | null {
    if (!model?.url) {
      return null;
    }
    return `${environment.apiUrl}${model.url}`;
  }

  onImageFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0] ?? null;
    const previousPreviewUrl = this.imageUploadState.previewUrl;
    this.revokeImagePreviewUrl(previousPreviewUrl);

    if (!file) {
      this.imageUploadState = {
        ...this.imageUploadState,
        file: null,
        previewUrl: null,
      };
      return;
    }

    if (!this.isAllowedImageType(file.type, file.name)) {
      this.imageUploadState = {
        ...this.imageUploadState,
        file: null,
        previewUrl: null,
      };
      this.errorMessage =
        'Sono ammesse immagini JPG, PNG o WEBP per il catalogo.';
      return;
    }

    const nextTranslations = this.cloneTranslations(
      this.imageUploadState.translations,
    );
    if (this.areAllTitlesBlank(nextTranslations)) {
      const defaultTitle = this.deriveDefaultTitle(file.name);
      for (const language of this.mediaLanguages) {
        nextTranslations[language].title = defaultTitle;
      }
    }

    this.imageUploadState = {
      ...this.imageUploadState,
      file,
      previewUrl: URL.createObjectURL(file),
      translations: nextTranslations,
    };
  }

  setActiveImageLanguage(language: AdminMediaLanguage): void {
    this.imageUploadState = {
      ...this.imageUploadState,
      activeLanguage: language,
    };
  }

  getActiveImageTranslation(): AdminMediaTranslation {
    return this.imageUploadState.translations[
      this.imageUploadState.activeLanguage
    ];
  }

  isImageLanguageComplete(language: AdminMediaLanguage): boolean {
    return this.isTranslationComplete(
      this.imageUploadState.translations[language],
    );
  }

  isImageLanguageStarted(language: AdminMediaLanguage): boolean {
    const translation = this.imageUploadState.translations[language];
    return !!translation.title.trim() || !!translation.altText.trim();
  }

  isImageLanguageIncomplete(language: AdminMediaLanguage): boolean {
    return (
      this.isImageLanguageStarted(language) &&
      !this.isImageLanguageComplete(language)
    );
  }

  uploadProductImage(): void {
    if (
      !this.selectedProduct ||
      !this.selectedProductId ||
      !this.imageUploadState.file ||
      this.imageUploadState.saving
    ) {
      return;
    }

    const validationError = this.validateImageTranslations(
      this.imageUploadState.translations,
    );
    if (validationError) {
      this.errorMessage = validationError;
      this.successMessage = null;
      return;
    }

    const normalizedTranslations = this.normalizeTranslations(
      this.imageUploadState.translations,
    );
    const currentProductId = this.selectedProductId;
    const uploadFile = this.imageUploadState.file;
    const selectedProduct = this.selectedProduct;

    if (!uploadFile || !selectedProduct) {
      return;
    }

    this.imageUploadState = {
      ...this.imageUploadState,
      saving: true,
    };
    this.errorMessage = null;
    this.successMessage = null;

    this.adminShopService
      .uploadMediaAsset(uploadFile, {
        title: normalizedTranslations['it'].title,
        altText: normalizedTranslations['it'].altText,
        visibility: 'PUBLIC',
      })
      .pipe(
        switchMap((asset) =>
          this.adminShopService.createMediaUsage({
            usageType: selectedProduct.mediaUsageType,
            usageKey: selectedProduct.mediaUsageKey,
            mediaAssetId: asset.id,
            sortOrder: this.imageUploadState.sortOrder,
            isPrimary: this.imageUploadState.isPrimary,
            isActive: true,
            translations: normalizedTranslations,
          }),
        ),
      )
      .subscribe({
        next: () => {
          this.imageUploadState = {
            ...this.imageUploadState,
            saving: false,
          };
          this.successMessage = 'Immagine prodotto caricata.';
          this.loadWorkspace(currentProductId);
        },
        error: (error) => {
          this.imageUploadState = {
            ...this.imageUploadState,
            saving: false,
          };
          this.errorMessage = this.extractErrorMessage(
            error,
            'Upload immagine prodotto non riuscito.',
          );
        },
      });
  }

  saveImageSortOrder(item: ProductImageItem): void {
    if (
      this.imageActionIds.has(item.usageId) ||
      item.draftSortOrder === item.sortOrder
    ) {
      return;
    }

    this.imageActionIds.add(item.usageId);
    this.errorMessage = null;
    this.successMessage = null;

    this.adminShopService
      .updateMediaUsage(item.usageId, { sortOrder: item.draftSortOrder })
      .subscribe({
        next: () => {
          this.imageActionIds.delete(item.usageId);
          this.successMessage = 'Ordine immagini aggiornato.';
          this.loadWorkspace(this.selectedProductId ?? undefined);
        },
        error: (error) => {
          this.imageActionIds.delete(item.usageId);
          this.errorMessage = this.extractErrorMessage(
            error,
            'Aggiornamento ordine immagini non riuscito.',
          );
        },
      });
  }

  setPrimaryImage(item: ProductImageItem): void {
    if (item.isPrimary || this.imageActionIds.has(item.usageId)) {
      return;
    }

    this.imageActionIds.add(item.usageId);
    this.errorMessage = null;
    this.successMessage = null;

    this.adminShopService
      .updateMediaUsage(item.usageId, { isPrimary: true, isActive: true })
      .subscribe({
        next: () => {
          this.imageActionIds.delete(item.usageId);
          this.successMessage = 'Immagine principale aggiornata.';
          this.loadWorkspace(this.selectedProductId ?? undefined);
        },
        error: (error) => {
          this.imageActionIds.delete(item.usageId);
          this.errorMessage = this.extractErrorMessage(
            error,
            'Aggiornamento immagine principale non riuscito.',
          );
        },
      });
  }

  removeImage(item: ProductImageItem): void {
    if (this.imageActionIds.has(item.usageId)) {
      return;
    }

    if (!window.confirm('Rimuovere questa immagine dal prodotto?')) {
      return;
    }

    this.imageActionIds.add(item.usageId);
    this.errorMessage = null;
    this.successMessage = null;

    this.adminShopService
      .updateMediaUsage(item.usageId, { isActive: false, isPrimary: false })
      .subscribe({
        next: () => {
          this.imageActionIds.delete(item.usageId);
          this.successMessage = 'Immagine rimossa dal prodotto.';
          this.loadWorkspace(this.selectedProductId ?? undefined);
        },
        error: (error) => {
          this.imageActionIds.delete(item.usageId);
          this.errorMessage = this.extractErrorMessage(
            error,
            'Rimozione immagine non riuscita.',
          );
        },
      });
  }

  isImageBusy(usageId: string): boolean {
    return this.imageActionIds.has(usageId);
  }

  trackCategory(_: number, category: AdminShopCategory): string {
    return category.id;
  }

  trackProduct(_: number, product: AdminShopProduct): string {
    return product.id;
  }

  trackMaterial(_: number, material: ProductMaterialFormState): string {
    return `${material.materialCode || 'material'}-${material.sortOrder}`;
  }

  trackImage(_: number, image: ProductImageItem): string {
    return image.usageId;
  }

  formatFileSize(bytes: number | null | undefined): string {
    if (!bytes || bytes <= 0) {
      return '-';
    }
    const units = ['B', 'KB', 'MB', 'GB'];
    let value = bytes;
    let unitIndex = 0;
    while (value >= 1024 && unitIndex < units.length - 1) {
      value /= 1024;
      unitIndex += 1;
    }
    return `${value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
  }

  productStatusChipClass(product: AdminShopProduct): string {
    if (!product.isActive) {
      return 'ui-status-chip--danger';
    }
    if (product.isFeatured) {
      return 'ui-status-chip--success';
    }
    return 'ui-status-chip--neutral';
  }

  private applyProductFilters(): void {
    const searchNeedle = this.productSearchTerm.trim().toLowerCase();
    this.filteredProducts = this.products.filter((product) => {
      const matchesCategory =
        this.categoryFilter === 'ALL' ||
        product.categoryId === this.categoryFilter;
      const matchesStatus =
        this.productStatusFilter === 'ALL' ||
        (this.productStatusFilter === 'ACTIVE' && product.isActive) ||
        (this.productStatusFilter === 'INACTIVE' && !product.isActive) ||
        (this.productStatusFilter === 'FEATURED' && product.isFeatured);
      const matchesSearch =
        searchNeedle.length === 0 ||
        [
          product.name,
          product.slug,
          product.categoryName,
          ...product.variants.map((variant) => variant.colorName),
          ...product.variants.map((variant) => variant.internalMaterialCode),
        ]
          .filter(Boolean)
          .some((value) => value.toLowerCase().includes(searchNeedle));
      return matchesCategory && matchesStatus && matchesSearch;
    });
  }

  private updateListPanelWidthFromPointer(clientX: number): void {
    const workspace = this.workspaceRef?.nativeElement;
    if (!workspace) {
      return;
    }
    const bounds = workspace.getBoundingClientRect();
    if (bounds.width <= 0) {
      return;
    }

    const relativeX = clientX - bounds.left;
    const nextPercent = (relativeX / bounds.width) * 100;
    this.listPanelWidthPercent = this.clampListPanelWidth(nextPercent);
  }

  private restoreListPanelWidth(): void {
    const storedValue = window.localStorage.getItem(
      SHOP_LIST_PANEL_WIDTH_STORAGE_KEY,
    );
    if (!storedValue) {
      return;
    }
    const parsed = Number(storedValue);
    if (!Number.isFinite(parsed)) {
      return;
    }
    this.listPanelWidthPercent = this.clampListPanelWidth(parsed);
  }

  private persistListPanelWidth(): void {
    window.localStorage.setItem(
      SHOP_LIST_PANEL_WIDTH_STORAGE_KEY,
      String(this.listPanelWidthPercent),
    );
  }

  private clampListPanelWidth(value: number): number {
    return Math.min(
      MAX_LIST_PANEL_WIDTH_PERCENT,
      Math.max(MIN_LIST_PANEL_WIDTH_PERCENT, value),
    );
  }

  private ensureCategoryFilterStillValid(): void {
    if (
      this.categoryFilter !== 'ALL' &&
      !this.categories.some((category) => category.id === this.categoryFilter)
    ) {
      this.categoryFilter = 'ALL';
      this.applyProductFilters();
    }
  }

  private createEmptyCategoryForm(): CategoryFormState {
    return {
      id: null,
      parentCategoryId: null,
      slug: '',
      name: '',
      description: '',
      seoTitle: '',
      seoDescription: '',
      ogTitle: '',
      ogDescription: '',
      indexable: true,
      isActive: true,
      sortOrder: 0,
    };
  }

  private resetCategoryForm(): void {
    Object.assign(this.categoryForm, this.createEmptyCategoryForm());
  }

  private loadCategoryIntoForm(category: AdminShopCategory): void {
    Object.assign(this.categoryForm, {
      id: category.id,
      parentCategoryId: category.parentCategoryId,
      slug: category.slug ?? '',
      name: category.name ?? '',
      description: category.description ?? '',
      seoTitle: category.seoTitle ?? '',
      seoDescription: category.seoDescription ?? '',
      ogTitle: category.ogTitle ?? '',
      ogDescription: category.ogDescription ?? '',
      indexable: category.indexable,
      isActive: category.isActive,
      sortOrder: category.sortOrder ?? 0,
    });
  }

  private buildCategoryPayload(): AdminUpsertShopCategoryPayload {
    return {
      parentCategoryId: this.categoryForm.parentCategoryId || null,
      slug: this.categoryForm.slug.trim(),
      name: this.categoryForm.name.trim(),
      description: this.categoryForm.description.trim(),
      seoTitle: this.categoryForm.seoTitle.trim(),
      seoDescription: this.categoryForm.seoDescription.trim(),
      ogTitle: this.categoryForm.ogTitle.trim(),
      ogDescription: this.categoryForm.ogDescription.trim(),
      indexable: this.categoryForm.indexable,
      isActive: this.categoryForm.isActive,
      sortOrder: Number(this.categoryForm.sortOrder) || 0,
    };
  }

  private validateCategoryForm(): string | null {
    if (!this.categoryForm.name.trim()) {
      return 'Il nome categoria è obbligatorio.';
    }
    if (!this.categoryForm.slug.trim()) {
      return 'Lo slug categoria è obbligatorio.';
    }
    return null;
  }

  private createEmptyProductForm(): ProductFormState {
    const defaultCategoryId =
      this.categoryFilter !== 'ALL'
        ? this.categoryFilter
        : (this.categories[0]?.id ?? '');
    const defaultMaterialCode = this.stockMaterialCodes()[0] ?? '';
    return {
      categoryId: defaultCategoryId,
      slug: '',
      names: {
        it: '',
        en: '',
        de: '',
        fr: '',
      },
      excerpts: {
        it: '',
        en: '',
        de: '',
        fr: '',
      },
      descriptions: {
        it: '',
        en: '',
        de: '',
        fr: '',
      },
      seoTitles: {
        it: '',
        en: '',
        de: '',
        fr: '',
      },
      seoDescriptions: {
        it: '',
        en: '',
        de: '',
        fr: '',
      },
      indexable: true,
      isFeatured: false,
      isActive: true,
      sortOrder: 0,
      materials: defaultMaterialCode
        ? [this.createEmptyMaterialForm(0, true, defaultMaterialCode)]
        : [],
    };
  }

  private resetProductForm(): void {
    Object.assign(this.productForm, this.createEmptyProductForm());
    this.renderActiveDescriptionInEditor();
  }

  private createEmptyMaterialForm(
    sortOrder: number,
    isDefault: boolean,
    materialCode = '',
  ): ProductMaterialFormState {
    return {
      materialCode,
      priceChf: '0.00',
      isDefault,
      isActive: true,
      sortOrder,
    };
  }

  private loadProductIntoForm(product: AdminShopProduct): void {
    Object.assign(this.productForm, {
      categoryId: product.categoryId ?? '',
      slug: product.slug ?? '',
      names: {
        it: product.nameIt ?? '',
        en: product.nameEn ?? '',
        de: product.nameDe ?? '',
        fr: product.nameFr ?? '',
      },
      excerpts: {
        it: product.excerptIt ?? '',
        en: product.excerptEn ?? '',
        de: product.excerptDe ?? '',
        fr: product.excerptFr ?? '',
      },
      descriptions: {
        it: this.normalizeDescriptionForEditor(product.descriptionIt),
        en: this.normalizeDescriptionForEditor(product.descriptionEn),
        de: this.normalizeDescriptionForEditor(product.descriptionDe),
        fr: this.normalizeDescriptionForEditor(product.descriptionFr),
      },
      seoTitles: {
        it: product.seoTitleIt ?? '',
        en: product.seoTitleEn ?? '',
        de: product.seoTitleDe ?? '',
        fr: product.seoTitleFr ?? '',
      },
      seoDescriptions: {
        it: product.seoDescriptionIt ?? '',
        en: product.seoDescriptionEn ?? '',
        de: product.seoDescriptionDe ?? '',
        fr: product.seoDescriptionFr ?? '',
      },
      indexable: product.indexable,
      isFeatured: product.isFeatured,
      isActive: product.isActive,
      sortOrder: product.sortOrder ?? 0,
      materials: this.toMaterialForms(product.variants),
    });
    this.renderActiveDescriptionInEditor();
  }

  private toMaterialForms(
    variants: AdminShopProductVariant[],
  ): ProductMaterialFormState[] {
    if (!variants.length) {
      const defaultMaterialCode = this.stockMaterialCodes()[0] ?? '';
      return defaultMaterialCode
        ? [this.createEmptyMaterialForm(0, true, defaultMaterialCode)]
        : [];
    }

    const groups = new Map<string, AdminShopProductVariant[]>();
    for (const variant of variants) {
      const materialCode = (variant.internalMaterialCode ?? '')
        .trim()
        .toUpperCase();
      if (!materialCode) {
        continue;
      }
      const group = groups.get(materialCode) ?? [];
      group.push(variant);
      groups.set(materialCode, group);
    }

    const materials = Array.from(groups.entries())
      .map(([materialCode, materialVariants]) => {
        const sortedVariants = [...materialVariants].sort(
          (left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0),
        );
        const firstVariant = sortedVariants[0];
        return {
          materialCode,
          priceChf: Number(firstVariant?.priceChf ?? 0).toFixed(2),
          isDefault: materialVariants.some((variant) => variant.isDefault),
          isActive: materialVariants.some((variant) => variant.isActive),
          sortOrder: Math.min(
            ...materialVariants.map((variant) => variant.sortOrder ?? 0),
          ),
        };
      })
      .sort((left, right) => left.sortOrder - right.sortOrder);

    if (!materials.some((material) => material.isDefault) && materials[0]) {
      materials[0].isDefault = true;
    }

    return materials;
  }

  private validateProductForm(): string | null {
    if (!this.productForm.categoryId) {
      return 'Seleziona una categoria per il prodotto.';
    }
    if (!this.productForm.slug.trim()) {
      return 'Lo slug prodotto è obbligatorio.';
    }
    for (const language of this.shopLanguages) {
      if (!this.productForm.names[language].trim()) {
        return `Il nome prodotto ${this.languageLabels[language]} è obbligatorio.`;
      }
      if (this.productForm.seoDescriptions[language].trim().length > 160) {
        return `La SEO description ${this.languageLabels[language]} deve stare sotto i 160 caratteri.`;
      }
    }
    if (this.productForm.materials.length === 0) {
      return 'Seleziona almeno un materiale disponibile a stock.';
    }

    let defaultCount = 0;
    const materialCodes = new Set<string>();
    for (const material of this.productForm.materials) {
      const materialCode = material.materialCode.trim().toUpperCase();
      if (!materialCode) {
        return 'Ogni riga materiale richiede un materiale selezionato.';
      }
      if (materialCodes.has(materialCode)) {
        return `Il materiale "${materialCode}" è duplicato.`;
      }
      materialCodes.add(materialCode);
      if (!this.stockMaterialCodes().includes(materialCode)) {
        return `Il materiale "${materialCode}" non è disponibile nello stock attivo.`;
      }
      if (this.stockVariantsForMaterial(materialCode).length === 0) {
        return `Il materiale "${materialCode}" non ha colori disponibili a stock.`;
      }

      const price = Number(material.priceChf);
      if (!Number.isFinite(price) || price < 0) {
        return `Il materiale "${materialCode}" ha un prezzo non valido.`;
      }
      if (material.isDefault) {
        defaultCount += 1;
      }
    }
    if (defaultCount !== 1) {
      return 'Devi impostare un solo materiale predefinito.';
    }

    return null;
  }

  private buildProductPayload(): AdminUpsertShopProductPayload {
    const variants = this.buildVariantsFromMaterials();

    return {
      categoryId: this.productForm.categoryId,
      slug: this.productForm.slug.trim(),
      name: this.productForm.names['it'].trim(),
      nameIt: this.productForm.names['it'].trim(),
      nameEn: this.productForm.names['en'].trim(),
      nameDe: this.productForm.names['de'].trim(),
      nameFr: this.productForm.names['fr'].trim(),
      excerpt: this.optionalValue(this.productForm.excerpts['it']),
      excerptIt: this.optionalValue(this.productForm.excerpts['it']),
      excerptEn: this.optionalValue(this.productForm.excerpts['en']),
      excerptDe: this.optionalValue(this.productForm.excerpts['de']),
      excerptFr: this.optionalValue(this.productForm.excerpts['fr']),
      description: this.optionalRichTextValue(
        this.productForm.descriptions['it'],
      ),
      descriptionIt: this.optionalRichTextValue(
        this.productForm.descriptions['it'],
      ),
      descriptionEn: this.optionalRichTextValue(
        this.productForm.descriptions['en'],
      ),
      descriptionDe: this.optionalRichTextValue(
        this.productForm.descriptions['de'],
      ),
      descriptionFr: this.optionalRichTextValue(
        this.productForm.descriptions['fr'],
      ),
      seoTitle: this.optionalValue(this.productForm.seoTitles['it']),
      seoTitleIt: this.optionalValue(this.productForm.seoTitles['it']),
      seoTitleEn: this.optionalValue(this.productForm.seoTitles['en']),
      seoTitleDe: this.optionalValue(this.productForm.seoTitles['de']),
      seoTitleFr: this.optionalValue(this.productForm.seoTitles['fr']),
      seoDescription: this.optionalValue(
        this.productForm.seoDescriptions['it'],
      ),
      seoDescriptionIt: this.optionalValue(
        this.productForm.seoDescriptions['it'],
      ),
      seoDescriptionEn: this.optionalValue(
        this.productForm.seoDescriptions['en'],
      ),
      seoDescriptionDe: this.optionalValue(
        this.productForm.seoDescriptions['de'],
      ),
      seoDescriptionFr: this.optionalValue(
        this.productForm.seoDescriptions['fr'],
      ),
      ogTitle: this.optionalValue(this.productForm.seoTitles['it']),
      ogDescription: this.optionalValue(this.productForm.seoDescriptions['it']),
      indexable: this.productForm.indexable,
      isFeatured: this.productForm.isFeatured,
      isActive: this.productForm.isActive,
      sortOrder: Number(this.productForm.sortOrder) || 0,
      variants,
    };
  }

  private buildVariantsFromMaterials(): AdminUpsertShopProductVariantPayload[] {
    const persistedDefaultVariant = this.selectedProduct?.variants.find(
      (variant) => variant.isDefault,
    );
    const existingVariantsByKey = new Map(
      (this.selectedProduct?.variants ?? []).map((variant) => [
        this.variantKey(
          variant.internalMaterialCode,
          variant.colorName,
          variant.colorHex,
        ),
        variant,
      ]),
    );
    const persistedDefaultKey = persistedDefaultVariant
      ? this.variantKey(
          persistedDefaultVariant.internalMaterialCode,
          persistedDefaultVariant.colorName,
          persistedDefaultVariant.colorHex,
        )
      : null;

    const variants: AdminUpsertShopProductVariantPayload[] = [];
    let defaultAssigned = false;

    const sortedMaterials = [...this.productForm.materials].sort(
      (left, right) => left.sortOrder - right.sortOrder,
    );

    for (const material of sortedMaterials) {
      const materialCode = material.materialCode.trim().toUpperCase();
      const stockVariants = this.stockVariantsForMaterial(materialCode);
      let defaultVariantKeyForMaterial: string | null = null;

      if (material.isDefault && persistedDefaultKey) {
        defaultVariantKeyForMaterial =
          stockVariants
            .map((variant) =>
              this.variantKey(
                materialCode,
                variant.colorName,
                variant.colorHex,
              ),
            )
            .find((variantKey) => variantKey === persistedDefaultKey) ?? null;
      }

      stockVariants.forEach((stockVariant, colorIndex) => {
        const variantKey = this.variantKey(
          materialCode,
          stockVariant.colorName,
          stockVariant.colorHex,
        );
        const existingVariant = existingVariantsByKey.get(variantKey);
        const isDefault =
          material.isDefault &&
          !defaultAssigned &&
          (defaultVariantKeyForMaterial
            ? variantKey === defaultVariantKeyForMaterial
            : colorIndex === 0);

        variants.push({
          id: existingVariant?.id,
          sku: this.optionalValue(existingVariant?.sku ?? ''),
          variantLabel: materialCode,
          colorName: stockVariant.colorName.trim(),
          colorHex: this.optionalValue(
            stockVariant.colorHex ?? '',
          )?.toUpperCase(),
          internalMaterialCode: materialCode,
          priceChf: Number(material.priceChf),
          isDefault,
          isActive: material.isActive,
          sortOrder: material.sortOrder * 100 + colorIndex,
        });

        if (isDefault) {
          defaultAssigned = true;
        }
      });
    }

    if (!defaultAssigned && variants[0]) {
      variants[0].isDefault = true;
    }

    return variants;
  }

  stockMaterialCodes(): string[] {
    return Array.from(
      new Set(
        this.stockFilamentVariants.map((variant) =>
          variant.materialCode.trim().toUpperCase(),
        ),
      ),
    ).sort((left, right) => left.localeCompare(right));
  }

  private stockVariantsForMaterial(
    materialCode: string,
  ): AdminFilamentVariant[] {
    const targetMaterialCode = materialCode.trim().toUpperCase();
    const seenKeys = new Set<string>();

    return this.stockFilamentVariants
      .filter(
        (variant) =>
          variant.materialCode.trim().toUpperCase() === targetMaterialCode,
      )
      .sort((left, right) => {
        const leftName = `${left.colorName} ${left.variantDisplayName}`.trim();
        const rightName =
          `${right.colorName} ${right.variantDisplayName}`.trim();
        return leftName.localeCompare(rightName);
      })
      .filter((variant) => {
        const key = this.variantKey(
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

  private nextAvailableMaterialCode(): string | null {
    const selectedCodes = new Set(
      this.productForm.materials
        .map((material) => material.materialCode.trim().toUpperCase())
        .filter(Boolean),
    );

    return (
      this.stockMaterialCodes().find(
        (materialCode) => !selectedCodes.has(materialCode),
      ) ?? null
    );
  }

  private filterStockedFilamentVariants(
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

  private variantKey(
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

  private updateSelectedProduct(product: AdminShopProduct): void {
    this.selectedProduct = product;
    this.selectedProductId = product.id;
    this.productImages = this.buildProductImages(product);
    this.loadProductIntoForm(product);
    this.resetImageUploadState(product);
  }

  private buildProductImages(product: AdminShopProduct): ProductImageItem[] {
    const publicByAssetId = new Map<string, AdminPublicMediaUsage>();
    for (const image of product.images) {
      publicByAssetId.set(image.mediaAssetId, image);
    }

    return product.mediaUsages
      .filter((usage) => usage.isActive)
      .map((usage) => {
        const publicUsage = publicByAssetId.get(usage.mediaAssetId);
        const translations = this.normalizeTranslations(usage.translations);
        return {
          usageId: usage.id,
          mediaAssetId: usage.mediaAssetId,
          previewUrl: this.resolveProductImageUrl(publicUsage),
          sortOrder: usage.sortOrder ?? 0,
          draftSortOrder: usage.sortOrder ?? 0,
          isPrimary: usage.isPrimary,
          createdAt: usage.createdAt,
          translations,
          title:
            publicUsage?.title ??
            translations[this.imageUploadState.activeLanguage].title,
          altText:
            publicUsage?.altText ??
            translations[this.imageUploadState.activeLanguage].altText,
        };
      })
      .sort((left, right) => {
        if (left.sortOrder !== right.sortOrder) {
          return left.sortOrder - right.sortOrder;
        }
        return left.createdAt.localeCompare(right.createdAt);
      });
  }

  private resolveProductImageUrl(
    image: AdminPublicMediaUsage | undefined,
  ): string | null {
    if (!image) {
      return null;
    }
    return image.card?.url ?? image.hero?.url ?? image.thumb?.url ?? null;
  }

  private createEmptyImageUploadState(): ProductImageUploadState {
    return {
      file: null,
      previewUrl: null,
      activeLanguage: 'it',
      translations: this.createEmptyTranslations(),
      sortOrder: 0,
      isPrimary: false,
      saving: false,
    };
  }

  private resetImageUploadState(product: AdminShopProduct | null): void {
    this.revokeImagePreviewUrl(this.imageUploadState.previewUrl);
    const nextSortOrder = (this.productImages.at(-1)?.sortOrder ?? -1) + 1;
    this.imageUploadState = {
      file: null,
      previewUrl: null,
      activeLanguage: 'it',
      translations: this.createEmptyTranslations(),
      sortOrder: Math.max(0, nextSortOrder),
      isPrimary: (product?.images.length ?? 0) === 0,
      saving: false,
    };
  }

  private revokeImagePreviewUrl(previewUrl: string | null): void {
    if (previewUrl?.startsWith('blob:')) {
      URL.revokeObjectURL(previewUrl);
    }
  }

  private createEmptyTranslations(): Record<
    AdminMediaLanguage,
    AdminMediaTranslation
  > {
    return {
      it: { title: '', altText: '' },
      en: { title: '', altText: '' },
      de: { title: '', altText: '' },
      fr: { title: '', altText: '' },
    };
  }

  private cloneTranslations(
    translations: Record<AdminMediaLanguage, AdminMediaTranslation>,
  ): Record<AdminMediaLanguage, AdminMediaTranslation> {
    return this.normalizeTranslations(translations);
  }

  private normalizeTranslations(
    translations: Partial<
      Record<AdminMediaLanguage, Partial<AdminMediaTranslation>>
    >,
  ): Record<AdminMediaLanguage, AdminMediaTranslation> {
    return {
      it: {
        title: translations['it']?.title?.trim() ?? '',
        altText: translations['it']?.altText?.trim() ?? '',
      },
      en: {
        title: translations['en']?.title?.trim() ?? '',
        altText: translations['en']?.altText?.trim() ?? '',
      },
      de: {
        title: translations['de']?.title?.trim() ?? '',
        altText: translations['de']?.altText?.trim() ?? '',
      },
      fr: {
        title: translations['fr']?.title?.trim() ?? '',
        altText: translations['fr']?.altText?.trim() ?? '',
      },
    };
  }

  private isTranslationComplete(translation: AdminMediaTranslation): boolean {
    return !!translation.title.trim() && !!translation.altText.trim();
  }

  private validateImageTranslations(
    translations: Record<AdminMediaLanguage, AdminMediaTranslation>,
  ): string | null {
    for (const language of this.mediaLanguages) {
      if (!this.isTranslationComplete(translations[language])) {
        return `Titolo e alt text immagine ${this.languageLabels[language]} sono obbligatori.`;
      }
    }
    return null;
  }

  private areAllTitlesBlank(
    translations: Record<AdminMediaLanguage, AdminMediaTranslation>,
  ): boolean {
    return this.mediaLanguages.every(
      (language) => !translations[language].title.trim(),
    );
  }

  private deriveDefaultTitle(filename: string): string {
    return filename
      .replace(/\.[^.]+$/, '')
      .replace(/[-_]+/g, ' ')
      .trim();
  }

  private optionalValue(value: string): string | undefined {
    const normalized = value.trim();
    return normalized ? normalized : undefined;
  }

  private optionalRichTextValue(value: string): string | undefined {
    const normalized = this.normalizeRichTextStorageValue(value);
    return normalized ? normalized : undefined;
  }

  private syncDescriptionFromEditor(
    editor: HTMLDivElement | null,
    sanitize: boolean,
  ): void {
    if (!editor) {
      return;
    }
    const currentHtml = this.serializeNodeChildren(editor);
    const currentLanguage = this.activeContentLanguage;
    if (sanitize) {
      const normalized = this.normalizeRichTextStorageValue(currentHtml);
      const safeHtml = normalized ?? '';
      this.productForm.descriptions[currentLanguage] = safeHtml;
      if (currentHtml !== safeHtml) {
        this.replaceElementContentFromHtml(editor, safeHtml);
      }
      return;
    }
    this.productForm.descriptions[currentLanguage] = currentHtml;
  }

  private renderActiveDescriptionInEditor(): void {
    const editor = this.descriptionEditorElement;
    if (!editor) {
      return;
    }
    const html =
      this.productForm.descriptions[this.activeContentLanguage] ?? '';
    if (this.serializeNodeChildren(editor) !== html) {
      this.replaceElementContentFromHtml(editor, html);
    }
  }

  private applyDescriptionExecCommand(command: string): void {
    const editor = this.descriptionEditorElement;
    if (!editor) {
      return;
    }
    editor.focus();
    document.execCommand(command, false);
    this.syncDescriptionFromEditor(editor, false);
  }

  private normalizeDescriptionForEditor(
    value: string | null | undefined,
  ): string {
    return this.normalizeRichTextStorageValue(value ?? '') ?? '';
  }

  private normalizeRichTextStorageValue(value: string): string | null {
    const normalized = value.trim();
    if (!normalized) {
      return null;
    }
    const sanitized = this.containsHtmlMarkup(normalized)
      ? this.sanitizeRichTextHtml(normalized)
      : this.plainTextToRichTextHtml(normalized);
    const compact = sanitized.trim();
    if (!compact || !this.hasMeaningfulRichText(compact)) {
      return null;
    }
    return compact;
  }

  private containsHtmlMarkup(value: string): boolean {
    return /<\/?[a-z][\s\S]*>/i.test(value);
  }

  private plainTextToRichTextHtml(value: string): string {
    const normalized = value.replace(/\r\n?/g, '\n').trim();
    if (!normalized) {
      return '';
    }
    return normalized
      .split(/\n{2,}/)
      .map(
        (paragraph) =>
          `<p>${this.escapeHtml(paragraph).replace(/\n/g, '<br>')}</p>`,
      )
      .join('');
  }

  private sanitizeRichTextHtml(value: string): string {
    const parser = new DOMParser();
    const sourceDocument = parser.parseFromString(
      `<body>${value}</body>`,
      'text/html',
    );
    const outputDocument = parser.parseFromString('<body></body>', 'text/html');
    const outputBody = outputDocument.body;

    for (const child of Array.from(sourceDocument.body.childNodes)) {
      const sanitizedNode = this.sanitizeRichTextNode(child, outputDocument);
      if (sanitizedNode) {
        outputBody.appendChild(sanitizedNode);
      }
    }

    return this.serializeNodeChildren(outputBody);
  }

  private sanitizeRichTextNode(
    node: Node,
    outputDocument: Document,
  ): Node | DocumentFragment | null {
    if (node.nodeType === Node.TEXT_NODE) {
      return outputDocument.createTextNode(node.textContent ?? '');
    }
    if (node.nodeType !== Node.ELEMENT_NODE) {
      return null;
    }

    const sourceElement = node as HTMLElement;
    const tagName = sourceElement.tagName.toUpperCase();
    const childNodes = Array.from(sourceElement.childNodes)
      .map((child) => this.sanitizeRichTextNode(child, outputDocument))
      .filter((child): child is Node | DocumentFragment => child !== null);

    if (!RICH_TEXT_ALLOWED_TAGS.has(tagName)) {
      const fragment = outputDocument.createDocumentFragment();
      for (const child of childNodes) {
        fragment.appendChild(child);
      }
      return fragment;
    }

    const element = outputDocument.createElement(tagName.toLowerCase());
    if (tagName === 'A') {
      const href = this.sanitizeRichTextHref(
        sourceElement.getAttribute('href'),
      );
      if (href) {
        element.setAttribute('href', href);
        if (href.startsWith('http://') || href.startsWith('https://')) {
          element.setAttribute('target', '_blank');
          element.setAttribute('rel', 'noopener noreferrer');
        }
      }
    }
    for (const child of childNodes) {
      element.appendChild(child);
    }

    if (tagName === 'A' && !element.textContent?.trim()) {
      return null;
    }
    if (
      (tagName === 'UL' || tagName === 'OL') &&
      !element.querySelector('li')
    ) {
      return null;
    }
    if (tagName === 'LI' && !element.textContent?.trim()) {
      return null;
    }

    return element;
  }

  private sanitizeRichTextHref(rawHref: string | null): string | null {
    const href = rawHref?.trim();
    if (!href) {
      return null;
    }
    const lowerHref = href.toLowerCase();
    if (lowerHref.startsWith('/') || lowerHref.startsWith('#')) {
      return href;
    }
    if (
      lowerHref.startsWith('http://') ||
      lowerHref.startsWith('https://') ||
      lowerHref.startsWith('mailto:') ||
      lowerHref.startsWith('tel:')
    ) {
      return href;
    }
    return null;
  }

  private hasMeaningfulRichText(value: string): boolean {
    return (
      this.extractTextFromHtml(value)
        .replace(/\u00a0/g, ' ')
        .trim().length > 0
    );
  }

  private extractTextFromHtml(value: string): string {
    const parser = new DOMParser();
    const parsed = parser.parseFromString(`<body>${value}</body>`, 'text/html');
    return parsed.body.textContent ?? '';
  }

  private serializeNodeChildren(node: Node): string {
    const serializer = new XMLSerializer();
    let html = '';
    for (const child of Array.from(node.childNodes)) {
      html += serializer.serializeToString(child);
    }
    return html;
  }

  private replaceElementContentFromHtml(
    element: HTMLElement,
    html: string,
  ): void {
    if (!html) {
      element.replaceChildren();
      return;
    }

    const parser = new DOMParser();
    const parsed = parser.parseFromString(`<body>${html}</body>`, 'text/html');
    const nodes = Array.from(parsed.body.childNodes).map((child) =>
      document.importNode(child, true),
    );
    element.replaceChildren(...nodes);
  }

  private escapeHtml(value: string): string {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  seoDescriptionLength(language: ShopLanguage): number {
    return this.productForm.seoDescriptions[language].trim().length;
  }

  private slugify(source: string): string {
    return source
      .normalize('NFD')
      .replace(/\p{Diacritic}+/gu, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }

  private resolveFileExtension(filename: string): string {
    const lastDotIndex = filename.lastIndexOf('.');
    return lastDotIndex >= 0
      ? filename.slice(lastDotIndex + 1).toLowerCase()
      : '';
  }

  private isAllowedImageType(mimeType: string, filename: string): boolean {
    if (['image/jpeg', 'image/png', 'image/webp'].includes(mimeType)) {
      return true;
    }
    const extension = this.resolveFileExtension(filename);
    return ['jpg', 'jpeg', 'png', 'webp'].includes(extension);
  }

  private extractErrorMessage(error: unknown, fallback: string): string {
    const candidate = error as {
      error?: { message?: string };
      message?: string;
    };
    return candidate?.error?.message || candidate?.message || fallback;
  }
}
