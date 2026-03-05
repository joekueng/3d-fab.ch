package com.printcalculator.service;

import com.printcalculator.entity.NozzleLayerHeightOption;
import com.printcalculator.repository.NozzleLayerHeightOptionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class NozzleLayerHeightPolicyService {
    private static final BigDecimal DEFAULT_NOZZLE = BigDecimal.valueOf(0.40).setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal DEFAULT_LAYER = BigDecimal.valueOf(0.20).setScale(3, RoundingMode.HALF_UP);

    private final NozzleLayerHeightOptionRepository ruleRepo;

    public NozzleLayerHeightPolicyService(NozzleLayerHeightOptionRepository ruleRepo) {
        this.ruleRepo = ruleRepo;
    }

    public Map<BigDecimal, List<BigDecimal>> getActiveRulesByNozzle() {
        List<NozzleLayerHeightOption> rules = ruleRepo.findByIsActiveTrueOrderByNozzleDiameterMmAscLayerHeightMmAsc();
        if (rules.isEmpty()) {
            return fallbackRules();
        }

        Map<BigDecimal, List<BigDecimal>> byNozzle = new LinkedHashMap<>();
        for (NozzleLayerHeightOption rule : rules) {
            BigDecimal nozzle = normalizeNozzle(rule.getNozzleDiameterMm());
            BigDecimal layer = normalizeLayer(rule.getLayerHeightMm());
            if (nozzle == null || layer == null) {
                continue;
            }
            byNozzle.computeIfAbsent(nozzle, ignored -> new ArrayList<>()).add(layer);
        }

        byNozzle.values().forEach(this::sortAndDeduplicate);
        return byNozzle;
    }

    public BigDecimal normalizeNozzle(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal normalizeLayer(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    public BigDecimal resolveNozzle(BigDecimal requestedNozzle) {
        return normalizeNozzle(requestedNozzle != null ? requestedNozzle : DEFAULT_NOZZLE);
    }

    public BigDecimal resolveLayer(BigDecimal requestedLayer, BigDecimal nozzleDiameter) {
        if (requestedLayer != null) {
            return normalizeLayer(requestedLayer);
        }
        return defaultLayerForNozzle(nozzleDiameter);
    }

    public List<BigDecimal> allowedLayersForNozzle(BigDecimal nozzleDiameter) {
        BigDecimal nozzle = resolveNozzle(nozzleDiameter);
        List<BigDecimal> allowed = getActiveRulesByNozzle().get(nozzle);
        return allowed != null ? allowed : List.of();
    }

    public boolean isAllowed(BigDecimal nozzleDiameter, BigDecimal layerHeight) {
        BigDecimal layer = normalizeLayer(layerHeight);
        if (layer == null) {
            return false;
        }
        return allowedLayersForNozzle(nozzleDiameter)
                .stream()
                .anyMatch(allowed -> allowed.compareTo(layer) == 0);
    }

    public BigDecimal defaultLayerForNozzle(BigDecimal nozzleDiameter) {
        List<BigDecimal> allowed = allowedLayersForNozzle(nozzleDiameter);
        if (allowed.isEmpty()) {
            return DEFAULT_LAYER;
        }

        BigDecimal preferred = normalizeLayer(DEFAULT_LAYER);
        for (BigDecimal candidate : allowed) {
            if (candidate.compareTo(preferred) == 0) {
                return candidate;
            }
        }
        return allowed.get(0);
    }

    public String allowedLayersLabel(BigDecimal nozzleDiameter) {
        List<BigDecimal> allowed = allowedLayersForNozzle(nozzleDiameter);
        if (allowed.isEmpty()) {
            return "none";
        }
        return allowed.stream()
                .map(value -> String.format(Locale.ROOT, "%.2f", value))
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    private void sortAndDeduplicate(List<BigDecimal> values) {
        values.sort(Comparator.naturalOrder());
        for (int i = values.size() - 1; i > 0; i--) {
            if (values.get(i).compareTo(values.get(i - 1)) == 0) {
                values.remove(i);
            }
        }
    }

    private Map<BigDecimal, List<BigDecimal>> fallbackRules() {
        Map<BigDecimal, List<BigDecimal>> fallback = new LinkedHashMap<>();
        fallback.put(scaleNozzle(0.20), scaleLayers(0.04, 0.06, 0.08, 0.10, 0.12));
        fallback.put(scaleNozzle(0.40), scaleLayers(0.08, 0.12, 0.16, 0.20, 0.24, 0.28));
        fallback.put(scaleNozzle(0.60), scaleLayers(0.16, 0.20, 0.24, 0.30, 0.36));
        fallback.put(scaleNozzle(0.80), scaleLayers(0.20, 0.28, 0.36, 0.40, 0.48, 0.56));
        return fallback;
    }

    private BigDecimal scaleNozzle(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private List<BigDecimal> scaleLayers(double... values) {
        List<BigDecimal> scaled = new ArrayList<>();
        for (double value : values) {
            scaled.add(BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP));
        }
        return scaled;
    }
}
