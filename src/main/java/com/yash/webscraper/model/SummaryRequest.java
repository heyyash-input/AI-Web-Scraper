package com.yash.webscraper.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SummaryRequest(
        @NotBlank(message = "Please enter a webpage URL.")
        @Size(max = 2048, message = "The URL must be less than 2,048 characters.")
        String url) {
}
