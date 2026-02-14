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
    private static final Pattern TOTAL_ESTIMATED_TIME_PATTERN = Pattern.compile(
            ";\\s*.*total\\s+estimated\\s+time\\s*[:=]\\s*([^;]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MODEL_PRINTING_TIME_PATTERN = Pattern.compile(
            ";\\s*.*model\\s+printing\\s+time\\s*[:=]\\s*([^;]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_PATTERN = Pattern.compile(
            ";\\s*(?:estimated\\s+printing\\s+time|estimated\\s+print\\s+time|print\\s+time).*?[:=]\\s*(.*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FILAMENT_G_PATTERN = Pattern.compile(";\\s*filament used \\[g\\]\\s*=\\s*([^;\\(\\n\\r]+)(?:\\s*\\(([^,]+) model,\\s*([^ ]+) support\\))?");
    private static final Pattern FILAMENT_MM_PATTERN = Pattern.compile(";\\s*filament used \\[mm\\]\\s*=\\s*(.*)");

    public PrintStats parse(File gcodeFile) throws IOException {
        long seconds = 0;
        double weightG = 0;
        double lengthMm = 0;
        Double modelWeightG = null;
        Double supportWeightG = null;
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

                Matcher totalTimeMatcher = TOTAL_ESTIMATED_TIME_PATTERN.matcher(line);
                if (totalTimeMatcher.find()) {
                    timeFormatted = totalTimeMatcher.group(1).trim();
                    seconds = parseTimeString(timeFormatted);
                    System.out.println("GCodeParser: Found total estimated time: " + timeFormatted + " (" + seconds + "s)");
                    continue;
                }

                Matcher modelTimeMatcher = MODEL_PRINTING_TIME_PATTERN.matcher(line);
                if (modelTimeMatcher.find()) {
                    timeFormatted = modelTimeMatcher.group(1).trim();
                    seconds = parseTimeString(timeFormatted);
                    System.out.println("GCodeParser: Found model printing time: " + timeFormatted + " (" + seconds + "s)");
                    continue;
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
                        System.out.println("GCodeParser: Found total weight: " + weightG + "g");
                        
                        // Check if we have groups 2 and 3 for breakdown
                        if (weightMatcher.groupCount() >= 3 && weightMatcher.group(2) != null) {
                            modelWeightG = Double.parseDouble(weightMatcher.group(2).trim());
                            supportWeightG = Double.parseDouble(weightMatcher.group(3).trim());
                            System.out.println("GCodeParser: Found breakdown - Model: " + modelWeightG + "g, Support: " + supportWeightG + "g");
                        }
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
        
        return PrintStats.builder()
                .printTimeSeconds(seconds)
                .printTimeFormatted(timeFormatted)
                .filamentWeightGrams(weightG)
                .filamentLengthMm(lengthMm)
                .modelWeightGrams(modelWeightG)
                .supportWeightGrams(supportWeightG)
                .build();
    }

    private long parseTimeString(String timeStr) {
        // Formats: "1d 2h 3m 4s", "1h 20m 10s", "01:23:45", "12:34"
        String lower = timeStr.toLowerCase();
        double totalSeconds = 0;
        boolean matched = false;

        Matcher d = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*d").matcher(lower);
        if (d.find()) {
            totalSeconds += Double.parseDouble(d.group(1)) * 86400;
            matched = true;
        }

        Matcher h = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*h").matcher(lower);
        if (h.find()) {
            totalSeconds += Double.parseDouble(h.group(1)) * 3600;
            matched = true;
        }

        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*m").matcher(lower);
        if (m.find()) {
            totalSeconds += Double.parseDouble(m.group(1)) * 60;
            matched = true;
        }

        Matcher s = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*s").matcher(lower);
        if (s.find()) {
            totalSeconds += Double.parseDouble(s.group(1));
            matched = true;
        }

        if (matched) {
            return Math.round(totalSeconds);
        }

        long daySeconds = 0;
        Matcher dayPrefix = Pattern.compile("(\\d+)\\s*d").matcher(lower);
        if (dayPrefix.find()) {
            daySeconds = Long.parseLong(dayPrefix.group(1)) * 86400;
        }

        Matcher hms = Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})").matcher(lower);
        if (hms.find()) {
            long hours = Long.parseLong(hms.group(1));
            long minutes = Long.parseLong(hms.group(2));
            long seconds = Long.parseLong(hms.group(3));
            return daySeconds + hours * 3600 + minutes * 60 + seconds;
        }

        Matcher ms = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(lower);
        if (ms.find()) {
            long minutes = Long.parseLong(ms.group(1));
            long seconds = Long.parseLong(ms.group(2));
            return daySeconds + minutes * 60 + seconds;
        }

        return 0;
    }
}
