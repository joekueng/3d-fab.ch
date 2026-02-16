package com.printcalculator.service;

import com.printcalculator.model.StlBounds;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Service
public class StlService {

    public StlBounds readBounds(File stlFile) throws IOException {
        long size = stlFile.length();
        if (size >= 84 && isBinaryStl(stlFile, size)) {
            return readBinaryBounds(stlFile);
        }
        return readAsciiBounds(stlFile);
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
