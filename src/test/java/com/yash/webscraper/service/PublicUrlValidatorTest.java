package com.yash.webscraper.service;

import com.yash.webscraper.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.*;

class PublicUrlValidatorTest {
    private final PublicUrlValidator validator = new PublicUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://example.com", "https://", "not a url",
            "https://user:password@example.com", "http://example.com:3000", "http://127.0.0.1",
            "http://10.0.0.1", "http://169.254.169.254", "http://[::1]", "http://[fc00::1]"})
    void rejectsInvalidAndPrivateUrls(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1",
            "169.254.169.254", "100.64.0.1", "224.0.0.1", "255.255.255.255", "::1", "fe80::1", "fd00::1", "2002:7f00:1::"})
    void rejectsNonPublicAddresses(String address) throws Exception {
        assertThat(PublicUrlValidator.isPublic(InetAddress.getByName(address))).isFalse();
    }

    @Test
    void acceptsPublicAddressesWithoutExternalDns() throws Exception {
        assertThat(PublicUrlValidator.isPublic(InetAddress.getByName("93.184.216.34"))).isTrue();
        assertThat(PublicUrlValidator.isPublic(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
        assertThat(validator.validate("https://93.184.216.34/article").getPath()).isEqualTo("/article");
    }
}
