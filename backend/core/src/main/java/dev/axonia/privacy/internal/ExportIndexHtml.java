package dev.axonia.privacy.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.util.HtmlUtils;

import dev.axonia.ExportFragment;
import lombok.experimental.UtilityClass;

/**
 * A self-contained, static rendering of an export package: inline CSS, no external stylesheet, script or font, so it
 * opens directly in a browser with no toolchain and no network access. Every value it renders came out of a row an
 * {@link dev.axonia.PersonalDataExporter} read - member-authored free text such as artifact bodies, comments and
 * community descriptions live in there - so every value is HTML-escaped before it reaches the page; rendering it raw
 * would turn a person's own downloaded export into a stored-XSS vector against themselves. The {@link ExportHeader}
 * names people rather than identifying them, and a display name and an address are member-authored too, so they are
 * escaped on exactly the same footing as a row value.
 */
@UtilityClass
class ExportIndexHtml {

    private static final String STYLE = "body{font-family:sans-serif;margin:2rem;color:#222;}"
            + "table{border-collapse:collapse;margin-bottom:1.5rem;width:100%;}"
            + "td{border:1px solid #ccc;padding:0.25rem 0.5rem;vertical-align:top;}"
            + "h2{margin-top:2rem;border-bottom:1px solid #ccc;padding-bottom:0.25rem;}"
            + ".empty{color:#666;font-style:italic;}";

    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    String render(ExportHeader header, List<ExportFragment> fragments) {

        var html = new StringBuilder();
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Data export</title>");
        html.append("<style>").append(STYLE).append("</style></head><body>");
        html.append("<h1>Data export</h1>");
        renderSummary(html, header);
        fragments.forEach(fragment -> renderSection(html, fragment));
        html.append("</body></html>");
        return html.toString();
    }

    private void renderSummary(StringBuilder html, ExportHeader header) {

        if (header.organization() != null) {
            html.append("<p>Organization: ").append(escape(header.organization())).append("</p>");
        }
        if (header.member() != null) {
            html.append("<p>Member: ").append(escape(header.member() + addressOf(header))).append("</p>");
        }
        html.append("<p>Generated at: ").append(escape(formattedUtc(header.generatedAt()))).append("</p>");
    }

    private String addressOf(ExportHeader header) {

        return Optional.ofNullable(header.memberEmail()).map(" <%s>"::formatted).orElse("");
    }

    private void renderSection(StringBuilder html, ExportFragment fragment) {

        html.append("<section><h2>").append(escape(fragment.fileName())).append("</h2>");
        fragment.payload().forEach((table, rows) -> renderTable(html, fragment, table, rows));
        html.append("</section>");
    }

    private void renderTable(StringBuilder html, ExportFragment fragment, String table, Object rows) {

        html.append("<h3>").append(escape(table)).append("</h3>");
        var rowList = asRows(fragment, table, rows);
        if (rowList.isEmpty()) {
            html.append("<p class=\"empty\">No rows.</p>");
            return;
        }
        html.append("<table><tbody>");
        rowList.forEach(row -> renderRow(html, fragment, table, row));
        html.append("</tbody></table>");
    }

    /**
     * A row can be left with no cells at all once the subject columns have been withheld: {@code expertise_opt_out}
     * holds nothing else. The row existing is itself the fact being exported, so it renders as a sentence rather than
     * as an empty {@code
     *
    <tr>
     * } a reader would take for a rendering fault.
     */
    private void renderRow(StringBuilder html, ExportFragment fragment, String table, Object row) {

        html.append("<tr>");
        var cells = asRow(fragment, table, row);
        if (cells.isEmpty()) {
            html.append("<td class=\"empty\">Recorded.</td>");
        }
        cells.forEach((column, value) -> html.append("<td>").append(escape(String.valueOf(column))).append(": ")
                .append(escape(cellValueOf(value))).append("</td>"));
        html.append("</tr>");
    }

    /** A raw driver {@link Timestamp} prints in the JVM's own zone with no marker unless normalised to UTC here. */
    private String cellValueOf(Object value) {

        return value instanceof Timestamp timestamp ? formattedUtc(timestamp.toInstant()) : String.valueOf(value);
    }

    private String formattedUtc(Instant instant) {

        return UTC_FORMAT.format(instant);
    }

    /**
     * Every current {@link dev.axonia.PersonalDataExporter} shapes its payload as table name -> list of row maps, but
     * nothing in the type system enforces that for a fragment written next year. A bare {@code ClassCastException}
     * while rendering somebody's real export would name no module and no key, which is a miserable start to diagnosing
     * it; naming both here costs nothing in the common case.
     */
    private List<?> asRows(ExportFragment fragment, String table, Object rows) {

        if (rows instanceof List<?> list) {
            return list;
        }
        throw new IllegalStateException("Fragment '%s' key '%s' is not a list of rows: %s"
                .formatted(fragment.fileName(), table, describe(rows)));
    }

    private Map<?, ?> asRow(ExportFragment fragment, String table, Object row) {

        if (row instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalStateException("Fragment '%s' key '%s' contains a row that is not a map: %s"
                .formatted(fragment.fileName(), table, describe(row)));
    }

    private String describe(Object value) {

        return value == null ? "null" : value.getClass().getName();
    }

    private String escape(String value) {

        return HtmlUtils.htmlEscape(value);
    }
}
