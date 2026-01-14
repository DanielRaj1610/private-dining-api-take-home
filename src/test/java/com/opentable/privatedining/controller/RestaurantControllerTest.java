package com.opentable.privatedining.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opentable.privatedining.dto.RestaurantDTO;
import com.opentable.privatedining.dto.SpaceDTO;
import com.opentable.privatedining.mapper.RestaurantMapper;
import com.opentable.privatedining.mapper.SpaceMapper;
import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.service.RestaurantService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RestaurantController.class)
class RestaurantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RestaurantService restaurantService;

    @MockBean
    private RestaurantMapper restaurantMapper;

    @MockBean
    private SpaceMapper spaceMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllRestaurants_ShouldReturnListOfRestaurants() throws Exception {
        // Given
        Restaurant restaurant1 = new Restaurant("Restaurant 1", "Address 1", "Italian", 50);
        Restaurant restaurant2 = new Restaurant("Restaurant 2", "Address 2", "French", 30);
        List<Restaurant> restaurants = Arrays.asList(restaurant1, restaurant2);

        RestaurantDTO restaurantDTO1 = new RestaurantDTO("1", "Restaurant 1", "Address 1", "Italian", 50, Arrays.asList());
        RestaurantDTO restaurantDTO2 = new RestaurantDTO("2", "Restaurant 2", "Address 2", "French", 30, Arrays.asList());

        when(restaurantService.getAllRestaurants()).thenReturn(restaurants);
        when(restaurantMapper.toDTO(any(Restaurant.class))).thenReturn(restaurantDTO1).thenReturn(restaurantDTO2);

        // When & Then
        mockMvc.perform(get("/v1/restaurants"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Restaurant 1"))
                .andExpect(jsonPath("$[0].address").value("Address 1"))
                .andExpect(jsonPath("$[0].cuisineType").value("Italian"))
                .andExpect(jsonPath("$[0].capacity").value(50))
                .andExpect(jsonPath("$[1].name").value("Restaurant 2"));
    }

    @Test
    void getRestaurantById_WhenRestaurantExists_ShouldReturnRestaurant() throws Exception {
        // Given
        ObjectId restaurantId = new ObjectId();
        Restaurant restaurant = new Restaurant("Test Restaurant", "Test Address", "Test Cuisine", 40);
        restaurant.setId(restaurantId);

        RestaurantDTO restaurantDTO = new RestaurantDTO(restaurantId.toString(), "Test Restaurant", "Test Address", "Test Cuisine", 40, Arrays.asList());

        when(restaurantService.getRestaurantById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantMapper.toDTO(any(Restaurant.class))).thenReturn(restaurantDTO);

        // When & Then
        mockMvc.perform(get("/v1/restaurants/" + restaurantId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Test Restaurant"))
                .andExpect(jsonPath("$.address").value("Test Address"))
                .andExpect(jsonPath("$.cuisineType").value("Test Cuisine"))
                .andExpect(jsonPath("$.capacity").value(40));
    }

    @Test
    void getRestaurantById_WhenRestaurantNotFound_ShouldReturn404() throws Exception {
        // Given
        ObjectId restaurantId = new ObjectId();
        when(restaurantService.getRestaurantById(restaurantId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/v1/restaurants/" + restaurantId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRestaurantById_WhenInvalidId_ShouldReturn400() throws Exception {
        // When & Then
        mockMvc.perform(get("/v1/restaurants/invalid-id"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRestaurant_ShouldReturnCreatedRestaurant() throws Exception {
        // Given
        RestaurantDTO inputRestaurantDTO = new RestaurantDTO("New Restaurant", "New Address", "New Cuisine", 60);
        Restaurant restaurant = new Restaurant("New Restaurant", "New Address", "New Cuisine", 60);
        Restaurant savedRestaurant = new Restaurant("New Restaurant", "New Address", "New Cuisine", 60);
        savedRestaurant.setId(new ObjectId());
        RestaurantDTO savedRestaurantDTO = new RestaurantDTO(savedRestaurant.getId().toString(), "New Restaurant", "New Address", "New Cuisine", 60, Arrays.asList());

        when(restaurantMapper.toModel(any(RestaurantDTO.class))).thenReturn(restaurant);
        when(restaurantService.createRestaurant(any(Restaurant.class))).thenReturn(savedRestaurant);
        when(restaurantMapper.toDTO(any(Restaurant.class))).thenReturn(savedRestaurantDTO);

        // When & Then
        mockMvc.perform(post("/v1/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputRestaurantDTO)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("New Restaurant"))
                .andExpect(jsonPath("$.address").value("New Address"))
                .andExpect(jsonPath("$.cuisineType").value("New Cuisine"))
                .andExpect(jsonPath("$.capacity").value(60));
    }

    @Test
    void updateRestaurant_WhenRestaurantExists_ShouldReturnUpdatedRestaurant() throws Exception {
        // Given
        ObjectId restaurantId = new ObjectId();
        RestaurantDTO inputRestaurantDTO = new RestaurantDTO("Updated Restaurant", "Updated Address", "Updated Cuisine", 70);
        Restaurant restaurant = new Restaurant("Updated Restaurant", "Updated Address", "Updated Cuisine", 70);
        Restaurant updatedRestaurant = new Restaurant("Updated Restaurant", "Updated Address", "Updated Cuisine", 70);
        updatedRestaurant.setId(restaurantId);
        RestaurantDTO updatedRestaurantDTO = new RestaurantDTO(restaurantId.toString(), "Updated Restaurant", "Updated Address", "Updated Cuisine", 70, Arrays.asList());

        when(restaurantMapper.toModel(any(RestaurantDTO.class))).thenReturn(restaurant);
        when(restaurantService.updateRestaurant(eq(restaurantId), any(Restaurant.class))).thenReturn(Optional.of(updatedRestaurant));
        when(restaurantMapper.toDTO(any(Restaurant.class))).thenReturn(updatedRestaurantDTO);

        // When & Then
        mockMvc.perform(put("/v1/restaurants/" + restaurantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputRestaurantDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Updated Restaurant"))
                .andExpect(jsonPath("$.address").value("Updated Address"));
    }

    @Test
    void updateRestaurant_WhenRestaurantNotFound_ShouldReturn404() throws Exception {
        // Given
        ObjectId restaurantId = new ObjectId();
        RestaurantDTO restaurantDTO = new RestaurantDTO("Restaurant", "Address", "Cuisine", 50);
        Restaurant restaurant = new Restaurant("Restaurant", "Address", "Cuisine", 50);

        when(restaurantMapper.toModel(restaurantDTO)).thenReturn(restaurant);
        when(restaurantService.updateRestaurant(eq(restaurantId), eq(restaurant))).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(put("/v1/restaurants/" + restaurantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(restaurantDTO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRestaurant_WhenInvalidId_ShouldReturn400() throws Exception {
        // Given
        RestaurantDTO restaurantDTO = new RestaurantDTO("Restaurant", "Address", "Cuisine", 50);

        // When & Then
        mockMvc.perform(put("/v1/restaurants/invalid-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(restaurantDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRestaurant_WhenRestaurantExists_ShouldReturn204() throws Exception {
        // Given
        ObjectId restaurantId = new ObjectId();
        when(restaurantService.deleteRestaurant(restaurantId)).thenReturn(true);

        // When & Then
        mockMvc.perform(delete("/v1/restaurants/" + restaurantId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRestaurant_WhenRestaurantNotFound_ShouldReturn404() throws Exception {
        // Given
        ObjectId restaurantId = new ObjectId();
        when(restaurantService.deleteRestaurant(restaurantId)).thenReturn(false);

        // When & Then
        mockMvc.perform(delete("/v1/restaurants/" + restaurantId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRestaurant_WhenInvalidId_ShouldReturn400() throws Exception {
        // When & Then
        mockMvc.perform(delete("/v1/restaurants/invalid-id"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addSpaceToRestaurant_WhenRestaurantExists_ShouldReturnUpdatedRestaurant() throws Exception {
        // Given
        ObjectId restaurantId = new ObjectId();
        SpaceDTO spaceDTO = new SpaceDTO("Private Room", 2, 10);
        Space space = new Space("Private Room", 2, 10);
        Restaurant updatedRestaurant = new Restaurant("Test Restaurant", "Test Address", "Test Cuisine", 50);
        updatedRestaurant.setId(restaurantId);
        updatedRestaurant.getSpaces().add(space);

        SpaceDTO returnedSpaceDTO = new SpaceDTO(UUID.randomUUID(), "Private Room", 2, 10);
        RestaurantDTO updatedRestaurantDTO = new RestaurantDTO(restaurantId.toString(), "Test Restaurant", "Test Address", "Test Cuisine", 50, Arrays.asList(returnedSpaceDTO));

        when(spaceMapper.toModel(any(SpaceDTO.class))).thenReturn(space);
        when(restaurantService.addSpaceToRestaurant(eq(restaurantId), any(Space.class))).thenReturn(Optional.of(updatedRestaurant));
        when(restaurantMapper.toDTO(any(Restaurant.class))).thenReturn(updatedRestaurantDTO);

        // When & Then
        mockMvc.perform(post("/v1/restaurants/" + restaurantId.toString() + "/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spaceDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.spaces.length()").value(1))
                .andExpect(jsonPath("$.spaces[0].name").value("Private Room"))
                .andExpect(jsonPath("$.spaces[0].minCapacity").value(2))
                .andExpect(jsonPath("$.spaces[0].maxCapacity").value(10));
    }

    @Test
    void addSpaceToRestaurant_WhenRestaurantNotFound_ShouldReturn404() throws Exception {
        // Given
        ObjectId restaurantId = new ObjectId();
        SpaceDTO spaceDTO = new SpaceDTO("Private Room", 2, 10);
        Space space = new Space("Private Room", 2, 10);

        when(spaceMapper.toModel(spaceDTO)).thenReturn(space);
        when(restaurantService.addSpaceToRestaurant(eq(restaurantId), eq(space))).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(post("/v1/restaurants/" + restaurantId.toString() + "/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spaceDTO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addSpaceToRestaurant_WhenInvalidId_ShouldReturn400() throws Exception {
        // Given
        SpaceDTO spaceDTO = new SpaceDTO("Private Room", 2, 10);

        // When & Then
        mockMvc.perform(post("/v1/restaurants/invalid-id/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spaceDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeSpaceFromRestaurant_WhenSpaceExists_ShouldReturnUpdatedRestaurant() throws Exception {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();
        Restaurant updatedRestaurant = new Restaurant("Test Restaurant", "Test Address", "Test Cuisine", 50);
        updatedRestaurant.setId(restaurantId);
        RestaurantDTO updatedRestaurantDTO = new RestaurantDTO(restaurantId.toString(), "Test Restaurant", "Test Address", "Test Cuisine", 50, Arrays.asList());

        when(restaurantService.removeSpaceFromRestaurant(restaurantId, spaceId)).thenReturn(Optional.of(updatedRestaurant));
        when(restaurantMapper.toDTO(any(Restaurant.class))).thenReturn(updatedRestaurantDTO);

        // When & Then
        mockMvc.perform(delete("/v1/restaurants/" + restaurantId.toString() + "/spaces/" + spaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Test Restaurant"));
    }

    @Test
    void removeSpaceFromRestaurant_WhenRestaurantOrSpaceNotFound_ShouldReturn404() throws Exception {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        when(restaurantService.removeSpaceFromRestaurant(restaurantId, spaceId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(delete("/v1/restaurants/" + restaurantId.toString() + "/spaces/" + spaceId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeSpaceFromRestaurant_WhenInvalidRestaurantId_ShouldReturn400() throws Exception {
        // Given
        UUID spaceId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(delete("/v1/restaurants/invalid-id/spaces/" + spaceId.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeSpaceFromRestaurant_WhenInvalidSpaceId_ShouldReturn400() throws Exception {
        // Given
        ObjectId restaurantId = new ObjectId();

        // When & Then
        mockMvc.perform(delete("/v1/restaurants/" + restaurantId.toString() + "/spaces/invalid-uuid"))
                .andExpect(status().isBadRequest());
    }
}