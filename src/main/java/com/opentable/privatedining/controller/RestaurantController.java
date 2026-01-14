package com.opentable.privatedining.controller;

import com.opentable.privatedining.dto.RestaurantDTO;
import com.opentable.privatedining.dto.SpaceDTO;
import com.opentable.privatedining.mapper.RestaurantMapper;
import com.opentable.privatedining.mapper.SpaceMapper;
import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.service.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/restaurants")
@Tag(name = "Restaurant", description = "Restaurant management API")
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final RestaurantMapper restaurantMapper;
    private final SpaceMapper spaceMapper;

    public RestaurantController(RestaurantService restaurantService, RestaurantMapper restaurantMapper, SpaceMapper spaceMapper) {
        this.restaurantService = restaurantService;
        this.restaurantMapper = restaurantMapper;
        this.spaceMapper = spaceMapper;
    }

    @GetMapping
    @Operation(summary = "Get all restaurants", description = "Retrieve a list of all restaurants")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved list of restaurants",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RestaurantDTO.class)))
    public List<RestaurantDTO> getAllRestaurants() {
        return restaurantService.getAllRestaurants()
                .stream()
                .map(restaurantMapper::toDTO)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get restaurant by ID", description = "Retrieve a restaurant by its unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Restaurant found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RestaurantDTO.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant not found")
    })
    public ResponseEntity<RestaurantDTO> getRestaurantById(
            @Parameter(description = "ID of the restaurant to retrieve", required = true)
            @PathVariable String id) {
        try {
            ObjectId objectId = new ObjectId(id);
            Optional<Restaurant> restaurant = restaurantService.getRestaurantById(objectId);
            return restaurant.map(r -> ResponseEntity.ok(restaurantMapper.toDTO(r)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    @Operation(summary = "Create new restaurant", description = "Create a new restaurant in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Restaurant created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RestaurantDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<RestaurantDTO> createRestaurant(
            @Parameter(description = "Restaurant object to be created", required = true)
            @RequestBody RestaurantDTO restaurantDTO) {
        Restaurant restaurant = restaurantMapper.toModel(restaurantDTO);
        Restaurant savedRestaurant = restaurantService.createRestaurant(restaurant);
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantMapper.toDTO(savedRestaurant));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update restaurant", description = "Update an existing restaurant by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Restaurant updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RestaurantDTO.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant not found"),
            @ApiResponse(responseCode = "400", description = "Invalid ID format")
    })
    public ResponseEntity<RestaurantDTO> updateRestaurant(
            @Parameter(description = "ID of the restaurant to update", required = true)
            @PathVariable String id,
            @Parameter(description = "Updated restaurant object", required = true)
            @RequestBody RestaurantDTO restaurantDTO) {
        try {
            ObjectId objectId = new ObjectId(id);
            Restaurant restaurant = restaurantMapper.toModel(restaurantDTO);
            Optional<Restaurant> updatedRestaurant = restaurantService.updateRestaurant(objectId, restaurant);
            return updatedRestaurant.map(r -> ResponseEntity.ok(restaurantMapper.toDTO(r)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete restaurant", description = "Delete a restaurant by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Restaurant deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Restaurant not found"),
            @ApiResponse(responseCode = "400", description = "Invalid ID format")
    })
    public ResponseEntity<Void> deleteRestaurant(
            @Parameter(description = "ID of the restaurant to delete", required = true)
            @PathVariable String id) {
        try {
            ObjectId objectId = new ObjectId(id);
            boolean deleted = restaurantService.deleteRestaurant(objectId);
            return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/spaces")
    @Operation(summary = "Add space to restaurant", description = "Add a new space to a restaurant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Space added successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RestaurantDTO.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant not found"),
            @ApiResponse(responseCode = "400", description = "Invalid ID format")
    })
    public ResponseEntity<RestaurantDTO> addSpaceToRestaurant(
            @Parameter(description = "ID of the restaurant", required = true)
            @PathVariable String id,
            @Parameter(description = "Space object to be added", required = true)
            @RequestBody SpaceDTO spaceDTO) {
        try {
            ObjectId objectId = new ObjectId(id);
            Space space = spaceMapper.toModel(spaceDTO);
            Optional<Restaurant> updatedRestaurant = restaurantService.addSpaceToRestaurant(objectId, space);
            return updatedRestaurant.map(r -> ResponseEntity.ok(restaurantMapper.toDTO(r)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}/spaces/{spaceId}")
    @Operation(summary = "Remove space from restaurant", description = "Remove a space from a restaurant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Space removed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RestaurantDTO.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant or space not found"),
            @ApiResponse(responseCode = "400", description = "Invalid ID format")
    })
    public ResponseEntity<RestaurantDTO> removeSpaceFromRestaurant(
            @Parameter(description = "ID of the restaurant", required = true)
            @PathVariable String id,
            @Parameter(description = "UUID of the space to remove", required = true)
            @PathVariable String spaceId) {
        try {
            ObjectId objectId = new ObjectId(id);
            UUID spaceUuid = UUID.fromString(spaceId);
            Optional<Restaurant> updatedRestaurant = restaurantService.removeSpaceFromRestaurant(objectId, spaceUuid);
            return updatedRestaurant.map(r -> ResponseEntity.ok(restaurantMapper.toDTO(r)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}