package com.example.fraud.geo;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoIpServiceTest {

    @Mock
    private DatabaseReader databaseReader;

    @Test
    void getCountryReturnsCountryName() throws Exception {
        var geoIpService = new GeoIpService(databaseReader);
        var response = mock(CountryResponse.class);
        var country = mock(Country.class);
        when(country.getName()).thenReturn("Australia");
        when(response.getCountry()).thenReturn(country);
        when(databaseReader.country(InetAddress.getByName("1.2.3.4"))).thenReturn(response);

        Optional<String> result = geoIpService.getCountry("1.2.3.4");

        assertThat(result).contains("Australia");
    }

    @Test
    void getCountryReturnsEmptyOnException() throws Exception {
        var geoIpService = new GeoIpService(databaseReader);
        when(databaseReader.country(InetAddress.getByName("9.9.9.9")))
            .thenThrow(new RuntimeException("lookup failed"));

        Optional<String> result = geoIpService.getCountry("9.9.9.9");

        assertThat(result).isEmpty();
    }

    @Test
    void getCountryReturnsEmptyWhenNullReader() {
        var geoIpService = new GeoIpService(null);

        Optional<String> result = geoIpService.getCountry("1.2.3.4");

        assertThat(result).isEmpty();
    }
}
