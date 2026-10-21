package com.printcalculator.tools;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/** Scope of the A1 timing comparison, not proof that a generated toolpath is printable. */
public final class A1TimeReference {
    private static final Set<String> MATERIALS = Set.of("PLA", "PLA TOUGH", "PETG", "TPU");
    private A1TimeReference() {}

    public static boolean supports(String material, BigDecimal nozzle) {
        String code = material == null ? "" : material.trim().toUpperCase(Locale.ROOT);
        return MATERIALS.contains(code) && nozzle != null
                && !(code.equals("TPU") && nozzle.compareTo(new BigDecimal("0.2")) == 0);
    }
}
