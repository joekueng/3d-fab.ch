import { CommonModule, DOCUMENT, isPlatformBrowser } from '@angular/common';
import {
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
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
  AdminTranslateShopProductPayload,
  AdminTranslateShopProductResponse,
  AdminUpsertShopCategoryPayload,
  AdminUpsertShopProductPayload,
  AdminUpsertShopProductVariantPayload,
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
import {
  hasMeaningfulRichText,
  normalizeDescriptionForEditor,
  normalizeRichTextStorageValue,
} from './admin-shop-rich-text.util';
import { AdminShopRichTextEditorComponent } from './admin-shop-rich-text-editor.component';
import {
  CategoryFormState,
  ProductFormState,
  ProductImageItem,
  ProductImageUploadState,
  ProductMaterialFormState,
  ProductMode,
  ProductStatusFilter,
  ShopLanguage,
} from './admin-shop.types';
import {
  LANGUAGE_LABELS,
  MAX_LIST_PANEL_WIDTH_PERCENT,
  MAX_MODEL_FILE_SIZE_BYTES,
  MEDIA_LANGUAGES,
  MIN_LIST_PANEL_WIDTH_PERCENT,
  PRODUCT_STATUS_FILTERS,
  SHOP_LANGUAGES,
  SHOP_LIST_PANEL_WIDTH_STORAGE_KEY,
} from './admin-shop.config';
import {
  filterStockedFilamentVariants,
  getNextAvailableMaterialCode,
  getStockMaterialCodes,
  getStockVariantsForMaterial,
  resolveStockMaterialDefaultColorKey,
  stockVariantKey,
  stockVariantLabel,
} from './admin-shop-stock.util';
import {
  areAllMediaTitlesBlank,
  buildProductImages,
  cloneMediaTranslations,
  createEmptyMediaTranslations,
  deriveDefaultMediaTitle,
  isMediaTranslationComplete,
  normalizeMediaTranslations,
  validateMediaTranslations,
} from './admin-shop-image.util';
import { AdminLanguageToolbarComponent } from '../../../shared/components/admin-language-toolbar/admin-language-toolbar.component';
import {
  AdminLanguageStatus,
  buildAdminLanguageStatusMap,
  mergeLocalizedTextMap,
} from '../../../shared/utils/admin-localization.util';
@Component({
  selector: 'app-admin-shop',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    AdminShopRichTextEditorComponent,
    AdminLanguageToolbarComponent,
  ],
  templateUrl: './admin-shop.component.html',
  styleUrl: './admin-shop.component.scss',
})
export class AdminShopComponent implements OnInit, OnDestroy {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly documentRef = inject(DOCUMENT);
  private readonly adminShopService = inject(AdminShopService);
  private readonly adminOperationsService = inject(AdminOperationsService);
  @ViewChild('workspaceRef')
  private readonly workspaceRef?: ElementRef<HTMLDivElement>;

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
  translatingProduct = false;
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
  overwriteExistingTranslations = false;

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
    if (this.isBrowser) {
      this.documentRef.body?.style.removeProperty('cursor');
    }
  }

  @HostListener('window:pointermove', ['$event'])
  onWindowPointerMove(event: PointerEvent): void {
    if (!this.isBrowser || !this.isResizingPanels) {
      return;
    }
    this.updateListPanelWidthFromPointer(event.clientX);
  }

  @HostListener('window:pointerup')
  @HostListener('window:pointercancel')
  onWindowPointerUp(): void {
    if (!this.isBrowser || !this.isResizingPanels) {
      return;
    }
    this.isResizingPanels = false;
    if (this.documentRef.body) {
      this.documentRef.body.style.cursor = '';
    }
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
          filterStockedFilamentVariants(filamentVariants);
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
        this.productImages = buildProductImages(
          product,
          this.imageUploadState.activeLanguage,
        );
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
      !this.confirmBrowser(
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
    if (!this.isBrowser || window.innerWidth <= 1060) {
      return;
    }
    event.preventDefault();
    this.isResizingPanels = true;
    if (this.documentRef.body) {
      this.documentRef.body.style.cursor = 'col-resize';
    }
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
      !this.confirmBrowser(
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
    const source =
      this.categoryForm.names[this.activeContentLanguage] ||
      this.categoryForm.names['it'];
    this.categoryForm.slug = this.slugify(source);
  }

  translateProductFromCurrentLanguage(): void {
    if (this.translatingProduct) {
      return;
    }

    const sourceLanguage = this.activeContentLanguage;
    if (!this.productForm.names[sourceLanguage].trim()) {
      this.errorMessage = `Il nome prodotto ${this.languageLabels[sourceLanguage]} e obbligatorio per avviare la traduzione.`;
      this.successMessage = null;
      return;
    }

    const payload = this.buildProductTranslationPayload(sourceLanguage);
    this.translatingProduct = true;
    this.errorMessage = null;
    this.successMessage = null;

    this.adminShopService.translateProduct(payload).subscribe({
      next: (response) => {
        this.translatingProduct = false;
        this.applyProductTranslation(response, payload.overwriteExisting);
        this.successMessage = response.targetLanguages.length
          ? `Traduzioni ${response.targetLanguages
              .map((language) => this.languageLabels[language])
              .join(' / ')} aggiornate nel form.`
          : 'Nessun campo da tradurre.';
      },
      error: (error) => {
        this.translatingProduct = false;
        this.errorMessage = this.extractErrorMessage(
          error,
          'Traduzione prodotto non riuscita.',
        );
      },
    });
  }

  canTranslateProductFromCurrentLanguage(): boolean {
    return (
      !this.translatingProduct &&
      !!this.productForm.names[this.activeContentLanguage].trim()
    );
  }

  setActiveContentLanguage(language: ShopLanguage): void {
    this.activeContentLanguage = language;
  }

  isContentLanguageComplete(language: ShopLanguage): boolean {
    return !!this.productForm.names[language].trim();
  }

  isContentLanguageStarted(language: ShopLanguage): boolean {
    return (
      !!this.productForm.names[language].trim() ||
      !!this.productForm.excerpts[language].trim() ||
      hasMeaningfulRichText(
        this.productForm.descriptions[language],
        this.isBrowser,
      )
    );
  }

  isContentLanguageIncomplete(language: ShopLanguage): boolean {
    return (
      this.isContentLanguageStarted(language) &&
      !this.isContentLanguageComplete(language)
    );
  }

  contentLanguageStatuses(): Record<ShopLanguage, AdminLanguageStatus> {
    return buildAdminLanguageStatusMap(
      this.shopLanguages,
      (language) => this.isContentLanguageComplete(language),
      (language) => this.isContentLanguageStarted(language),
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

  seoLanguageStatuses(): Record<ShopLanguage, AdminLanguageStatus> {
    return buildAdminLanguageStatusMap(
      this.shopLanguages,
      (language) => this.isSeoLanguageComplete(language),
      (language) => this.isSeoLanguageStarted(language),
    );
  }

  isCategoryContentLanguageComplete(language: ShopLanguage): boolean {
    return !!this.categoryForm.names[language].trim();
  }

  isCategoryContentLanguageStarted(language: ShopLanguage): boolean {
    return (
      !!this.categoryForm.names[language].trim() ||
      !!this.categoryForm.descriptions[language].trim()
    );
  }

  isCategoryContentLanguageIncomplete(language: ShopLanguage): boolean {
    return (
      this.isCategoryContentLanguageStarted(language) &&
      !this.isCategoryContentLanguageComplete(language)
    );
  }

  categoryContentLanguageStatuses(): Record<
    ShopLanguage,
    AdminLanguageStatus
  > {
    return buildAdminLanguageStatusMap(
      this.shopLanguages,
      (language) => this.isCategoryContentLanguageComplete(language),
      (language) => this.isCategoryContentLanguageStarted(language),
    );
  }

  isCategorySeoLanguageComplete(language: ShopLanguage): boolean {
    return (
      !!this.categoryForm.seoTitles[language].trim() &&
      !!this.categoryForm.seoDescriptions[language].trim()
    );
  }

  isCategorySeoLanguageStarted(language: ShopLanguage): boolean {
    return (
      !!this.categoryForm.seoTitles[language].trim() ||
      !!this.categoryForm.seoDescriptions[language].trim()
    );
  }

  isCategorySeoLanguageIncomplete(language: ShopLanguage): boolean {
    return (
      this.isCategorySeoLanguageStarted(language) &&
      !this.isCategorySeoLanguageComplete(language)
    );
  }

  categorySeoLanguageStatuses(): Record<ShopLanguage, AdminLanguageStatus> {
    return buildAdminLanguageStatusMap(
      this.shopLanguages,
      (language) => this.isCategorySeoLanguageComplete(language),
      (language) => this.isCategorySeoLanguageStarted(language),
    );
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
      nextMaterials[0] = {
        ...nextMaterials[0],
        isDefault: true,
        defaultColorKey: this.resolveMaterialDefaultColorKey(
          nextMaterials[0].materialCode,
          nextMaterials[0].defaultColorKey,
        ),
      };
    }
    this.productForm.materials = nextMaterials;
  }

  setDefaultMaterial(index: number): void {
    this.productForm.materials = this.productForm.materials.map(
      (material, currentIndex) => ({
        ...material,
        isDefault: currentIndex === index,
        defaultColorKey: this.resolveMaterialDefaultColorKey(
          material.materialCode,
          material.defaultColorKey,
        ),
      }),
    );
  }

  onMaterialCodeChange(index: number, nextMaterialCode: string): void {
    const normalizedMaterialCode = nextMaterialCode.trim().toUpperCase();
    this.productForm.materials = this.productForm.materials.map(
      (material, currentIndex) => {
        if (currentIndex !== index) {
          return material;
        }

        return {
          ...material,
          materialCode: normalizedMaterialCode,
          defaultColorKey: this.resolveMaterialDefaultColorKey(
            normalizedMaterialCode,
            material.defaultColorKey,
          ),
        };
      },
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

  materialColorOptions(
    materialCode: string,
  ): Array<{ key: string; label: string }> {
    const normalizedMaterialCode = materialCode.trim().toUpperCase();
    return this.stockVariantsForMaterial(normalizedMaterialCode).map(
      (variant) => ({
        key: this.variantKey(
          normalizedMaterialCode,
          variant.colorName,
          variant.colorHex,
        ),
        label: this.stockVariantLabel(variant),
      }),
    );
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
      !this.confirmBrowser(
        'Rimuovere il modello 3D associato a questo prodotto?',
      )
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

    const nextTranslations = cloneMediaTranslations(
      this.imageUploadState.translations,
    );
    if (areAllMediaTitlesBlank(nextTranslations, this.mediaLanguages)) {
      const defaultTitle = deriveDefaultMediaTitle(file.name);
      for (const language of this.mediaLanguages) {
        nextTranslations[language].title = defaultTitle;
      }
    }

    this.imageUploadState = {
      ...this.imageUploadState,
      file,
      previewUrl: this.isBrowser ? URL.createObjectURL(file) : null,
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
    return isMediaTranslationComplete(
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

  imageLanguageStatuses(): Record<AdminMediaLanguage, AdminLanguageStatus> {
    return buildAdminLanguageStatusMap(
      this.mediaLanguages,
      (language) => this.isImageLanguageComplete(language),
      (language) => this.isImageLanguageStarted(language),
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

    const validationError = validateMediaTranslations(
      this.imageUploadState.translations,
      this.mediaLanguages,
      this.languageLabels,
    );
    if (validationError) {
      this.errorMessage = validationError;
      this.successMessage = null;
      return;
    }

    const normalizedTranslations = normalizeMediaTranslations(
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

    if (!this.confirmBrowser('Rimuovere questa immagine dal prodotto?')) {
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
    if (!this.isBrowser) {
      return;
    }
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
    if (!this.isBrowser) {
      return;
    }
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
      names: this.createEmptyLocalizedTextRecord(),
      descriptions: this.createEmptyLocalizedTextRecord(),
      seoTitles: this.createEmptyLocalizedTextRecord(),
      seoDescriptions: this.createEmptyLocalizedTextRecord(),
      ogTitle: '',
      ogDescription: '',
      indexable: true,
      isActive: true,
      sortOrder: 0,
    };
  }

  private resetCategoryForm(): void {
    this.activeContentLanguage = 'it';
    Object.assign(this.categoryForm, this.createEmptyCategoryForm());
  }

  private loadCategoryIntoForm(category: AdminShopCategory): void {
    Object.assign(this.categoryForm, {
      id: category.id,
      parentCategoryId: category.parentCategoryId,
      slug: category.slug ?? '',
      names: {
        it: category.nameIt ?? category.name ?? '',
        en: category.nameEn ?? category.name ?? '',
        de: category.nameDe ?? category.name ?? '',
        fr: category.nameFr ?? category.name ?? '',
      },
      descriptions: {
        it: category.descriptionIt ?? category.description ?? '',
        en: category.descriptionEn ?? category.description ?? '',
        de: category.descriptionDe ?? category.description ?? '',
        fr: category.descriptionFr ?? category.description ?? '',
      },
      seoTitles: {
        it: category.seoTitleIt ?? category.seoTitle ?? '',
        en: category.seoTitleEn ?? category.seoTitle ?? '',
        de: category.seoTitleDe ?? category.seoTitle ?? '',
        fr: category.seoTitleFr ?? category.seoTitle ?? '',
      },
      seoDescriptions: {
        it: category.seoDescriptionIt ?? category.seoDescription ?? '',
        en: category.seoDescriptionEn ?? category.seoDescription ?? '',
        de: category.seoDescriptionDe ?? category.seoDescription ?? '',
        fr: category.seoDescriptionFr ?? category.seoDescription ?? '',
      },
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
      name: this.categoryForm.names['it'].trim(),
      nameIt: this.categoryForm.names['it'].trim(),
      nameEn: this.categoryForm.names['en'].trim(),
      nameDe: this.categoryForm.names['de'].trim(),
      nameFr: this.categoryForm.names['fr'].trim(),
      description: this.optionalValue(this.categoryForm.descriptions['it']),
      descriptionIt: this.optionalValue(this.categoryForm.descriptions['it']),
      descriptionEn: this.optionalValue(this.categoryForm.descriptions['en']),
      descriptionDe: this.optionalValue(this.categoryForm.descriptions['de']),
      descriptionFr: this.optionalValue(this.categoryForm.descriptions['fr']),
      seoTitle: this.optionalValue(this.categoryForm.seoTitles['it']),
      seoTitleIt: this.optionalValue(this.categoryForm.seoTitles['it']),
      seoTitleEn: this.optionalValue(this.categoryForm.seoTitles['en']),
      seoTitleDe: this.optionalValue(this.categoryForm.seoTitles['de']),
      seoTitleFr: this.optionalValue(this.categoryForm.seoTitles['fr']),
      seoDescription: this.optionalValue(
        this.categoryForm.seoDescriptions['it'],
      ),
      seoDescriptionIt: this.optionalValue(
        this.categoryForm.seoDescriptions['it'],
      ),
      seoDescriptionEn: this.optionalValue(
        this.categoryForm.seoDescriptions['en'],
      ),
      seoDescriptionDe: this.optionalValue(
        this.categoryForm.seoDescriptions['de'],
      ),
      seoDescriptionFr: this.optionalValue(
        this.categoryForm.seoDescriptions['fr'],
      ),
      ogTitle: this.categoryForm.ogTitle.trim(),
      ogDescription: this.categoryForm.ogDescription.trim(),
      indexable: this.categoryForm.indexable,
      isActive: this.categoryForm.isActive,
      sortOrder: Number(this.categoryForm.sortOrder) || 0,
    };
  }

  private validateCategoryForm(): string | null {
    for (const language of this.shopLanguages) {
      if (!this.categoryForm.names[language].trim()) {
        return `Il nome categoria ${this.languageLabels[language]} è obbligatorio.`;
      }
    }
    if (!this.categoryForm.slug.trim()) {
      return 'Lo slug categoria è obbligatorio.';
    }
    for (const language of this.shopLanguages) {
      if (this.categoryForm.seoDescriptions[language].trim().length > 160) {
        return `La SEO description categoria ${this.languageLabels[language]} deve avere massimo 160 caratteri.`;
      }
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
  }

  private createEmptyMaterialForm(
    sortOrder: number,
    isDefault: boolean,
    materialCode = '',
  ): ProductMaterialFormState {
    return {
      materialCode,
      defaultColorKey: this.resolveMaterialDefaultColorKey(materialCode),
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
        it: normalizeDescriptionForEditor(product.descriptionIt, this.isBrowser),
        en: normalizeDescriptionForEditor(product.descriptionEn, this.isBrowser),
        de: normalizeDescriptionForEditor(product.descriptionDe, this.isBrowser),
        fr: normalizeDescriptionForEditor(product.descriptionFr, this.isBrowser),
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
        const defaultVariantForMaterial =
          materialVariants.find((variant) => variant.isDefault) ?? null;
        const persistedDefaultColorKey = defaultVariantForMaterial
          ? this.variantKey(
              materialCode,
              defaultVariantForMaterial.colorName,
              defaultVariantForMaterial.colorHex,
            )
          : null;
        return {
          materialCode,
          defaultColorKey: this.resolveMaterialDefaultColorKey(
            materialCode,
            persistedDefaultColorKey,
          ),
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

  private buildProductTranslationPayload(
    sourceLanguage: ShopLanguage,
  ): AdminTranslateShopProductPayload {
    const materialCodes = Array.from(
      new Set(
        this.productForm.materials
          .map((material) => material.materialCode.trim().toUpperCase())
          .filter((materialCode) => !!materialCode),
      ),
    );

    return {
      categoryId: this.productForm.categoryId || undefined,
      sourceLanguage,
      overwriteExisting: this.overwriteExistingTranslations,
      materialCodes,
      names: { ...this.productForm.names },
      excerpts: { ...this.productForm.excerpts },
      descriptions: { ...this.productForm.descriptions },
      seoTitles: { ...this.productForm.seoTitles },
      seoDescriptions: { ...this.productForm.seoDescriptions },
    };
  }

  private applyProductTranslation(
    response: AdminTranslateShopProductResponse,
    overwriteExisting: boolean,
  ): void {
    mergeLocalizedTextMap(this.productForm.names, response.names, {
      overwriteExisting,
      targetLanguages: response.targetLanguages,
    });
    mergeLocalizedTextMap(this.productForm.excerpts, response.excerpts, {
      overwriteExisting,
      targetLanguages: response.targetLanguages,
    });
    mergeLocalizedTextMap(this.productForm.seoTitles, response.seoTitles, {
      overwriteExisting,
      targetLanguages: response.targetLanguages,
    });
    mergeLocalizedTextMap(
      this.productForm.seoDescriptions,
      response.seoDescriptions,
      {
        overwriteExisting,
        targetLanguages: response.targetLanguages,
      },
    );

    for (const language of response.targetLanguages) {
      this.mergeLocalizedText(
        this.productForm.descriptions,
        response.descriptions,
        language,
        overwriteExisting,
        true,
      );
    }

  }

  private mergeLocalizedText(
    target: Record<ShopLanguage, string>,
    translated:
      | Partial<Record<ShopLanguage, string>>
      | Record<ShopLanguage, string>
      | undefined,
    language: ShopLanguage,
    overwriteExisting: boolean,
    richText = false,
  ): void {
    const incoming = translated?.[language];
    if (incoming === undefined) {
      return;
    }

    const hasCurrentValue = richText
      ? hasMeaningfulRichText(target[language] ?? '', this.isBrowser)
      : !!target[language]?.trim();
    if (hasCurrentValue && !overwriteExisting) {
      return;
    }

    target[language] = richText
      ? normalizeDescriptionForEditor(incoming, this.isBrowser)
      : incoming.trim();
  }

  private buildVariantsFromMaterials(): AdminUpsertShopProductVariantPayload[] {
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
    const variants: AdminUpsertShopProductVariantPayload[] = [];
    let defaultAssigned = false;

    const sortedMaterials = [...this.productForm.materials].sort(
      (left, right) => left.sortOrder - right.sortOrder,
    );

    for (const material of sortedMaterials) {
      const materialCode = material.materialCode.trim().toUpperCase();
      const stockVariants = this.stockVariantsForMaterial(materialCode);
      const selectedDefaultColorKey = this.resolveMaterialDefaultColorKey(
        materialCode,
        material.defaultColorKey,
      );

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
          variantKey === selectedDefaultColorKey;

        variants.push({
          id: existingVariant?.id,
          sku: this.optionalValue(existingVariant?.sku ?? ''),
          variantLabel: materialCode,
          colorName: stockVariant.colorName.trim(),
          colorLabelIt: this.optionalValue(stockVariant.colorLabelIt ?? ''),
          colorLabelEn: this.optionalValue(stockVariant.colorLabelEn ?? ''),
          colorLabelDe: this.optionalValue(stockVariant.colorLabelDe ?? ''),
          colorLabelFr: this.optionalValue(stockVariant.colorLabelFr ?? ''),
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
    return getStockMaterialCodes(this.stockFilamentVariants);
  }

  private stockVariantsForMaterial(
    materialCode: string,
  ): AdminFilamentVariant[] {
    return getStockVariantsForMaterial(this.stockFilamentVariants, materialCode);
  }

  private resolveMaterialDefaultColorKey(
    materialCode: string,
    preferredKey?: string | null,
  ): string {
    return resolveStockMaterialDefaultColorKey(
      this.stockFilamentVariants,
      materialCode,
      preferredKey,
    );
  }

  private stockVariantLabel(variant: AdminFilamentVariant): string {
    return stockVariantLabel(variant);
  }

  private nextAvailableMaterialCode(): string | null {
    return getNextAvailableMaterialCode(
      this.stockFilamentVariants,
      this.productForm.materials,
    );
  }

  private variantKey(
    materialCode: string | null | undefined,
    colorName: string | null | undefined,
    colorHex: string | null | undefined,
  ): string {
    return stockVariantKey(materialCode, colorName, colorHex);
  }

  private updateSelectedProduct(product: AdminShopProduct): void {
    this.selectedProduct = product;
    this.selectedProductId = product.id;
    this.productImages = buildProductImages(
      product,
      this.imageUploadState.activeLanguage,
    );
    this.loadProductIntoForm(product);
    this.resetImageUploadState(product);
  }

  private createEmptyImageUploadState(): ProductImageUploadState {
    return {
      file: null,
      previewUrl: null,
      activeLanguage: 'it',
      translations: createEmptyMediaTranslations(),
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
      translations: createEmptyMediaTranslations(),
      sortOrder: Math.max(0, nextSortOrder),
      isPrimary: (product?.images.length ?? 0) === 0,
      saving: false,
    };
  }

  private revokeImagePreviewUrl(previewUrl: string | null): void {
    if (!this.isBrowser) {
      return;
    }
    if (previewUrl?.startsWith('blob:')) {
      URL.revokeObjectURL(previewUrl);
    }
  }

  private optionalValue(value: string): string | undefined {
    const normalized = value.trim();
    return normalized ? normalized : undefined;
  }

  private optionalRichTextValue(value: string): string | undefined {
    const normalized = normalizeRichTextStorageValue(value, this.isBrowser);
    return normalized ? normalized : undefined;
  }

  private confirmBrowser(message: string): boolean {
    return this.isBrowser ? window.confirm(message) : false;
  }

  seoDescriptionLength(language: ShopLanguage): number {
    return this.productForm.seoDescriptions[language].trim().length;
  }

  categorySeoDescriptionLength(language: ShopLanguage): number {
    return this.categoryForm.seoDescriptions[language].trim().length;
  }

  private createEmptyLocalizedTextRecord(): Record<ShopLanguage, string> {
    return {
      it: '',
      en: '',
      de: '',
      fr: '',
    };
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
