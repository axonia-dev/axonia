package dev.axonia.communication.internal;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.unbescape.html.HtmlEscape;

import lombok.RequiredArgsConstructor;

/**
 * Turns an opaque template name plus a map of already-resolved facts into a subject line, an HTML part and a plain-text
 * part. This class knows nothing about sessions, communities or any other bounded context - {@code
 * template} is just a file basename under {@code templates/mail/} and {@code facts} is just a bag of values a Thymeleaf
 * expression can read. Whoever calls {@link #render} owns what the keys mean.
 *
 * <p>
 * Each template is one HTML file and one TXT file sharing a basename, so the two parts of a message and its subject
 * never drift apart in separate files. The subject lives inside the HTML file as a {@code th:block th:fragment="
 * subject"}, extracted with a fragment selector rather than duplicated into a third file or a Java constant.
 *
 * <p>
 * A missing or unresolvable template name is not caught here: Thymeleaf throws {@code
 * org.thymeleaf.exceptions.TemplateInputException}, which propagates unhandled. That is deliberate - {@code
 * MailRenderer} does not know whether a bad template name should fail one outbox row or the whole drain tick, so it
 * fails loud and leaves that call to whoever calls it.
 */
@Component
@RequiredArgsConstructor
class MailRenderer {

    private static final String SUBJECT_FRAGMENT = "subject";
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\n{3,}");

    private final TemplateEngine mailTemplateEngine;

    /**
     * {@code scheduledAtLabel} is derived here rather than by each caller, and here rather than in each template: it is
     * the one fact whose value depends on another fact the caller adds ({@code readerTimeZone}), and every mail render
     * in the process goes through this method. Deriving it at the single choke point is what makes a preview and a send
     * agree about a member's hour by construction instead of by two call sites staying in step.
     */
    RenderedMail render(String template, Map<String, Object> facts) {

        var context = new Context();
        context.setVariables(facts);
        context.setVariable(MomentLine.LABEL_FACT, MomentLine.full(facts));

        // Two passes over the same .html file: the fragment selector below extracts only the subject th:block, and
        // the unqualified process() call further down renders the whole document. Thymeleaf caches each combination
        // of template name and fragment selector as a distinct parsed-template-cache entry, so this costs a second
        // cache slot, not a second parse per render call.
        var rawSubject = mailTemplateEngine.process(template + ".html", Set.of(SUBJECT_FRAGMENT), context).strip();
        // th:text escapes for the HTML sink the subject block sits in, but the extracted string becomes a mail
        // header, not markup - unescape here so "Q&A" round-trips as "Q&A", not "Q&amp;A", in the inbox.
        var subject = HtmlEscape.unescapeHtml(rawSubject);
        var html = mailTemplateEngine.process(template + ".html", context);
        var text = normalizeBlankLines(mailTemplateEngine.process(template + ".txt", context));

        return new RenderedMail(subject, html, text);
    }

    /**
     * A TEXT-mode template's optional sections each carry their own line boundary, so any run of them being absent at
     * once - a realistic case, not a corner one, since most of a message's facts are optional - leaves behind a run of
     * blank lines from the template source rather than a clean single paragraph break. Collapsing here, once, in one
     * place, is more robust than hand-tuning every template's whitespace against every combination of present and
     * absent facts: it holds even for a template nobody has re-balanced yet.
     */
    private static String normalizeBlankLines(String text) {

        return EXCESS_BLANK_LINES.matcher(text).replaceAll("\n\n").strip();
    }
}
