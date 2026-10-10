package dev.axonia.shared.api.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.ZoneId;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Refuses anything {@link ZoneId#of} cannot resolve. The zone is stored as free text and read back by {@code ZoneId.of}
 * on every projection, so an unresolvable value would not fail here but would throw on the next schedule read of a
 * series nobody could then repair through the API.
 */
@Documented
@Constraint(validatedBy = ValidTimeZone.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimeZone {

    String message() default "must be a known IANA time zone";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidTimeZone, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {

            if (value == null || value.isBlank()) {
                return true;
            }
            try {
                ZoneId.of(value);
                return true;
            } catch (java.time.DateTimeException e) {
                return false;
            }
        }
    }
}
