package com.printcalculator.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.service.ProfileManager;
import com.printcalculator.service.SlicerService;
import com.printcalculator.service.StepConversionService;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/** Offline adapter: extended timing metadata never changes the website parser or statistics. */
final class CalibrationSlicer {
    private final CalibrationGCodeParser parser = new CalibrationGCodeParser();
    private final SlicerService delegate;

    CalibrationSlicer(String executable, String assimp, ProfileManager profiles, ObjectMapper mapper,
                      StepConversionService converter) {
        delegate = new SlicerService(executable, assimp, profiles, parser, mapper, converter);
    }

    CalibrationPrintStats slice(File file, String machine, String filament, String process,
                                Map<String,String> machineOverrides, Map<String,String> processOverrides) throws IOException {
        delegate.slice(file, machine, filament, process, machineOverrides, processOverrides);
        return parser.statistics();
    }
}
