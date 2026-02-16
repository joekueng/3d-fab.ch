package com.printcalculator.model;

import java.nio.file.Path;

public record StlShiftResult(Path shiftedPath,
                             double offsetX,
                             double offsetY,
                             double offsetZ,
                             boolean shifted) {
}
