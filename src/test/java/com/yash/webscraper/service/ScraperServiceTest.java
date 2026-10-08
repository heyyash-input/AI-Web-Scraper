package com.yash.webscraper.service;

import com.yash.webscraper.exception.ApiException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScraperServiceTest {
    private final CloseableHttpClient client = mock(CloseableHttpClient.class);
    private final PublicUrlValidator validator = mock(PublicUrlValidator.class);
    private final ScraperService scraper = new ScraperService(client, validator);
    private final String article = "Spring Boot serves the frontend and a small REST API from the same application. "
            + "Jsoup extracts the main content before it is passed to an AI model for a short summary.";

    @Test
    void extractsMainContentWithoutNavigationScriptsAndFooter() {
        var page = scraper.extract(Jsoup.parse("<title>Article title</title><nav>Menu noise</nav>"
                + "<main><h1>Article</h1><p>" + article + "</p><script>tracking()</script></main>"
                + "<footer>Footer noise</footer>"), "https://example.com");
        assertThat(page.title()).isEqualTo("Article title");
        assertThat(page.text()).contains(article).doesNotContain("Menu noise", "Footer noise", "tracking");
        assertThat(page.truncated()).isFalse();
    }

    @Test
    void fallsBackToBodyAndHostname() {
        var page = scraper.extract(Jsoup.parse("<p>" + article + "</p>"), "https://example.com");
        assertThat(page.title()).isEqualTo("example.com");
        assertThat(page.text()).isEqualTo(article);
    }

    @Test
    void limitsLongPagesAndReportsTruncation() {
        var page = scraper.extract(Jsoup.parse("<article>" + article.repeat(200) + "</article>"), "https://example.com");
        assertThat(page.text()).hasSize(ScraperService.MAX_TEXT_LENGTH);
        assertThat(page.truncated()).isTrue();
    }

    @Test
    void rejectsEmptyOrJavascriptOnlyPages() {
        assertThatThrownBy(() -> scraper.extract(Jsoup.parse("<script>loadApp()</script>"), "https://example.com"))
                .isInstanceOf(ApiException.class).hasMessageContaining("Not enough readable text");
    }

    @Test
    void fetchesAndParsesHtmlResponse() throws Exception {
        when(validator.validate("https://example.com")).thenReturn(URI.create("https://example.com"));
        var response = new BasicClassicHttpResponse(200);
        response.setEntity(new StringEntity("<main>" + article + "</main>", ContentType.TEXT_HTML));
        respondWith(response);
        assertThat(scraper.scrape("https://example.com").text()).isEqualTo(article);
    }

    @Test
    void validatesRedirectBeforeFollowingIt() throws Exception {
        when(validator.validate("https://example.com")).thenReturn(URI.create("https://example.com"));
        when(validator.validate("http://127.0.0.1/admin")).thenThrow(new ApiException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Private address blocked"));
        var response = new BasicClassicHttpResponse(302);
        response.addHeader("Location", "http://127.0.0.1/admin");
        respondWith(response);
        assertThatThrownBy(() -> scraper.scrape("https://example.com")).hasMessage("Private address blocked");
        verify(client, times(1)).execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
    }

    @Test
    void rejectsNonHtmlResponse() throws Exception {
        when(validator.validate("https://example.com")).thenReturn(URI.create("https://example.com"));
        var response = new BasicClassicHttpResponse(200);
        response.setEntity(new StringEntity("PDF content", ContentType.APPLICATION_PDF));
        respondWith(response);
        assertThatThrownBy(() -> scraper.scrape("https://example.com")).hasMessageContaining("HTML webpage");
    }

    @Test
    void rejectsOversizedResponse() throws Exception {
        when(validator.validate("https://example.com")).thenReturn(URI.create("https://example.com"));
        var response = new BasicClassicHttpResponse(200);
        response.setEntity(new StringEntity("x".repeat(2 * 1024 * 1024 + 1), ContentType.TEXT_HTML));
        respondWith(response);
        assertThatThrownBy(() -> scraper.scrape("https://example.com")).hasMessageContaining("too large");
    }

    private void respondWith(BasicClassicHttpResponse response) throws Exception {
        when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(call -> {
                    HttpClientResponseHandler<?> handler = call.getArgument(1);
                    return handler.handleResponse(response);
                });
    }
}
