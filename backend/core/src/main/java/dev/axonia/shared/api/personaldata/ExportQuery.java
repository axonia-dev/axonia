package dev.axonia.shared.api.personaldata;

import java.util.Map;

/** A named-parameter {@code SELECT} built by {@link ExportQueries}, for the exporter's persistence adapter to run. */
public record ExportQuery(String sql, Map<String, Object> params) {
}
