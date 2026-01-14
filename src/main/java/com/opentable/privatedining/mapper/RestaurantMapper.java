package com.opentable.privatedining.mapper;

import com.opentable.privatedining.dto.RestaurantDTO;
import com.opentable.privatedining.dto.SpaceDTO;
import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RestaurantMapper {

    private final SpaceMapper spaceMapper;

    public RestaurantMapper(SpaceMapper spaceMapper) {
        this.spaceMapper = spaceMapper;
    }

    public RestaurantDTO toDTO(Restaurant restaurant) {
        if (restaurant == null) {
            return null;
        }

        List<SpaceDTO> spaceDTOs = new ArrayList<>();
        if (restaurant.getSpaces() != null) {
            spaceDTOs = restaurant.getSpaces().stream()
                    .map(spaceMapper::toDTO)
                    .collect(Collectors.toList());
        }

        return new RestaurantDTO(
                restaurant.getId() != null ? restaurant.getId().toString() : null,
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getCuisineType(),
                restaurant.getCapacity(),
                spaceDTOs
        );
    }

    public Restaurant toModel(RestaurantDTO restaurantDTO) {
        if (restaurantDTO == null) {
            return null;
        }

        Restaurant restaurant = new Restaurant(
                restaurantDTO.getName(),
                restaurantDTO.getAddress(),
                restaurantDTO.getCuisineType(),
                restaurantDTO.getCapacity()
        );

        if (restaurantDTO.getId() != null && !restaurantDTO.getId().isEmpty()) {
            try {
                restaurant.setId(new ObjectId(restaurantDTO.getId()));
            } catch (IllegalArgumentException e) {
                // Invalid ObjectId format, leave it null for new entities
            }
        }

        if (restaurantDTO.getSpaces() != null) {
            List<Space> spaces = restaurantDTO.getSpaces().stream()
                    .map(spaceMapper::toModel)
                    .collect(Collectors.toList());
            restaurant.setSpaces(spaces);
        }

        return restaurant;
    }
}