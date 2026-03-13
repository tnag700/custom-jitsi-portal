package com.acme.jitsi.infrastructure.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks mutating HTTP endpoints that should use the infrastructure idempotency guard.
 * The effective key scope is built from the HTTP method, the server-observed request URI, and
 * the {@code Idempotency-Key} header value.
 * Reverse proxy rewrites, servlet path changes, or context path changes can therefore change the
 * effective scope for the same external request when the backend observes a different URI.
 * The current implementation is lock-only: it does not perform cross-proxy URL canonicalization
 * and it does not replay a previously completed HTTP response.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
