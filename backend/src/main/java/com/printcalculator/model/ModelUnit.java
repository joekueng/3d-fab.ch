package com.printcalculator.model;

public enum ModelUnit {
    MILLIMETRES("mm", 1), CENTIMETRES("cm", 10), METRES("m", 1000), INCHES("in", 25.4);

    private final String code;
    private final double millimetres;

    ModelUnit(String code, double millimetres) {
        this.code = code;
        this.millimetres = millimetres;
    }

    public double millimetres() { return millimetres; }
    public String code() { return code; }

    public static ModelUnit parse(String code) {
        if (code == null) return MILLIMETRES;
        for (ModelUnit unit : values()) {
            if (unit.code.equals(code)) return unit;
        }
        throw new IllegalArgumentException("Unsupported model unit. Allowed: mm, cm, m, in");
    }
}
