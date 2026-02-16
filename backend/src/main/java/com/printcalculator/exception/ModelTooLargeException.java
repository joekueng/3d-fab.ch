package com.printcalculator.exception;

public class ModelTooLargeException extends RuntimeException {
    private final double modelX;
    private final double modelY;
    private final double modelZ;
    private final int buildX;
    private final int buildY;
    private final int buildZ;

    public ModelTooLargeException(double modelX, double modelY, double modelZ,
                                  int buildX, int buildY, int buildZ) {
        super("Model size exceeds build volume");
        this.modelX = modelX;
        this.modelY = modelY;
        this.modelZ = modelZ;
        this.buildX = buildX;
        this.buildY = buildY;
        this.buildZ = buildZ;
    }

    public double getModelX() {
        return modelX;
    }

    public double getModelY() {
        return modelY;
    }

    public double getModelZ() {
        return modelZ;
    }

    public int getBuildX() {
        return buildX;
    }

    public int getBuildY() {
        return buildY;
    }

    public int getBuildZ() {
        return buildZ;
    }
}
