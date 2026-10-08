package com.yash.webscraper.controller;

import com.yash.webscraper.model.SummaryRequest;
import com.yash.webscraper.model.SummaryResponse;
import com.yash.webscraper.service.ScraperService;
import com.yash.webscraper.service.SummaryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SummaryController {
    private final ScraperService scraper;
    private final SummaryService summaries;

    public SummaryController(ScraperService scraper, SummaryService summaries) {
        this.scraper = scraper;
        this.summaries = summaries;
    }

    @PostMapping("/summarize")
    public SummaryResponse summarize(@Valid @RequestBody SummaryRequest request) {
        summaries.requireApiKey();
        var page = scraper.scrape(request.url());
        return new SummaryResponse(page.url(), page.title(), summaries.summarize(page.text()), page.truncated());
    }
}
