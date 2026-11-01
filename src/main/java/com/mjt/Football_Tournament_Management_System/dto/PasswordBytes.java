package com.mjt.Football_Tournament_Management_System.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * A password bcrypt can hash: at most 72 bytes in UTF-8. bcrypt ignores everything after byte 72 (and Spring refuses
 * longer ones with an error), and the limit is in bytes, not characters: "ñ" or "€" take 2 or 3, so 50 ñ are 100 bytes.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordBytes.Validator.class)
public @interface PasswordBytes {
    int MAX = 72;

    String message() default "la contraseña es demasiado larga (hasta 72 letras sin acentos, menos con ñ o tildes)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<PasswordBytes, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return fits(value);
        }

        public static boolean fits(String password) {
            return password == null || password.getBytes(StandardCharsets.UTF_8).length <= MAX;
        }
    }
}
