package com.yash.webscraper.service;

import com.yash.webscraper.exception.ApiException;
import com.yash.webscraper.model.ScrapedPage;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Comparator;
import java.util.Locale;

@Service
public class ScraperService {
    private static final int MAX_PAGE_BYTES = 2 * 1024 * 1024;
    static final int MAX_TEXT_LENGTH = 12_000;
    private final CloseableHttpClient client;
    private final PublicUrlValidator validator;

    public ScraperService(CloseableHttpClient client, PublicUrlValidator validator) {
        this.client = client;
        this.validator = validator;
    }

    public ScrapedPage scrape(String url) {
        URI current = validator.validate(url);
        try {
            for (int redirects = 0; redirects <= 5; redirects++) {
                String pageUrl = current.toString();
                HttpGet request = new HttpGet(current);
                request.setHeader("User-Agent", "WebScraper/1.0");
                request.setHeader("Accept", "text/html,application/xhtml+xml");
                FetchedPage fetched = client.execute(request, response -> {
                    int status = response.getCode();
                    if (status >= 300 && status < 400 && response.getFirstHeader("Location") != null) {
                        return new FetchedPage(response.getFirstHeader("Location").getValue(), null);
                    }
                    if (status < 200 || status >= 300) {
                        throw new ApiException(HttpStatus.BAD_GATEWAY,
                                "The website returned HTTP " + status + ". Try another public page.");
                    }
                    var entity = response.getEntity();
                    String type = entity == null || entity.getContentType() == null ? "" : entity.getContentType();
                    type = type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                    if (!type.equals("text/html") && !type.equals("application/xhtml+xml")) {
                        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "This URL does not contain an HTML webpage.");
                    }
                    try (var body = entity.getContent()) {
                        byte[] bytes = body.readNBytes(MAX_PAGE_BYTES + 1);
                        if (bytes.length > MAX_PAGE_BYTES) {
                            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "This page is too large. Try a smaller article.");
                        }
                        var charset = org.apache.hc.core5.http.ContentType.parse(entity.getContentType()).getCharset();
                        return new FetchedPage(null, Jsoup.parse(new ByteArrayInputStream(bytes),
                                charset == null ? null : charset.name(), pageUrl));
                    }
                });
                if (fetched.redirect() == null) {
                    return extract(fetched.document(), current.toString());
                }
                current = validator.validate(current.resolve(fetched.redirect()).toString());
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "This website redirects too many times. Try its final page URL.");
        } catch (SocketTimeoutException exception) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "The website took too long to respond. Please try again.");
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not read this website. It may be unavailable or blocking automated access.");
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The website returned an invalid response or redirect.");
        }
    }

    ScrapedPage extract(Document document, String url) {
        String title = document.title().isBlank() ? URI.create(url).getHost() : document.title();
        document.select("script, style, noscript, nav, footer, aside, form, svg, iframe, [hidden], [aria-hidden=true]").remove();
        Element content = document.select("article, main, [role=main]").stream()
                .max(Comparator.comparingInt(element -> element.text().length()))
                .orElse(document.body());
        String text = content.text().replaceAll("\\s+", " ").trim();
        if (text.length() < 80) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Not enough readable text was found. Try a public article that does not require JavaScript or a login.");
        }
        boolean truncated = text.length() > MAX_TEXT_LENGTH;
        if (truncated) {
            int end = MAX_TEXT_LENGTH;
            if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
            text = text.substring(0, end);
        }
        return new ScrapedPage(url, title, text, truncated);
    }

    private record FetchedPage(String redirect, Document document) {
    }
}
