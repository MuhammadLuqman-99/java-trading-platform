package com.tradingplatform.tradingapi.config;

import java.net.URI;
import com.tradingplatform.domain.orders.OrderDomainException;
import com.tradingplatform.domain.wallet.InsufficientBalanceException;
import com.tradingplatform.domain.wallet.WalletDomainException;
import com.tradingplatform.tradingapi.risk.RiskViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private static final String TYPE_PREFIX = "/problems/";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    problem.setType(URI.create(TYPE_PREFIX + "validation-error"));
    problem.setTitle("Validation Error");
    problem.setProperty(
        "errors",
        ex.getFieldErrors().stream()
            .map(
                fe ->
                    new FieldError(
                        fe.getField(),
                        fe.getDefaultMessage(),
                        String.valueOf(fe.getRejectedValue())))
            .toList());
    return problem;
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(URI.create(TYPE_PREFIX + "missing-parameter"));
    problem.setTitle("Missing Parameter");
    return problem;
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String detail =
        String.format(
            "Parameter '%s' should be of type '%s'",
            ex.getName(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    problem.setType(URI.create(TYPE_PREFIX + "type-mismatch"));
    problem.setTitle("Type Mismatch");
    return problem;
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage());
    problem.setType(URI.create(TYPE_PREFIX + "method-not-allowed"));
    problem.setTitle("Method Not Allowed");
    return problem;
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail handleNotFound(NoResourceFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setType(URI.create(TYPE_PREFIX + "not-found"));
    problem.setTitle("Not Found");
    return problem;
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "You do not have permission to access this resource");
    problem.setType(URI.create(TYPE_PREFIX + "access-denied"));
    problem.setTitle("Access Denied");
    return problem;
  }

  @ExceptionHandler({
    AuthenticationException.class,
    AuthenticationCredentialsNotFoundException.class
  })
  public ProblemDetail handleAuthentication(Exception ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource");
    problem.setType(URI.create(TYPE_PREFIX + "unauthorized"));
    problem.setTitle("Unauthorized");
    return problem;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(URI.create(TYPE_PREFIX + "invalid-argument"));
    problem.setTitle("Invalid Argument");
    return problem;
  }

  @ExceptionHandler(OrderDomainException.class)
  public ProblemDetail handleOrderDomain(OrderDomainException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(URI.create(TYPE_PREFIX + "order-domain-error"));
    problem.setTitle("Order Validation Error");
    return problem;
  }

  @ExceptionHandler(WalletDomainException.class)
  public ProblemDetail handleWalletDomain(WalletDomainException ex) {
    HttpStatus status =
        (ex instanceof InsufficientBalanceException) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    problem.setType(URI.create(TYPE_PREFIX + "wallet-error"));
    problem.setTitle("Wallet Error");
    return problem;
  }

  @ExceptionHandler(RiskViolationException.class)
  public ProblemDetail handleRiskViolation(RiskViolationException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problem.setType(URI.create(TYPE_PREFIX + "risk-violation"));
    problem.setTitle("Risk Violation");
    problem.setProperty("code", ex.code());
    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later.");
    problem.setType(URI.create(TYPE_PREFIX + "internal-error"));
    problem.setTitle("Internal Server Error");
    return problem;
  }

  private record FieldError(String field, String message, String rejectedValue) {}
}
