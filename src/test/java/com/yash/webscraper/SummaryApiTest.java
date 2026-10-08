package com.yash.webscraper;

import com.sun.net.httpserver.HttpServer;
import com.yash.webscraper.model.ScrapedPage;
import com.yash.webscraper.service.ScraperService;
import com.yash.webscraper.service.SummaryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "groq.api-key=test-key")
@AutoConfigureMockMvc
class SummaryApiTest {
    static final HttpServer provider;
    static final AtomicInteger providerStatus = new AtomicInteger(200);
    static final AtomicReference<String> providerBody = new AtomicReference<>();
    static final AtomicReference<String> requestBody = new AtomicReference<>();
    static final AtomicReference<String> authorization = new AtomicReference<>();

    static {
        try {
            provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            provider.createContext("/chat/completions", exchange -> {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] bytes = providerBody.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(providerStatus.get(), bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            provider.start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void configureProvider(DynamicPropertyRegistry registry) {
        registry.add("groq.base-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort());
    }

    @Autowired MockMvc mvc;
    @MockitoBean ScraperService scraper;

    @BeforeEach
    void resetProvider() {
        providerStatus.set(200);
        providerBody.set("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"A concise summary of the article.\"}}]}");
        when(scraper.scrape("https://example.com/article"))
                .thenReturn(new ScrapedPage("https://example.com/article", "Article title", "Article text to summarize", false));
    }

    @AfterAll
    static void stopProvider() { provider.stop(0); }

    @Test
    void returnsSummaryThroughRealHttpProviderAdapter() throws Exception {
        mvc.perform(post("/api/summarize").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/article\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Article title"))
                .andExpect(jsonPath("$.summary").value("A concise summary of the article."))
                .andExpect(jsonPath("$.truncated").value(false));
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(requestBody.get()).contains("Article text to summarize", "messages", "max_completion_tokens");
    }

    @Test
    void rejectsBlankUrlBeforeCallingScraper() throws Exception {
        mvc.perform(post("/api/summarize").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Please enter a webpage URL."));
        verifyNoInteractions(scraper);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/summarize").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void handlesProviderRateLimitWithoutLeakingProviderBody() throws Exception {
        providerStatus.set(429);
        providerBody.set("{\"error\":\"private provider details\"}");
        mvc.perform(post("/api/summarize").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/article\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("The AI free-tier limit was reached. Wait a moment and try again."));
    }

    @Test
    void handlesInvalidApiKey() throws Exception {
        providerStatus.set(401);
        mvc.perform(post("/api/summarize").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/article\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void handlesEmptyAiResponse() throws Exception {
        providerBody.set("{\"choices\":[]}");
        mvc.perform(post("/api/summarize").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/article\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void reportsMissingConfigurationWithoutMakingNetworkRequest() {
        var service = new SummaryService(RestClient.builder(), "", "test-model", "http://127.0.0.1:1");
        assertThatThrownBy(() -> service.summarize("text")).hasMessageContaining("GROQ_API_KEY");
    }

    @Test
    void servesFrontendFromSameApplication() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/styles.css")).andExpect(status().isOk());
    }
}
