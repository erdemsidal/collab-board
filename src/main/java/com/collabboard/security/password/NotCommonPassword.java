package com.collabboard.security.password;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Şifre en yaygın şifrelerden biri olamaz (CommonPasswords).
 *
 * Kayıt, şifre değişimi ve sıfırlamada kullanılır; üçü de aynı kuralı uygular,
 * yoksa biri diğerinin arka kapısı olurdu. Uzunluk kuralı ayrıca @Size'da.
 */
@Documented
@Constraint(validatedBy = NotCommonPassword.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotCommonPassword {

    String message() default "{validation.password.common}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<NotCommonPassword, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return !CommonPasswords.contains(value);
        }
    }
}
