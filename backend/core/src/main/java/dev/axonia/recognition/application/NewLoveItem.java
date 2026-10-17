package dev.axonia.recognition.application;

import dev.axonia.recognition.api.ContributionType;

public record NewLoveItem(ContributionType contributionType, Long contributionId, long newLoves) {
}
