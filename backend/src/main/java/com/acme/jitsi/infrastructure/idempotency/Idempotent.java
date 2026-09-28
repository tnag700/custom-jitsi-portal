package com.acme.jitsi.infrastructure.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks mutating HTTP endpoints that should use the infrastructure idempotency guard.
 * The effective key scope is built from the authenticated subject, HTTP method, server-observed request URI, and
 * the {@code Idempotency-Key} header value.
 * Reverse proxy rewrites, servlet path changes, or context path changes can therefore change the
 * effective scope for the same external request when the backend observes a different URI.
 * The database marker commits with the operation. Any repeated key (including a changed payload)
 * receives 409; responses and secrets are never cached. Only database effects participating in
 * the same transaction are covered. It does not perform cross-proxy URL canonicalization.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    Class<? extends Throwable>[] noRollbackFor() default {};
}
