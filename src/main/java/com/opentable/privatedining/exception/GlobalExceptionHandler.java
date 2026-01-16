package com.opentable.privatedining.exception;

import com.opentable.privatedining.dto.response.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for REST API.
 * Provides consistent error responses with trace IDs for debugging.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RestaurantNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleRestaurantNotFound(
            RestaurantNotFoundException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Restaurant not found: {}", traceId, ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND",
                ex.getMessage(), request, traceId);
    }

    @ExceptionHandler(SpaceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSpaceNotFound(
            SpaceNotFoundException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Space not found: {}", traceId, ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND",
                ex.getMessage(), request, traceId);
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleReservationNotFound(
            ReservationNotFoundException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Reservation not found: {}", traceId, ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND",
                ex.getMessage(), request, traceId);
    }

    @ExceptionHandler(InvalidPartySizeException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPartySize(
            InvalidPartySizeException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Invalid party size: {}", traceId, ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_PARTY_SIZE",
                ex.getMessage(), request, traceId);
    }

    @ExceptionHandler(ReservationConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleReservationConflict(
            ReservationConflictException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Reservation conflict: {}", traceId, ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "RESERVATION_CONFLICT",
                ex.getMessage(), request, traceId);
    }

    @ExceptionHandler(OutsideOperatingHoursException.class)
    public ResponseEntity<ApiErrorResponse> handleOutsideOperatingHours(
            OutsideOperatingHoursException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Outside operating hours: {}", traceId, ex.getMessage());

        ApiErrorResponse response = buildApiErrorResponse(HttpStatus.BAD_REQUEST,
                "OUTSIDE_OPERATING_HOURS", ex.getMessage(), request, traceId);

        if (!ex.isClosed() && ex.getOperatingStart() != null) {
            Map<String, Object> details = new HashMap<>();
            details.put("operatingStart", ex.getOperatingStart().toString());
            details.put("operatingEnd", ex.getOperatingEnd().toString());
            details.put("requestedDate", ex.getDate().toString());
            response.setDetails(details);
        }

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CapacityExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleCapacityExceeded(
            CapacityExceededException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Capacity exceeded: {}", traceId, ex.getMessage());

        ApiErrorResponse response = buildApiErrorResponse(HttpStatus.CONFLICT,
                "CAPACITY_EXCEEDED", ex.getMessage(), request, traceId);

        if (ex.getSpaceName() != null) {
            Map<String, Object> details = new HashMap<>();
            details.put("spaceName", ex.getSpaceName());
            details.put("maxCapacity", ex.getMaxCapacity());
            details.put("currentlyBooked", ex.getCurrentlyBooked());
            details.put("availableCapacity", ex.getAvailableCapacity());
            details.put("requestedPartySize", ex.getRequestedPartySize());
            response.setDetails(details);
        }

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidTimeSlotException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTimeSlot(
            InvalidTimeSlotException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Invalid time slot: {}", traceId, ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_TIME_SLOT",
                ex.getMessage(), request, traceId);
    }

    @ExceptionHandler(ReservationException.class)
    public ResponseEntity<ApiErrorResponse> handleReservationException(
            ReservationException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Reservation error: {}", traceId, ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(),
                ex.getMessage(), request, traceId);
    }

    @ExceptionHandler(ConcurrentModificationException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrentModification(
            ConcurrentModificationException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Concurrent modification: {}", traceId, ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                ex.getMessage(), request, traceId);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLocking(
            OptimisticLockingFailureException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Optimistic locking failure: {}", traceId, ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The resource was modified by another request. Please retry.", request, traceId);
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidDateRange(
            InvalidDateRangeException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Invalid date range: {}", traceId, ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                ex.getMessage(), request, traceId);
    }

    @ExceptionHandler(AdvanceBookingLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleAdvanceBookingLimit(
            AdvanceBookingLimitException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Advance booking limit exceeded: {}", traceId, ex.getMessage());

        ApiErrorResponse response = buildApiErrorResponse(HttpStatus.BAD_REQUEST,
                "ADVANCE_BOOKING_LIMIT_EXCEEDED", ex.getMessage(), request, traceId);

        if (ex.getRequestedDate() != null) {
            Map<String, Object> details = new HashMap<>();
            details.put("requestedDate", ex.getRequestedDate().toString());
            details.put("maxAdvanceDays", ex.getMaxAdvanceDays());
            response.setDetails(details);
        }

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Validation error: {}", traceId, ex.getMessage());

        Map<String, String> validationErrors = new HashMap<>();
        String errorCode = "VALIDATION_ERROR";
        String errorMessage = "Request validation failed";

        // Check for specific field errors and map to specific error codes
        for (var error : ex.getBindingResult().getAllErrors()) {
            if (error instanceof FieldError fieldError) {
                String fieldName = fieldError.getField();
                String message = error.getDefaultMessage();
                validationErrors.put(fieldName, message);

                // Map specific field validations to specific error codes
                String code = fieldError.getCode();
                if ("reservationDate".equals(fieldName) &&
                    ("FutureOrPresent".equals(code) || "Future".equals(code))) {
                    errorCode = "PAST_DATE";
                    errorMessage = "Reservation date must be today or in the future";
                } else if ("partySize".equals(fieldName) && "Max".equals(code)) {
                    errorCode = "INVALID_PARTY_SIZE";
                    errorMessage = message;
                } else if ("partySize".equals(fieldName) && "Min".equals(code)) {
                    errorCode = "INVALID_PARTY_SIZE";
                    errorMessage = message;
                } else if ("customerEmail".equals(fieldName) && "Email".equals(code)) {
                    errorCode = "INVALID_EMAIL";
                    errorMessage = "Invalid email format";
                }
            }
        }

        ApiErrorResponse response = buildApiErrorResponse(HttpStatus.BAD_REQUEST,
                errorCode, errorMessage, request, traceId);
        response.setValidationErrors(validationErrors);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.warn("TraceId: {} - Invalid argument: {}", traceId, ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
                ex.getMessage(), request, traceId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        String traceId = generateTraceId();
        logger.error("TraceId: {} - Unexpected error: {}", traceId, ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.", request, traceId);
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus status, String errorCode, String message,
            WebRequest request, String traceId) {
        ApiErrorResponse response = buildApiErrorResponse(status, errorCode, message, request, traceId);
        return new ResponseEntity<>(response, status);
    }

    private ApiErrorResponse buildApiErrorResponse(
            HttpStatus status, String errorCode, String message,
            WebRequest request, String traceId) {
        return ApiErrorResponse.builder()
                .status(status.value())
                .error(errorCode)
                .message(message)
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
