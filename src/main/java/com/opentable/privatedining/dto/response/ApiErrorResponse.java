package com.opentable.privatedining.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standard API error response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API error response")
public class ApiErrorResponse {

    @Schema(description = "HTTP status code", example = "400")
    private Integer status;

    @Schema(description = "Error code for programmatic handling", example = "CAPACITY_EXCEEDED")
    private String error;

    @Schema(description = "Human-readable error message", example = "Insufficient capacity for the requested party size")
    private String message;

    @Schema(description = "Timestamp when the error occurred")
    private LocalDateTime timestamp;

    @Schema(description = "Request path that caused the error", example = "/api/v1/reservations")
    private String path;

    @Schema(description = "Unique trace ID for debugging", example = "abc123-def456")
    private String traceId;

    @Schema(description = "Validation errors for each field")
    private Map<String, String> validationErrors;

    @Schema(description = "Additional details about the error")
    private Map<String, Object> details;

    /**
     * Create a simple error response.
     */
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return ApiErrorResponse.builder()
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create an error response with trace ID.
     */
    public static ApiErrorResponse withTraceId(int status, String error, String message, String path, String traceId) {
        return ApiErrorResponse.builder()
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
