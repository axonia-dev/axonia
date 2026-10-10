package dev.axonia.privacy.adapter.in.web;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import dev.axonia.privacy.application.DataExport;
import dev.axonia.privacy.application.ExportPackageNames;
import dev.axonia.privacy.domain.ExportPackageName;
import dev.axonia.shared.api.personaldata.ExportScope;
import dev.axonia.shared.api.ratelimit.RateLimiter;
import dev.axonia.shared.api.ratelimit.RateLimiters;

/**
 * Streams one export ZIP onto an HTTP response: rate limit, then headers, then the write itself. Shared by both
 * surfaces this module offers - {@link PrivacyController}'s self-serve member export and
 * {@link OrganizationExportController}'s enterprise-wide one - so the shape of a download response exists exactly once,
 * and each controller is left to decide only who may call it and which {@link ExportScope} applies.
 *
 * <p>
 * Rate limited per caller rather than per Organization: an export reads the entire Organization, so it should not be
 * something a loop can hold the database down with, the same concern
 * {@link dev.axonia.shared.api.ratelimit.RateLimiter} already backs agent tokens for.
 */
@Component
class ExportDownload {

    private static final String CONTENT_TYPE = "application/zip";
    private static final int EXPORTS_PER_MINUTE = 5;

    private final DataExport dataExport;
    private final Clock clock;
    private final ExportPackageNames packageNames;
    private final RateLimiter rateLimiter;

    ExportDownload(DataExport dataExport, Clock clock, ExportPackageNames packageNames, RateLimiters rateLimiters) {

        this.dataExport = dataExport;
        this.clock = clock;
        this.packageNames = packageNames;
        this.rateLimiter = rateLimiters.create("data-export", EXPORTS_PER_MINUTE, Duration.ofMinutes(1));
    }

    /**
     * Writes {@code scope}'s export package to {@code response}, keyed for rate-limiting by {@code rateLimitKey} (the
     * caller's own subject - never a target's, since a self-serve export and an admin export must not share one
     * caller's budget with an unrelated one).
     */
    void writeTo(ExportScope scope, String rateLimitKey, HttpServletResponse response) throws IOException {

        var attempt = rateLimiter.tryConsume(rateLimitKey);
        if (!attempt.allowed()) {
            throw new ExportRateLimitedException(attempt);
        }
        response.setContentType(CONTENT_TYPE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename(scope) + "\"");
        dataExport.writeTo(scope, response.getOutputStream());
    }

    /**
     * The Organization's label where it has one and its name where it does not, reduced to a safe character set by
     * {@link ExportPackageName} before it is interpolated into the header. The same helper names the archive's single
     * root folder through {@link ExportPackageNames}, so the {@code .zip} and the directory it unpacks into always
     * agree.
     */
    private String filename(ExportScope scope) {

        var date = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return packageNames.of(scope).root(date) + ".zip";
    }
}
