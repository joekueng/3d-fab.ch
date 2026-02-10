package com.printcalculator.service;

import com.printcalculator.model.PrintStats;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GCodeParser {

    // OrcaSlicer/BambuStudio format
    // ; estimated printing time = 1h 2m 3s
    // ; filament used [g] = 12.34
    // ; filament used [mm] = 1234.56
    private static final Pattern TIME_PATTERN = Pattern.compile(";\\s*estimated printing time.*=\\s*(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILAMENT_G_PATTERN = Pattern.compile(";\\s*filament used \\[g\\]\\s*=\\s*(.*)");
    private static final Pattern FILAMENT_MM_PATTERN = Pattern.compile(";\\s*filament used \\[mm\\]\\s*=\\s*(.*)");

    public PrintStats parse(File gcodeFile) throws IOException {
        long seconds = 0;
        double weightG = 0;
        double lengthMm = 0;
        String timeFormatted = "";

        try (BufferedReader reader = new BufferedReader(new FileReader(gcodeFile))) {
            String line;

            // Scan entire file as metadata is often at the end
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // OrcaSlicer comments start with ;
                if (!line.startsWith(";")) {
                    continue;
                }

                if (line.toLowerCase().contains("estimated printing time")) {
                    System.out.println("DEBUG: Found potential time line: '" + line + "'");
                }

                Matcher timeMatcher = TIME_PATTERN.matcher(line);
                if (timeMatcher.find()) {
                    timeFormatted = timeMatcher.group(1).trim();
                    seconds = parseTimeString(timeFormatted);
                    System.out.println("GCodeParser: Found time: " + timeFormatted + " (" + seconds + "s)");
                }

                Matcher weightMatcher = FILAMENT_G_PATTERN.matcher(line);
                if (weightMatcher.find()) {
                    try {
                        weightG = Double.parseDouble(weightMatcher.group(1).trim());
                         System.out.println("GCodeParser: Found weight: " + weightG + "g");
                    } catch (NumberFormatException ignored) {}
                }
                
                Matcher lengthMatcher = FILAMENT_MM_PATTERN.matcher(line);
                if (lengthMatcher.find()) {
                    try {
                        lengthMm = Double.parseDouble(lengthMatcher.group(1).trim());
                         System.out.println("GCodeParser: Found length: " + lengthMm + "mm");
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        
        return new PrintStats(seconds, timeFormatted, weightG, lengthMm);
    }

    private long parseTimeString(String timeStr) {
        // Formats: "1d 2h 3m 4s" or "1h 20m 10s"
        long totalSeconds = 0;
        
        Matcher d = Pattern.compile("(\\d+)d").matcher(timeStr);
        if (d.find()) totalSeconds += Long.parseLong(d.group(1)) * 86400;

        Matcher h = Pattern.compile("(\\d+)h").matcher(timeStr);
        if (h.find()) totalSeconds += Long.parseLong(h.group(1)) * 3600;

        Matcher m = Pattern.compile("(\\d+)m").matcher(timeStr);
        if (m.find()) totalSeconds += Long.parseLong(m.group(1)) * 60;

        Matcher s = Pattern.compile("(\\d+)s").matcher(timeStr);
        if (s.find()) totalSeconds += Long.parseLong(s.group(1));

        return totalSeconds;
    }
}
