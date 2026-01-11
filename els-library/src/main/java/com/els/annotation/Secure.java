package com.els.annotation;

import com.els.domain.Action;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Secure {
    Action action() default Action.SELECT;

    Class<?> entity(); // The entity class being accessed, e.g. Product.class
}
