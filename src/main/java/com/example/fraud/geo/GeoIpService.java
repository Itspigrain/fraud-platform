package com.example.fraud.geo;

import com.maxmind.geoip2.DatabaseReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.InetAddress;
import java.util.Optional;

@Service
public class GeoIpService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);
    private final DatabaseReader reader;

    public GeoIpService(@Autowired(required = false) @Nullable DatabaseReader reader) {
        this.reader = reader;
    }

    public Optional<String> getCountry(String ip) {
        if (reader == null) return Optional.empty();
        try {
            var response = reader.country(InetAddress.getByName(ip));
            return Optional.ofNullable(response.getCountry().getName());
        } catch (Exception e) {
            log.debug("GeoIP lookup failed for {}: {}", ip, e.getMessage());
            return Optional.empty();
        }
    }

    @Configuration
    static class GeoIpConfig {
        private static final Logger log = LoggerFactory.getLogger(GeoIpConfig.class);

        @Bean
        DatabaseReader geoIpDatabaseReader(
                @Value("${fraud.geoip.database-path:}") String dbPath) {
            if (dbPath == null || dbPath.isEmpty()) {
                log.warn("No GeoIP database configured. GeoIP lookups will return empty.");
                return null;
            }
            try {
                return new DatabaseReader.Builder(new File(dbPath)).build();
            } catch (Exception e) {
                log.warn("Failed to load GeoIP database from {}: {}", dbPath, e.getMessage());
                return null;
            }
        }
    }
}
