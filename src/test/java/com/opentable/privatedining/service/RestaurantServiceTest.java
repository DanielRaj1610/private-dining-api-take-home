package com.opentable.privatedining.service;

import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.repository.RestaurantRepository;
import com.opentable.privatedining.repository.SpaceRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private SpaceRepository spaceRepository;

    @InjectMocks
    private RestaurantService restaurantService;

    private Restaurant createTestRestaurant(String name, String address, String cuisineType) {
        return Restaurant.builder()
                .name(name)
                .address(address)
                .cuisineType(cuisineType)
                .city("New York")
                .state("NY")
                .zipCode("10001")
                .build();
    }

    private Space createTestSpace(String name, int minCapacity, int maxCapacity) {
        return Space.builder()
                .id(UUID.randomUUID())
                .name(name)
                .minCapacity(minCapacity)
                .maxCapacity(maxCapacity)
                .slotDurationMinutes(60)
                .bufferMinutes(15)
                .isActive(true)
                .build();
    }

    @Test
    void getAllRestaurants_ShouldReturnAllRestaurants() {
        // Given
        Restaurant restaurant1 = createTestRestaurant("Restaurant 1", "Address 1", "Italian");
        Restaurant restaurant2 = createTestRestaurant("Restaurant 2", "Address 2", "French");
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
        Restaurant restaurant = createTestRestaurant("Test Restaurant", "Test Address", "Test Cuisine");
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
        Restaurant inputRestaurant = createTestRestaurant("New Restaurant", "New Address", "New Cuisine");
        Restaurant savedRestaurant = createTestRestaurant("New Restaurant", "New Address", "New Cuisine");
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
        Restaurant existingRestaurant = createTestRestaurant("Old Restaurant", "Old Address", "Old Cuisine");
        existingRestaurant.setId(restaurantId);

        Restaurant updatedRestaurant = createTestRestaurant("Updated Restaurant", "Updated Address", "Updated Cuisine");
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
        Restaurant updatedRestaurant = createTestRestaurant("Updated Restaurant", "Updated Address", "Updated Cuisine");

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
        Restaurant existingRestaurant = createTestRestaurant("Test Restaurant", "Test Address", "Test Cuisine");
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
    void addSpaceToRestaurant_WhenRestaurantExists_ShouldReturnSavedSpace() {
        // Given
        ObjectId restaurantId = new ObjectId();
        Restaurant restaurant = createTestRestaurant("Test Restaurant", "Test Address", "Test Cuisine");
        restaurant.setId(restaurantId);

        Space space = createTestSpace("Private Room", 2, 10);
        Space savedSpace = createTestSpace("Private Room", 2, 10);
        savedSpace.setRestaurantId(restaurantId.toHexString());

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(spaceRepository.save(any(Space.class))).thenReturn(savedSpace);

        // When
        Optional<Space> result = restaurantService.addSpaceToRestaurant(restaurantId, space);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Private Room", result.get().getName());
        assertEquals(restaurantId.toHexString(), result.get().getRestaurantId());
        verify(restaurantRepository).findById(restaurantId);
        verify(spaceRepository).save(any(Space.class));
    }

    @Test
    void addSpaceToRestaurant_WhenRestaurantNotFound_ShouldReturnEmpty() {
        // Given
        ObjectId restaurantId = new ObjectId();
        Space space = createTestSpace("Private Room", 2, 10);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        // When
        Optional<Space> result = restaurantService.addSpaceToRestaurant(restaurantId, space);

        // Then
        assertFalse(result.isPresent());
        verify(restaurantRepository).findById(restaurantId);
        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    void removeSpaceFromRestaurant_WhenSpaceExists_ShouldReturnTrue() {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        Space space = createTestSpace("Private Room", 2, 10);
        space.setId(spaceId);
        space.setRestaurantId(restaurantId.toHexString());

        when(spaceRepository.findByIdAndRestaurantId(spaceId, restaurantId.toHexString()))
                .thenReturn(Optional.of(space));

        // When
        boolean result = restaurantService.removeSpaceFromRestaurant(restaurantId, spaceId);

        // Then
        assertTrue(result);
        verify(spaceRepository).findByIdAndRestaurantId(spaceId, restaurantId.toHexString());
        verify(spaceRepository).deleteById(spaceId);
    }

    @Test
    void removeSpaceFromRestaurant_WhenSpaceNotFound_ShouldReturnFalse() {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        when(spaceRepository.findByIdAndRestaurantId(spaceId, restaurantId.toHexString()))
                .thenReturn(Optional.empty());

        // When
        boolean result = restaurantService.removeSpaceFromRestaurant(restaurantId, spaceId);

        // Then
        assertFalse(result);
        verify(spaceRepository).findByIdAndRestaurantId(spaceId, restaurantId.toHexString());
        verify(spaceRepository, never()).deleteById(any());
    }

    @Test
    void getSpaceById_WhenSpaceExists_ShouldReturnSpace() {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        Space space = createTestSpace("Private Room", 2, 10);
        space.setId(spaceId);
        space.setRestaurantId(restaurantId.toHexString());

        when(spaceRepository.findByIdAndRestaurantId(spaceId, restaurantId.toHexString()))
                .thenReturn(Optional.of(space));

        // When
        Optional<Space> result = restaurantService.getSpaceById(restaurantId, spaceId);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Private Room", result.get().getName());
        assertEquals(spaceId, result.get().getId());
        verify(spaceRepository).findByIdAndRestaurantId(spaceId, restaurantId.toHexString());
    }

    @Test
    void getSpaceById_WhenSpaceNotFound_ShouldReturnEmpty() {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        when(spaceRepository.findByIdAndRestaurantId(spaceId, restaurantId.toHexString()))
                .thenReturn(Optional.empty());

        // When
        Optional<Space> result = restaurantService.getSpaceById(restaurantId, spaceId);

        // Then
        assertFalse(result.isPresent());
        verify(spaceRepository).findByIdAndRestaurantId(spaceId, restaurantId.toHexString());
    }

    @Test
    void getSpacesForRestaurant_ShouldReturnAllSpaces() {
        // Given
        ObjectId restaurantId = new ObjectId();
        Space space1 = createTestSpace("Room 1", 2, 10);
        Space space2 = createTestSpace("Room 2", 5, 20);
        space1.setRestaurantId(restaurantId.toHexString());
        space2.setRestaurantId(restaurantId.toHexString());

        when(spaceRepository.findByRestaurantId(restaurantId.toHexString()))
                .thenReturn(Arrays.asList(space1, space2));

        // When
        List<Space> result = restaurantService.getSpacesForRestaurant(restaurantId);

        // Then
        assertEquals(2, result.size());
        assertEquals("Room 1", result.get(0).getName());
        assertEquals("Room 2", result.get(1).getName());
        verify(spaceRepository).findByRestaurantId(restaurantId.toHexString());
    }

    @Test
    void spaceExistsInRestaurant_WhenSpaceExists_ShouldReturnTrue() {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        Space space = createTestSpace("Private Room", 2, 10);
        space.setId(spaceId);
        space.setRestaurantId(restaurantId.toHexString());

        when(spaceRepository.findByIdAndRestaurantId(spaceId, restaurantId.toHexString()))
                .thenReturn(Optional.of(space));

        // When
        boolean result = restaurantService.spaceExistsInRestaurant(restaurantId, spaceId);

        // Then
        assertTrue(result);
        verify(spaceRepository).findByIdAndRestaurantId(spaceId, restaurantId.toHexString());
    }

    @Test
    void spaceExistsInRestaurant_WhenSpaceNotFound_ShouldReturnFalse() {
        // Given
        ObjectId restaurantId = new ObjectId();
        UUID spaceId = UUID.randomUUID();

        when(spaceRepository.findByIdAndRestaurantId(spaceId, restaurantId.toHexString()))
                .thenReturn(Optional.empty());

        // When
        boolean result = restaurantService.spaceExistsInRestaurant(restaurantId, spaceId);

        // Then
        assertFalse(result);
        verify(spaceRepository).findByIdAndRestaurantId(spaceId, restaurantId.toHexString());
    }
}
