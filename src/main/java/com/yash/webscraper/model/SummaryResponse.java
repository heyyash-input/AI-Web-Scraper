package com.yash.webscraper.model;

public record SummaryResponse(String url, String title, String summary, boolean truncated) {
}
