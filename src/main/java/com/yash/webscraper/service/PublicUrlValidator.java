package com.yash.webscraper.service;

import com.yash.webscraper.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Component
public class PublicUrlValidator {
    public URI validate(String input) {
        URI uri;
        try {
            uri = URI.create(input.trim());
        } catch (IllegalArgumentException exception) {
            throw invalidUrl();
        }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)) {
            throw invalidUrl();
        }
        resolvePublic(uri.getHost());
        return uri;
    }

    // Used again by the HTTP client's DNS resolver so the connection itself uses checked addresses.
    public InetAddress[] resolvePublic(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (!isPublic(address)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Use a public website URL. Local and private addresses are not allowed.");
                }
            }
            return addresses;
        } catch (UnknownHostException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This website could not be found. Check the URL and try again.");
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 255;
        int second = bytes[1] & 255;
        if (bytes.length == 4) {
            return first != 0 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0)
                    && !(first == 198 && (second == 18 || second == 19));
        }
        // Allow global-unicast IPv6 only, excluding transition and documentation ranges.
        return (first & 0xe0) == 0x20
                && !(first == 0x20 && second == 0x02)
                && !(first == 0x20 && second == 0x01 && (bytes[2] & 255) < 2)
                && !(first == 0x20 && second == 0x01 && (bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8);
    }

    private ApiException invalidUrl() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid http:// or https:// URL using the standard web ports.");
    }
}
