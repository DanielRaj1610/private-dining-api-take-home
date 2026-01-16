package com.opentable.privatedining.mapper;

import com.opentable.privatedining.dto.RestaurantDTO;
import com.opentable.privatedining.dto.SpaceDTO;
import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.service.RestaurantService;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for Restaurant entity and DTO.
 * Spaces are fetched from SpaceRepository since they're in a separate collection.
 */
@Component
public class RestaurantMapper {

    private final SpaceMapper spaceMapper;
    private final RestaurantService restaurantService;

    public RestaurantMapper(SpaceMapper spaceMapper, @Lazy RestaurantService restaurantService) {
        this.spaceMapper = spaceMapper;
        this.restaurantService = restaurantService;
    }

    /**
     * Convert Restaurant entity to DTO.
     * Includes spaces fetched from the spaces collection.
     */
    public RestaurantDTO toDTO(Restaurant restaurant) {
        if (restaurant == null) {
            return null;
        }

        // Fetch active spaces from the separate collection
        List<SpaceDTO> spaceDTOs = new ArrayList<>();
        if (restaurant.getId() != null) {
            List<Space> spaces = restaurantService.getActiveSpacesForRestaurant(restaurant.getId());
            spaceDTOs = spaces.stream()
                    .map(spaceMapper::toDTO)
                    .collect(Collectors.toList());
        }

        // Calculate total capacity from spaces
        int totalCapacity = spaceDTOs.stream()
                .mapToInt(s -> s.getMaxCapacity() != null ? s.getMaxCapacity() : 0)
                .sum();

        return new RestaurantDTO(
                restaurant.getId() != null ? restaurant.getId().toHexString() : null,
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getCuisineType(),
                totalCapacity,
                spaceDTOs
        );
    }

    /**
     * Convert Restaurant entity to DTO without spaces (for performance when spaces aren't needed).
     */
    public RestaurantDTO toDTOWithoutSpaces(Restaurant restaurant) {
        if (restaurant == null) {
            return null;
        }

        return new RestaurantDTO(
                restaurant.getId() != null ? restaurant.getId().toHexString() : null,
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getCuisineType(),
                null,
                new ArrayList<>()
        );
    }

    /**
     * Convert DTO to Restaurant entity.
     * Note: Spaces are managed separately and should be created via SpaceService.
     */
    public Restaurant toModel(RestaurantDTO restaurantDTO) {
        if (restaurantDTO == null) {
            return null;
        }

        Restaurant restaurant = Restaurant.builder()
                .name(restaurantDTO.getName())
                .address(restaurantDTO.getAddress())
                .cuisineType(restaurantDTO.getCuisineType())
                .build();

        if (restaurantDTO.getId() != null && !restaurantDTO.getId().isEmpty()) {
            try {
                restaurant.setId(new ObjectId(restaurantDTO.getId()));
            } catch (IllegalArgumentException e) {
                // Invalid ObjectId format, leave it null for new entities
            }
        }

        return restaurant;
    }
}
