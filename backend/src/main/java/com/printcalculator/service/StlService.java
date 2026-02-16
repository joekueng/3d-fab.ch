package com.printcalculator.service;

import com.printcalculator.model.StlBounds;
import com.printcalculator.model.StlShiftResult;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class StlService {

    public StlBounds readBounds(File stlFile) throws IOException {
        long size = stlFile.length();
        if (size >= 84 && isBinaryStl(stlFile, size)) {
            return readBinaryBounds(stlFile);
        }
        return readAsciiBounds(stlFile);
    }

    public StlShiftResult shiftToFitIfNeeded(File stlFile, StlBounds bounds,
                                             int bedX, int bedY, int bedZ) throws IOException {
        double sizeX = bounds.sizeX();
        double sizeY = bounds.sizeY();
        double sizeZ = bounds.sizeZ();

        double targetMinX = (bedX - sizeX) / 2.0;
        double targetMinY = (bedY - sizeY) / 2.0;
        double targetMinZ = 0.0;

        double offsetX = targetMinX - bounds.minX();
        double offsetY = targetMinY - bounds.minY();
        double offsetZ = targetMinZ - bounds.minZ();

        boolean needsShift = Math.abs(offsetX) > 1e-6 || Math.abs(offsetY) > 1e-6 || Math.abs(offsetZ) > 1e-6;
        if (!needsShift) {
            return new StlShiftResult(null, offsetX, offsetY, offsetZ, false);
        }

        Path shiftedPath = Files.createTempFile("stl_shifted_", ".stl");
        writeShifted(stlFile, shiftedPath.toFile(), offsetX, offsetY, offsetZ);
        return new StlShiftResult(shiftedPath, offsetX, offsetY, offsetZ, true);
    }

    private boolean isBinaryStl(File stlFile, long size) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(stlFile, "r")) {
            raf.seek(80);
            long triangleCount = readLEUInt32(raf);
            long expected = 84L + triangleCount * 50L;
            return expected == size;
        }
    }

    private StlBounds readBinaryBounds(File stlFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(stlFile, "r")) {
            raf.seek(80);
            long triangleCount = readLEUInt32(raf);
            raf.seek(84);

            BoundsAccumulator acc = new BoundsAccumulator();
            for (long i = 0; i < triangleCount; i++) {
                // skip normal
                readLEFloat(raf);
                readLEFloat(raf);
                readLEFloat(raf);
                // 3 vertices
                acc.accept(readLEFloat(raf), readLEFloat(raf), readLEFloat(raf));
                acc.accept(readLEFloat(raf), readLEFloat(raf), readLEFloat(raf));
                acc.accept(readLEFloat(raf), readLEFloat(raf), readLEFloat(raf));
                // skip attribute byte count
                raf.skipBytes(2);
            }
            return acc.toBounds();
        }
    }

    private StlBounds readAsciiBounds(File stlFile) throws IOException {
        BoundsAccumulator acc = new BoundsAccumulator();
        try (BufferedReader reader = Files.newBufferedReader(stlFile.toPath(), StandardCharsets.US_ASCII)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("vertex")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 4) continue;
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                acc.accept(x, y, z);
            }
        }
        return acc.toBounds();
    }

    private void writeShifted(File input, File output, double offsetX, double offsetY, double offsetZ) throws IOException {
        long size = input.length();
        if (size >= 84 && isBinaryStl(input, size)) {
            writeShiftedBinary(input, output, offsetX, offsetY, offsetZ);
        } else {
            writeShiftedAscii(input, output, offsetX, offsetY, offsetZ);
        }
    }

    private void writeShiftedAscii(File input, File output, double offsetX, double offsetY, double offsetZ) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(input.toPath(), StandardCharsets.US_ASCII);
             BufferedWriter writer = Files.newBufferedWriter(output.toPath(), StandardCharsets.US_ASCII)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("vertex")) {
                    writer.write(line);
                    writer.newLine();
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length < 4) {
                    writer.write(line);
                    writer.newLine();
                    continue;
                }
                double x = Double.parseDouble(parts[1]) + offsetX;
                double y = Double.parseDouble(parts[2]) + offsetY;
                double z = Double.parseDouble(parts[3]) + offsetZ;
                int idx = line.indexOf("vertex");
                String indent = idx > 0 ? line.substring(0, idx) : "";
                writer.write(indent + String.format(Locale.US, "vertex %.6f %.6f %.6f", x, y, z));
                writer.newLine();
            }
        }
    }

    private void writeShiftedBinary(File input, File output, double offsetX, double offsetY, double offsetZ) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(input, "r");
             OutputStream out = new FileOutputStream(output)) {
            byte[] header = new byte[80];
            raf.readFully(header);
            out.write(header);

            long triangleCount = readLEUInt32(raf);
            writeLEUInt32(out, triangleCount);

            for (long i = 0; i < triangleCount; i++) {
                // normal
                writeLEFloat(out, readLEFloat(raf));
                writeLEFloat(out, readLEFloat(raf));
                writeLEFloat(out, readLEFloat(raf));

                // vertices
                writeLEFloat(out, (float) (readLEFloat(raf) + offsetX));
                writeLEFloat(out, (float) (readLEFloat(raf) + offsetY));
                writeLEFloat(out, (float) (readLEFloat(raf) + offsetZ));

                writeLEFloat(out, (float) (readLEFloat(raf) + offsetX));
                writeLEFloat(out, (float) (readLEFloat(raf) + offsetY));
                writeLEFloat(out, (float) (readLEFloat(raf) + offsetZ));

                writeLEFloat(out, (float) (readLEFloat(raf) + offsetX));
                writeLEFloat(out, (float) (readLEFloat(raf) + offsetY));
                writeLEFloat(out, (float) (readLEFloat(raf) + offsetZ));

                // attribute byte count
                int b1 = raf.read();
                int b2 = raf.read();
                if ((b1 | b2) < 0) throw new IOException("Unexpected EOF while reading STL");
                out.write(b1);
                out.write(b2);
            }
        }
    }

    private long readLEUInt32(RandomAccessFile raf) throws IOException {
        int b1 = raf.read();
        int b2 = raf.read();
        int b3 = raf.read();
        int b4 = raf.read();
        if ((b1 | b2 | b3 | b4) < 0) throw new IOException("Unexpected EOF while reading STL");
        return ((long) b1 & 0xFF)
                | (((long) b2 & 0xFF) << 8)
                | (((long) b3 & 0xFF) << 16)
                | (((long) b4 & 0xFF) << 24);
    }

    private int readLEInt(RandomAccessFile raf) throws IOException {
        int b1 = raf.read();
        int b2 = raf.read();
        int b3 = raf.read();
        int b4 = raf.read();
        if ((b1 | b2 | b3 | b4) < 0) throw new IOException("Unexpected EOF while reading STL");
        return (b1 & 0xFF)
                | ((b2 & 0xFF) << 8)
                | ((b3 & 0xFF) << 16)
                | ((b4 & 0xFF) << 24);
    }

    private float readLEFloat(RandomAccessFile raf) throws IOException {
        return Float.intBitsToFloat(readLEInt(raf));
    }

    private void writeLEUInt32(OutputStream out, long value) throws IOException {
        out.write((int) (value & 0xFF));
        out.write((int) ((value >> 8) & 0xFF));
        out.write((int) ((value >> 16) & 0xFF));
        out.write((int) ((value >> 24) & 0xFF));
    }

    private void writeLEFloat(OutputStream out, float value) throws IOException {
        int bits = Float.floatToIntBits(value);
        out.write(bits & 0xFF);
        out.write((bits >> 8) & 0xFF);
        out.write((bits >> 16) & 0xFF);
        out.write((bits >> 24) & 0xFF);
    }

    private static class BoundsAccumulator {
        private boolean hasPoint = false;
        private double minX;
        private double minY;
        private double minZ;
        private double maxX;
        private double maxY;
        private double maxZ;

        void accept(double x, double y, double z) {
            if (!hasPoint) {
                minX = maxX = x;
                minY = maxY = y;
                minZ = maxZ = z;
                hasPoint = true;
                return;
            }
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        StlBounds toBounds() throws IOException {
            if (!hasPoint) {
                throw new IOException("STL appears to contain no vertices");
            }
            return new StlBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
