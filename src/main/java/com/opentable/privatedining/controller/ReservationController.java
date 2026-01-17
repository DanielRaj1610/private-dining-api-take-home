package com.opentable.privatedining.controller;

import com.opentable.privatedining.dto.ReservationDTO;
import com.opentable.privatedining.dto.request.CancellationRequest;
import com.opentable.privatedining.dto.request.CreateReservationRequest;
import com.opentable.privatedining.dto.response.CancellationResponse;
import com.opentable.privatedining.dto.response.PageResponse;
import com.opentable.privatedining.dto.response.ReservationResponse;
import com.opentable.privatedining.mapper.ReservationMapper;
import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller for reservation management.
 * Supports both legacy DTO format and new request/response format.
 */
@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservation", description = "Reservation management API")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationMapper reservationMapper;

    public ReservationController(ReservationService reservationService, ReservationMapper reservationMapper) {
        this.reservationService = reservationService;
        this.reservationMapper = reservationMapper;
    }

    @GetMapping
    @Operation(summary = "Get all reservations (paginated)", description = "Retrieve a paginated list of all reservations")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved list of reservations",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class)))
    public PageResponse<ReservationDTO> getAllReservations(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field", example = "reservationDate")
            @RequestParam(defaultValue = "reservationDate") String sortBy,
            @Parameter(description = "Sort direction (asc/desc)", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Reservation> reservationPage = reservationService.getAllReservations(pageable);
        Page<ReservationDTO> dtoPage = reservationPage.map(reservationMapper::toDTO);

        return PageResponse.from(dtoPage);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get reservation by ID", description = "Retrieve a reservation by its unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reservation found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation not found"),
            @ApiResponse(responseCode = "400", description = "Invalid ID format")
    })
    public ResponseEntity<ReservationResponse> getReservationById(
            @Parameter(description = "ID of the reservation to retrieve", required = true)
            @PathVariable String id) {
        try {
            ObjectId objectId = new ObjectId(id);
            Optional<Reservation> reservation = reservationService.getReservationById(objectId);
            return reservation.map(r -> ResponseEntity.ok(reservationService.toResponse(r)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    @Operation(
            summary = "Create new reservation",
            description = "Create a new private dining reservation. " +
                    "Implements flexible capacity model where multiple reservations can share " +
                    "the same time slot as long as total party size doesn't exceed max capacity."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Reservation created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (validation error, outside operating hours, invalid time slot)"),
            @ApiResponse(responseCode = "404", description = "Space not found"),
            @ApiResponse(responseCode = "409", description = "Capacity exceeded for the requested time slot")
    })
    public ResponseEntity<ReservationResponse> createReservation(
            @Parameter(description = "Reservation details", required = true)
            @Valid @RequestBody CreateReservationRequest request) {
        Reservation savedReservation = reservationService.createReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.toResponse(savedReservation));
    }

    @PostMapping("/{id}/cancel")
    @Operation(
            summary = "Cancel reservation",
            description = "Cancel an existing reservation with an optional cancellation reason"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reservation cancelled successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CancellationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation not found"),
            @ApiResponse(responseCode = "400", description = "Reservation is already cancelled")
    })
    public ResponseEntity<CancellationResponse> cancelReservation(
            @Parameter(description = "ID of the reservation to cancel", required = true)
            @PathVariable String id,
            @Parameter(description = "Cancellation details")
            @RequestBody(required = false) CancellationRequest request) {
        try {
            ObjectId objectId = new ObjectId(id);
            CancellationRequest cancellationRequest = request != null ? request : new CancellationRequest();
            CancellationResponse response = reservationService.cancelReservation(objectId, cancellationRequest);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete reservation", description = "Permanently delete a reservation by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Reservation deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Reservation not found"),
            @ApiResponse(responseCode = "400", description = "Invalid ID format")
    })
    public ResponseEntity<Void> deleteReservation(
            @Parameter(description = "ID of the reservation to delete", required = true)
            @PathVariable String id) {
        try {
            ObjectId objectId = new ObjectId(id);
            boolean deleted = reservationService.deleteReservation(objectId);
            return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Legacy endpoint - maintains backward compatibility
    @PostMapping("/legacy")
    @Operation(summary = "Create reservation (legacy format)", description = "Create a reservation using the legacy DTO format")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Reservation created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReservationDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<ReservationDTO> createReservationLegacy(
            @Parameter(description = "Reservation object to be created", required = true)
            @RequestBody ReservationDTO reservationDTO) {
        // Convert legacy DTO to new request format
        Reservation reservation = reservationMapper.toModel(reservationDTO);

        CreateReservationRequest request = CreateReservationRequest.builder()
                .spaceId(reservation.getSpaceId())
                .reservationDate(reservation.getReservationDate())
                .startTime(reservation.getStartTime())
                .partySize(reservation.getPartySize())
                .customerEmail(reservation.getCustomerEmail())
                .customerName(reservation.getCustomerEmail()) // Legacy DTO doesn't have customer name
                .build();

        Reservation savedReservation = reservationService.createReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationMapper.toDTO(savedReservation));
    }
}
