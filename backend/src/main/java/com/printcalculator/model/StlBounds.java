package com.printcalculator.model;

public record StlBounds(double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ) {
    public double sizeX() {
        return maxX - minX;
    }

    public double sizeY() {
        return maxY - minY;
    }

    public double sizeZ() {
        return maxZ - minZ;
    }
}
