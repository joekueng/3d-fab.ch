package com.printcalculator.service.qr;

import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class GeoLite2CityService {

    private static final Logger logger = LoggerFactory.getLogger(GeoLite2CityService.class);

    private final boolean geoEnabled;
    private final Path databasePath;
    private final List<String> locales;
    private final Object readerLock = new Object();
    private final AtomicBoolean missingDatabaseLogged = new AtomicBoolean(false);

    private volatile DatabaseReader databaseReader;

    public GeoLite2CityService(
            @Value("${app.qr.geo.enabled:false}") boolean geoEnabled,
            @Value("${app.qr.geo.db-path:/app/cache/geoip/GeoLite2-City.mmdb}") String databasePath,
            @Value("${app.qr.geo.locales:it,en}") String localesCsv
    ) {
        this.geoEnabled = geoEnabled;
        this.databasePath = Path.of(databasePath);
        this.locales = parseLocales(localesCsv);
    }

    public Optional<GeoLocation> lookup(String clientIp) {
        if (!geoEnabled) {
            return Optional.empty();
        }

        String normalizedIp = IpAddressUtils.normalizeIp(clientIp);
        if (normalizedIp == null || !IpAddressUtils.isPublicIp(normalizedIp)) {
            return Optional.empty();
        }

        DatabaseReader reader = getReader();
        if (reader == null) {
            return Optional.empty();
        }

        try {
            InetAddress address = InetAddress.getByName(normalizedIp);
            return reader.tryCity(address).map(this::toGeoLocation);
        } catch (IOException | GeoIp2Exception ex) {
            logger.warn("GeoLite2 lookup failed for QR scan IP {}", normalizedIp, ex);
            return Optional.empty();
        }
    }

    @PreDestroy
    void closeReader() {
        DatabaseReader reader = databaseReader;
        if (reader == null) {
            return;
        }

        try {
            reader.close();
        } catch (IOException ex) {
            logger.debug("Unable to close GeoLite2 database reader cleanly", ex);
        }
    }

    private DatabaseReader getReader() {
        DatabaseReader current = databaseReader;
        if (current != null) {
            return current;
        }

        synchronized (readerLock) {
            if (databaseReader != null) {
                return databaseReader;
            }
            if (!Files.isReadable(databasePath)) {
                if (missingDatabaseLogged.compareAndSet(false, true)) {
                    logger.warn("GeoLite2 database not available at {}. QR geo enrichment will be skipped.", databasePath);
                }
                return null;
            }

            try {
                databaseReader = new DatabaseReader.Builder(databasePath.toFile())
                        .fileMode(Reader.FileMode.MEMORY_MAPPED)
                        .locales(locales)
                        .build();
                logger.info("Loaded GeoLite2 City database from {}", databasePath);
                return databaseReader;
            } catch (IOException ex) {
                logger.warn("Unable to load GeoLite2 database from {}", databasePath, ex);
                return null;
            }
        }
    }

    private GeoLocation toGeoLocation(CityResponse response) {
        String countryCode = emptyToNull(response.country().isoCode());
        String countryName = emptyToNull(response.country().name());
        String cityName = emptyToNull(response.city().name());
        return new GeoLocation(countryCode, countryName, cityName);
    }

    private List<String> parseLocales(String localesCsv) {
        return Arrays.stream(String.valueOf(localesCsv == null ? "" : localesCsv).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public record GeoLocation(String countryCode, String countryName, String cityName) {
    }
}
