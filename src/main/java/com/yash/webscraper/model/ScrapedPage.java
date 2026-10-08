package com.yash.webscraper.model;

public record ScrapedPage(String url, String title, String text, boolean truncated) {
}
