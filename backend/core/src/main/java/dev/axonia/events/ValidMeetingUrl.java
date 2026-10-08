package dev.axonia.events;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Refuses any join link that is not an {@code http} or {@code https} address. The link is stored as free text and
 * handed on verbatim: the join control renders it as an {@code href} and the calendar feed emits it as an ICS
 * {@code CONFERENCE} URI that Outlook and Google make clickable. A stored {@code javascript:} value is therefore stored
 * cross-site scripting that fires the moment a member follows the link, and a {@code data:} or {@code file:} one is the
 * same class of trap. Refusing it here rather than in the browser is deliberate: this is the one place every consumer
 * passes through, and a feed subscriber never runs the React validation at all.
 *
 * <p>
 * The scheme is matched at the very start of the raw value rather than parsed out, so nothing reaches an allowed scheme
 * past leading whitespace, a control character or an embedded newline - all of which some clients strip before
 * following a link. A relative or scheme-less address is refused too: a link somebody else has to resolve against an
 * unknown base is not a way in. A rejected value is a 400 from bean validation, never a silent strip, because quietly
 * storing something other than what was sent is how an organizer ends up publishing a link nobody can follow.
 *
 * <p>
 * A control character anywhere in the value is refused as well, and that half is about the 2040 characters the scheme
 * anchor says nothing about. The address is written into the public ICS feed as a {@code CONFERENCE} URI, and RFC 5545
 * separates properties with a CRLF, so a stored {@code https://x.example/a\r\nDESCRIPTION:...} is an injected calendar
 * property in a document nobody authenticates to read. The renderer strips control characters too, deliberately: this
 * end keeps such a value out of the database, that end keeps one that somehow got in from breaking the document.
 */
@Documented
@Constraint(validatedBy = ValidMeetingUrl.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMeetingUrl {

    String message() default "must be an http or https address";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidMeetingUrl, String> {

        private static final Pattern HTTP_SCHEME = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
        private static final Pattern CONTROL_CHARACTER = Pattern.compile("\\p{Cntrl}");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {

            if (value == null || value.isBlank()) {
                return true;
            }
            return HTTP_SCHEME.matcher(value).find() && !CONTROL_CHARACTER.matcher(value).find();
        }
    }
}
