package com.printcalculator.service.quote;

import com.printcalculator.dto.PrintSettingsDto;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.model.ModelDimensions;
import com.printcalculator.model.PrintStats;
import com.printcalculator.model.QuoteResult;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.OrcaProfileResolver;
import com.printcalculator.service.ProfileManager;
import com.printcalculator.service.QuoteCalculator;
import com.printcalculator.service.SlicerService;
import com.printcalculator.service.storage.ClamAVService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class QuoteSessionItemService {
    private final QuoteLineItemRepository lineItemRepo;
    private final QuoteSessionRepository sessionRepo;
    private final SlicerService slicerService;
    private final QuoteCalculator quoteCalculator;
    private final OrcaProfileResolver orcaProfileResolver;
    private final ClamAVService clamAVService;
    private final QuoteStorageService quoteStorageService;
    private final QuoteSessionSettingsService settingsService;
    private final ProfileManager profileManager;

    public QuoteSessionItemService(QuoteLineItemRepository lineItemRepo,
                                   QuoteSessionRepository sessionRepo,
                                   SlicerService slicerService,
                                   QuoteCalculator quoteCalculator,
                                   OrcaProfileResolver orcaProfileResolver,
                                   ClamAVService clamAVService,
                                   QuoteStorageService quoteStorageService,
                                   QuoteSessionSettingsService settingsService,
                                   ProfileManager profileManager) {
        this.lineItemRepo = lineItemRepo;
        this.sessionRepo = sessionRepo;
        this.slicerService = slicerService;
        this.quoteCalculator = quoteCalculator;
        this.orcaProfileResolver = orcaProfileResolver;
        this.clamAVService = clamAVService;
        this.quoteStorageService = quoteStorageService;
        this.settingsService = settingsService;
        this.profileManager = profileManager;
    }

    public QuoteLineItem addItemToSession(QuoteSession session, MultipartFile file, PrintSettingsDto settings) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if ("CONVERTED".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot modify a converted session");
        }

        clamAVService.scan(file.getInputStream());

        Path sessionStorageDir = quoteStorageService.sessionStorageDir(session.getId());
        String ext = quoteStorageService.getSafeExtension(file.getOriginalFilename(), "stl");
        String storedFilename = UUID.randomUUID() + "." + ext;
        Path persistentPath = quoteStorageService.resolveSessionPath(sessionStorageDir, storedFilename);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, persistentPath, StandardCopyOption.REPLACE_EXISTING);
        }

        Path convertedPersistentPath = null;
        try {
            boolean cadSession = "CAD_ACTIVE".equals(session.getStatus());

            if (cadSession) {
                settingsService.enforceCadPrintSettings(session, settings);
            } else {
                settingsService.applyPrintSettings(settings);
            }

            QuoteSessionSettingsService.NozzleLayerSettings nozzleAndLayer = settingsService.resolveNozzleAndLayer(settings);
            BigDecimal nozzleDiameter = nozzleAndLayer.nozzleDiameter();
            BigDecimal layerHeight = nozzleAndLayer.layerHeight();

            PrinterMachine machine = settingsService.resolvePrinterMachine(settings.getPrinterMachineId());
            FilamentVariant selectedVariant = settingsService.resolveFilamentVariant(settings);

            validateCadMaterialLock(session, cadSession, selectedVariant);

            if (!cadSession) {
                session.setMaterialCode(selectedVariant.getFilamentMaterialType().getMaterialCode());
                session.setNozzleDiameterMm(nozzleDiameter);
                session.setLayerHeightMm(layerHeight);
                session.setInfillPattern(settings.getInfillPattern());
                session.setInfillPercent(settings.getInfillDensity() != null ? settings.getInfillDensity().intValue() : 20);
                session.setSupportsEnabled(settings.getSupportsEnabled() != null ? settings.getSupportsEnabled() : false);
                sessionRepo.save(session);
            }

            OrcaProfileResolver.ResolvedProfiles profiles = orcaProfileResolver.resolve(machine, nozzleDiameter, selectedVariant);
            String processProfile = resolveProcessProfile(
                    settings,
                    profiles.machineProfileName(),
                    nozzleDiameter,
                    layerHeight
            );

            Map<String, String> processOverrides = new HashMap<>();
            processOverrides.put("layer_height", layerHeight.stripTrailingZeros().toPlainString());
            if (settings.getInfillDensity() != null) {
                processOverrides.put("sparse_infill_density", settings.getInfillDensity() + "%");
            }
            if (settings.getInfillPattern() != null) {
                processOverrides.put("sparse_infill_pattern", settings.getInfillPattern());
            }

            Path slicerInputPath = persistentPath;
            if ("3mf".equals(ext)) {
                String convertedFilename = UUID.randomUUID() + "-converted.stl";
                convertedPersistentPath = quoteStorageService.resolveSessionPath(sessionStorageDir, convertedFilename);
                slicerService.convert3mfToPersistentStl(persistentPath.toFile(), convertedPersistentPath);
                slicerInputPath = convertedPersistentPath;
            }

            PrintStats stats = slicerService.slice(
                    slicerInputPath.toFile(),
                    profiles.machineProfileName(),
                    profiles.filamentProfileName(),
                    processProfile,
                    null,
                    processOverrides
            );

            Optional<ModelDimensions> modelDimensions = slicerService.inspectModelDimensions(slicerInputPath.toFile());
            QuoteResult result = quoteCalculator.calculate(stats, machine.getPrinterDisplayName(), selectedVariant);

            QuoteLineItem item = buildLineItem(
                    session,
                    file.getOriginalFilename(),
                    settings,
                    selectedVariant,
                    nozzleDiameter,
                    layerHeight,
                    stats,
                    result,
                    modelDimensions,
                    persistentPath,
                    convertedPersistentPath
            );

            return lineItemRepo.save(item);
        } catch (Exception e) {
            Files.deleteIfExists(persistentPath);
            if (convertedPersistentPath != null) {
                Files.deleteIfExists(convertedPersistentPath);
            }
            throw e;
        }
    }

    private void validateCadMaterialLock(QuoteSession session, boolean cadSession, FilamentVariant selectedVariant) {
        if (!cadSession
                || session.getMaterialCode() == null
                || selectedVariant.getFilamentMaterialType() == null
                || selectedVariant.getFilamentMaterialType().getMaterialCode() == null) {
            return;
        }
        String lockedMaterial = settingsService.normalizeRequestedMaterialCode(session.getMaterialCode());
        String selectedMaterial = settingsService.normalizeRequestedMaterialCode(
                selectedVariant.getFilamentMaterialType().getMaterialCode()
        );
        if (!lockedMaterial.equals(selectedMaterial)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected filament does not match locked CAD material");
        }
    }

    private String resolveProcessProfile(PrintSettingsDto settings,
                                         String machineProfileName,
                                         BigDecimal nozzleDiameter,
                                         BigDecimal layerHeight) {
        if (machineProfileName == null || machineProfileName.isBlank() || layerHeight == null) {
            return resolveLegacyProcessProfile(settings);
        }

        String qualityHint = settingsService.resolveQuality(settings, layerHeight);
        return profileManager
                .findCompatibleProcessProfileName(machineProfileName, layerHeight, qualityHint)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Layer height " + layerHeight.stripTrailingZeros().toPlainString()
                                + " is not available for nozzle "
                                + (nozzleDiameter != null
                                ? nozzleDiameter.stripTrailingZeros().toPlainString()
                                : "-")
                                + " on printer profile " + machineProfileName
                ));
    }

    private String resolveLegacyProcessProfile(PrintSettingsDto settings) {
        if (settings.getLayerHeight() == null) {
            return "standard";
        }
        if (settings.getLayerHeight() >= 0.28) {
            return "draft";
        }
        if (settings.getLayerHeight() <= 0.12) {
            return "extra_fine";
        }
        return "standard";
    }

    private QuoteLineItem buildLineItem(QuoteSession session,
                                        String originalFilename,
                                        PrintSettingsDto settings,
                                        FilamentVariant selectedVariant,
                                        BigDecimal nozzleDiameter,
                                        BigDecimal layerHeight,
                                        PrintStats stats,
                                        QuoteResult result,
                                        Optional<ModelDimensions> modelDimensions,
                                        Path persistentPath,
                                        Path convertedPersistentPath) {
        QuoteLineItem item = new QuoteLineItem();
        item.setQuoteSession(session);
        item.setOriginalFilename(originalFilename);
        item.setStoredPath(quoteStorageService.toStoredPath(persistentPath));
        item.setQuantity(normalizeQuantity(settings.getQuantity()));
        item.setColorCode(selectedVariant.getColorName());
        item.setFilamentVariant(selectedVariant);
        item.setMaterialCode(selectedVariant.getFilamentMaterialType() != null
                ? selectedVariant.getFilamentMaterialType().getMaterialCode()
                : settingsService.normalizeRequestedMaterialCode(settings.getMaterial()));
        item.setQuality(settingsService.resolveQuality(settings, layerHeight));
        item.setNozzleDiameterMm(nozzleDiameter);
        item.setLayerHeightMm(layerHeight);
        item.setInfillPercent(settings.getInfillDensity() != null ? settings.getInfillDensity().intValue() : 20);
        item.setInfillPattern(settings.getInfillPattern());
        item.setSupportsEnabled(settings.getSupportsEnabled() != null ? settings.getSupportsEnabled() : false);
        item.setStatus("READY");

        item.setPrintTimeSeconds((int) stats.printTimeSeconds());
        item.setMaterialGrams(BigDecimal.valueOf(stats.filamentWeightGrams()));
        item.setUnitPriceChf(BigDecimal.valueOf(result.getTotalPrice()));

        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("machine_cost", result.getTotalPrice());
        breakdown.put("setup_fee", 0);
        if (convertedPersistentPath != null) {
            breakdown.put("convertedStoredPath", quoteStorageService.toStoredPath(convertedPersistentPath));
        }
        item.setPricingBreakdown(breakdown);

        item.setBoundingBoxXMm(modelDimensions
                .map(dim -> BigDecimal.valueOf(dim.xMm()))
                .orElseGet(() -> settings.getBoundingBoxX() != null ? BigDecimal.valueOf(settings.getBoundingBoxX()) : BigDecimal.ZERO));
        item.setBoundingBoxYMm(modelDimensions
                .map(dim -> BigDecimal.valueOf(dim.yMm()))
                .orElseGet(() -> settings.getBoundingBoxY() != null ? BigDecimal.valueOf(settings.getBoundingBoxY()) : BigDecimal.ZERO));
        item.setBoundingBoxZMm(modelDimensions
                .map(dim -> BigDecimal.valueOf(dim.zMm()))
                .orElseGet(() -> settings.getBoundingBoxZ() != null ? BigDecimal.valueOf(settings.getBoundingBoxZ()) : BigDecimal.ZERO));

        item.setCreatedAt(OffsetDateTime.now());
        item.setUpdatedAt(OffsetDateTime.now());
        return item;
    }

    private int normalizeQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            return 1;
        }
        return quantity;
    }
}
