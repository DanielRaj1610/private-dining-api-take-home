package com.opentable.privatedining.service;

import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.repository.RestaurantRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private RestaurantService restaurantService;

    @Test
    void getAllRestaurants_ShouldReturnAllRestaurants() {
        // Given
        Restaurant restaurant1 = new Restaurant("Restaurant 1", "Address 1", "Italian", 50);
        Restaurant restaurant2 = new Restaurant("Restaurant 2", "Address 2", "French", 30);
        List<Restaurant> restaurants = Arrays.asList(restaurant1, restaurant2);

        when(restaurantRepository.findAll()).thenReturn(restaurants);

        // When
        List<Restaurant> result = restaurantService.getAllRestaurants();

        // Then
        assertEquals(2, result.size());
        assertEquals("Restaurant 1", result.get(0).getName());
        assertEquals("Restaurant 2", result.get(1).getName());
        verify(restaurantRepository).findAll();
    }

    @Test
    void getRestaurantById_WhenRestaurantExists_ShouldReturnRestaurant() {
        // Given
        ObjectId restaurantId = new ObjectId();
        Restaurant restaurant = new Restaurant("Test Restaurant", "Test Address", "Test Cuisine", 40);
        restaurant.setId(restaurantId);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

        // When
        Optional<Restaurant> result = restaurantService.getRestaurantById(restaurantId);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Test Restaurant", result.get().getName());
        assertEquals("Test Address", result.get().getAddress());
        verify(restaurantRepository).findById(restaurantId);
    }

    @Test
    void getRestaurantById_WhenRestaurantNotFound_ShouldReturnEmpty() {
        // Given
        ObjectId restaurantId = new ObjectId();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        // When
        Optional<Restaurant> result = restaurantService.getRestaurantById(restaurantId);

        // Then
        assertFalse(result.isPresent());
        verify(restaurantRepository).findById(restaurantId);
    }

    @Test
    void createRestaurant_ShouldReturnSavedRestaurant() {
        // Given
        Restaurant inputRestaurant = new Restaurant("New Restaurant", "New Address", "New Cuisine", 60);
        Restaurant savedRestaurant = new Restaurant("New Restaurant", "New Address", "New Cuisine", 60);
        savedRestaurant.setId(new ObjectId());

        when(restaurantRepository.save(inputRestaurant)).thenReturn(savedRestaurant);

        // When
        Restaurant result = restaurantService.createRestaurant(inputRestaurant);

        // Then
        assertNotNull(result);
        assertEquals("New Restaurant", result.getName());
        assertNotNull(result.getId());
        verify(restaurantRepository).save(inputRestaurant);
    }

    @Test
    void updateRestaurant_WhenRestaurantExists_ShouldReturnUpdatedRestaurant() {
        // Given
        ObjectId restaurantId = new ObjectId();
        Restaurant existingRestaurant = new Restaurant("Old Restaurant", "Old Address", "Old Cuisine", 50);
        existingRestaurant.setId(restaurantId);

        Restaurant updatedRestaurant = new Restaurant("Updated Restaurant", "Updated Address", "Updated Cuisine", 70);
        updatedRestaurant.setId(restaurantId);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(existingRestaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenReturn(updatedRestaurant);

        // When
        Optional<Restaurant> result = restaurantService.updateRestaurant(restaurantId, updatedRestaurant);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Updated Restaurant", result.get().getName());
        assertEquals(restaurantId, result.get().getId());
        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository).save(updatedRestaurant);
    }

    @Test
    void updateRestaurant_WhenRestaurantNotFound_ShouldReturnEmpty() {
        // Given
        ObjectId restaurantId = new ObjectId();
        Restaurant updatedRestaurant = new Restaurant("Updated Restaurant", "Updated Address", "Updated Cuisine", 70);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        // When
        Optional<Restaurant> result = restaurantService.updateRestaurant(restaurantId, updatedRestaurant);

        // Then
        assertFalse(result.isPresent());
        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    void deleteRestaurant_WhenRestaurantExists_ShouldReturnTrue() {
        // Given
        ObjectId restaurantId = new ObjectId();
        Restaurant existingRestaurant = new Restaurant("Test Restaurant", "Test Address", "Test Cuisine", 50);
        existingRestaurant.setId(restaurantId);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(existingRestaurant));

        // When
        boolean result = restaurantService.deleteRestaurant(restaurantId);

        // Then
        assertTrue(result);
        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository).deleteById(restaurantId);
    }

    @Test
    void deleteRestaurant_WhenRestaurantNotFound_ShouldReturnFalse() {
        // Given
        ObjectId restaurantId = new ObjectId();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        // When
        boolean result = restaurantService.deleteRestaurant(restaurantId);

        // Then
        assertFalse(result);
        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository, never()).deleteById(restaurantId);
    }

    @Test
    void addSpaceToRestaurant_WhenRestaurantExists_ShouldReturnUpdatedRestaurant() {
        // Given
        ObjectId restaurantId = new ObjectId();
        Restaurant restaurant = new Restaurant("Test Restaurant", "Test Address", "Test Cuisine", 50);
        restaurant.setId(restaurantId);

        Space space = new Space("Private Room", 2, 10);
        Restaurant updatedRestaurant = new Restaurant("Test Restaurant", "Test Address", "Test Cuisine", 50);
        updatedRestaurant.setId(restaurantId);
        updatedRestaurant.getSpaces().add(space);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(restaurant)).thenReturn(updatedRestaurant);

        // When
        Optional<Restaurant> result = restaurantService.addSpaceToRestaurant(restaurantId, space);

        // Then
        assertTrue(result.isPresent());
        assertEquals(1, result.get().getSpaces().size());
        assertEquals("Private Room", result.get().getSpaces().get(0).getName());
        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    void addSpaceToRestaurant_WhenRestaurantNotFound_ShouldReturnEmpty() {
        // Given
        ObjectId restaurantId = new ObjectId();
        Space space = new Space("Private Room", 2, 10);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        // When
        Optional<Restaurant> result = restaurantService.addSpaceToRestaurant(restaurantId, space);

        // Then
        assertFalse(result.isPresent());
        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    void removeSpaceFromRestaurant_WhenSpaceExists_ShouldReturnUpdatedRestaurant() {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        Space space = new Space("Private Room", 2, 10);
        space.setId(spaceId);

        Restaurant restaurant = new Restaurant("Test Restaurant", "Test Address", "Test Cuisine", 50);
        restaurant.setId(restaurantId);
        restaurant.getSpaces().add(space);

        Restaurant updatedRestaurant = new Restaurant("Test Restaurant", "Test Address", "Test Cuisine", 50);
        updatedRestaurant.setId(restaurantId);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(restaurant)).thenReturn(updatedRestaurant);

        // When
        Optional<Restaurant> result = restaurantService.removeSpaceFromRestaurant(restaurantId, spaceId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(0, restaurant.getSpaces().size());
        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    void removeSpaceFromRestaurant_WhenRestaurantNotFound_ShouldReturnEmpty() {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        // When
        Optional<Restaurant> result = restaurantService.removeSpaceFromRestaurant(restaurantId, spaceId);

        // Then
        assertFalse(result.isPresent());
        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    void getSpaceById_WhenSpaceExists_ShouldReturnSpace() {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        Space space = new Space("Private Room", 2, 10);
        space.setId(spaceId);

        Restaurant restaurant = new Restaurant("Test Restaurant", "Test Address", "Test Cuisine", 50);
        restaurant.setId(restaurantId);
        restaurant.getSpaces().add(space);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

        // When
        Optional<Space> result = restaurantService.getSpaceById(restaurantId, spaceId);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Private Room", result.get().getName());
        assertEquals(spaceId, result.get().getId());
        verify(restaurantRepository).findById(restaurantId);
    }

    @Test
    void getSpaceById_WhenSpaceNotFound_ShouldReturnEmpty() {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        Restaurant restaurant = new Restaurant("Test Restaurant", "Test Address", "Test Cuisine", 50);
        restaurant.setId(restaurantId);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

        // When
        Optional<Space> result = restaurantService.getSpaceById(restaurantId, spaceId);

        // Then
        assertFalse(result.isPresent());
        verify(restaurantRepository).findById(restaurantId);
    }

    @Test
    void getSpaceById_WhenRestaurantNotFound_ShouldReturnEmpty() {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        // When
        Optional<Space> result = restaurantService.getSpaceById(restaurantId, spaceId);

        // Then
        assertFalse(result.isPresent());
        verify(restaurantRepository).findById(restaurantId);
    }

    @Test
    void spaceExistsInRestaurant_WhenSpaceExists_ShouldReturnTrue() {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        Space space = new Space("Private Room", 2, 10);
        space.setId(spaceId);

        Restaurant restaurant = new Restaurant("Test Restaurant", "Test Address", "Test Cuisine", 50);
        restaurant.setId(restaurantId);
        restaurant.getSpaces().add(space);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

        // When
        boolean result = restaurantService.spaceExistsInRestaurant(restaurantId, spaceId);

        // Then
        assertTrue(result);
        verify(restaurantRepository).findById(restaurantId);
    }

    @Test
    void spaceExistsInRestaurant_WhenSpaceNotFound_ShouldReturnFalse() {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        Restaurant restaurant = new Restaurant("Test Restaurant", "Test Address", "Test Cuisine", 50);
        restaurant.setId(restaurantId);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

        // When
        boolean result = restaurantService.spaceExistsInRestaurant(restaurantId, spaceId);

        // Then
        assertFalse(result);
        verify(restaurantRepository).findById(restaurantId);
    }
}