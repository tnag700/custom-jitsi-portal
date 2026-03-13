package com.acme.jitsi.infrastructure.idempotency;

import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.shared.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdempotencyExceptionHandler {

    private static final String IDEMPOTENCY_CONFLICT_DETAIL =
            "Request with the same Idempotency-Key is already being processed or has already been processed.";

    private final ProblemResponseFacade problemResponseFacade;

    public IdempotencyExceptionHandler(ProblemResponseFacade problemResponseFacade) {
        this.problemResponseFacade = problemResponseFacade;
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflictException(IdempotencyConflictException ex, HttpServletRequest request) {
        return problemResponseFacade.buildProblemDetail(
                request,
                HttpStatus.CONFLICT,
                "Idempotency Conflict",
                IDEMPOTENCY_CONFLICT_DETAIL,
                ErrorCode.IDEMPOTENCY_CONFLICT.code()
        );
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ProblemDetail handleInvalidIdempotencyKeyException(InvalidIdempotencyKeyException ex, HttpServletRequest request) {
        return problemResponseFacade.buildProblemDetail(
                request,
                HttpStatus.BAD_REQUEST,
                "Invalid Idempotency Key",
                ex.getMessage(),
                ErrorCode.IDEMPOTENCY_KEY_INVALID.code()
        );
    }
}
