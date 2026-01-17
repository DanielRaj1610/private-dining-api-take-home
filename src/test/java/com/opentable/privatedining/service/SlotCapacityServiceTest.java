package com.opentable.privatedining.service;

import com.opentable.privatedining.model.SlotCapacity;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.repository.SlotCapacityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlotCapacityService Tests")
class SlotCapacityServiceTest {

    @Mock
    private SlotCapacityRepository slotCapacityRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    private SlotCapacityService slotCapacityService;

    private Space testSpace;
    private UUID spaceId;
    private LocalDate testDate;
    private String startTime;
    private String endTime;

    @BeforeEach
    void setUp() {
        slotCapacityService = new SlotCapacityService(slotCapacityRepository, mongoTemplate);

        spaceId = UUID.randomUUID();
        testDate = LocalDate.now().plusDays(7);
        startTime = "18:00";
        endTime = "19:00";

        testSpace = Space.builder()
                .id(spaceId)
                .name("Garden Room")
                .maxCapacity(20)
                .slotDurationMinutes(60)
                .build();
    }

    @Nested
    @DisplayName("tryReserveCapacity tests")
    class TryReserveCapacityTests {

        @Test
        @DisplayName("Should successfully reserve capacity when available")
        void shouldSuccessfullyReserveCapacity() {
            // Given
            int partySize = 5;
            SlotCapacity resultSlot = SlotCapacity.builder()
                    .id(SlotCapacity.generateId(spaceId, testDate, startTime))
                    .spaceId(spaceId)
                    .date(testDate)
                    .startTime(startTime)
                    .endTime(endTime)
                    .bookedCapacity(partySize)
                    .maxCapacity(20)
                    .build();

            when(mongoTemplate.findAndModify(
                    any(Query.class),
                    any(Update.class),
                    any(FindAndModifyOptions.class),
                    eq(SlotCapacity.class)))
                    .thenReturn(resultSlot);

            // When
            boolean result = slotCapacityService.tryReserveCapacity(
                    testSpace, testDate, startTime, endTime, partySize);

            // Then
            assertTrue(result);
            verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(SlotCapacity.class));
            verify(mongoTemplate).findAndModify(
                    any(Query.class),
                    any(Update.class),
                    any(FindAndModifyOptions.class),
                    eq(SlotCapacity.class));
        }

        @Test
        @DisplayName("Should fail to reserve capacity when insufficient")
        void shouldFailWhenInsufficientCapacity() {
            // Given
            int partySize = 15;

            // findAndModify returns null when capacity condition not met
            when(mongoTemplate.findAndModify(
                    any(Query.class),
                    any(Update.class),
                    any(FindAndModifyOptions.class),
                    eq(SlotCapacity.class)))
                    .thenReturn(null);

            // When
            boolean result = slotCapacityService.tryReserveCapacity(
                    testSpace, testDate, startTime, endTime, partySize);

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("Should reserve capacity exactly at limit")
        void shouldReserveCapacityAtExactLimit() {
            // Given - Space has 20 capacity, trying to reserve exactly 20
            int partySize = 20;
            SlotCapacity resultSlot = SlotCapacity.builder()
                    .bookedCapacity(partySize)
                    .maxCapacity(20)
                    .build();

            when(mongoTemplate.findAndModify(
                    any(Query.class),
                    any(Update.class),
                    any(FindAndModifyOptions.class),
                    eq(SlotCapacity.class)))
                    .thenReturn(resultSlot);

            // When
            boolean result = slotCapacityService.tryReserveCapacity(
                    testSpace, testDate, startTime, endTime, partySize);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("Should fail when single party exceeds max capacity")
        void shouldFailWhenPartySizeExceedsMaxCapacity() {
            // Given - Space has 20 capacity, trying to reserve 25
            int partySize = 25;

            when(mongoTemplate.findAndModify(
                    any(Query.class),
                    any(Update.class),
                    any(FindAndModifyOptions.class),
                    eq(SlotCapacity.class)))
                    .thenReturn(null);

            // When
            boolean result = slotCapacityService.tryReserveCapacity(
                    testSpace, testDate, startTime, endTime, partySize);

            // Then
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("releaseCapacity tests")
    class ReleaseCapacityTests {

        @Test
        @DisplayName("Should successfully release capacity")
        void shouldSuccessfullyReleaseCapacity() {
            // Given
            int partySize = 5;
            SlotCapacity resultSlot = SlotCapacity.builder()
                    .bookedCapacity(10) // Was 15, now 10 after release
                    .maxCapacity(20)
                    .build();

            when(mongoTemplate.findAndModify(
                    any(Query.class),
                    any(Update.class),
                    any(FindAndModifyOptions.class),
                    eq(SlotCapacity.class)))
                    .thenReturn(resultSlot);

            // When
            slotCapacityService.releaseCapacity(spaceId, testDate, startTime, partySize);

            // Then
            ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
            verify(mongoTemplate).findAndModify(
                    any(Query.class),
                    updateCaptor.capture(),
                    any(FindAndModifyOptions.class),
                    eq(SlotCapacity.class));

            // Verify the update decrements by partySize
            Update capturedUpdate = updateCaptor.getValue();
            assertNotNull(capturedUpdate);
        }

        @Test
        @DisplayName("Should fix negative capacity if occurs")
        void shouldFixNegativeCapacity() {
            // Given
            int partySize = 5;
            SlotCapacity resultSlot = SlotCapacity.builder()
                    .id(SlotCapacity.generateId(spaceId, testDate, startTime))
                    .bookedCapacity(-3) // Negative after release (shouldn't happen normally)
                    .maxCapacity(20)
                    .build();

            when(mongoTemplate.findAndModify(
                    any(Query.class),
                    any(Update.class),
                    any(FindAndModifyOptions.class),
                    eq(SlotCapacity.class)))
                    .thenReturn(resultSlot);

            // When
            slotCapacityService.releaseCapacity(spaceId, testDate, startTime, partySize);

            // Then - should call updateFirst to fix negative capacity
            verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(SlotCapacity.class));
        }

        @Test
        @DisplayName("Should handle release when slot doesn't exist")
        void shouldHandleReleaseWhenSlotNotExists() {
            // Given
            int partySize = 5;

            when(mongoTemplate.findAndModify(
                    any(Query.class),
                    any(Update.class),
                    any(FindAndModifyOptions.class),
                    eq(SlotCapacity.class)))
                    .thenReturn(null);

            // When - should not throw
            assertDoesNotThrow(() ->
                    slotCapacityService.releaseCapacity(spaceId, testDate, startTime, partySize));

            // Then - no fix query should be called
            verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(SlotCapacity.class));
        }
    }

    @Nested
    @DisplayName("getAvailableCapacity tests")
    class GetAvailableCapacityTests {

        @Test
        @DisplayName("Should return available capacity when slot exists")
        void shouldReturnAvailableCapacityWhenSlotExists() {
            // Given
            String slotId = SlotCapacity.generateId(spaceId, testDate, startTime);
            SlotCapacity existingSlot = SlotCapacity.builder()
                    .id(slotId)
                    .bookedCapacity(12)
                    .maxCapacity(20)
                    .build();

            when(slotCapacityRepository.findById(slotId))
                    .thenReturn(Optional.of(existingSlot));

            // When
            int result = slotCapacityService.getAvailableCapacity(spaceId, testDate, startTime, 20);

            // Then
            assertEquals(8, result); // 20 - 12 = 8
        }

        @Test
        @DisplayName("Should return max capacity when no slot exists")
        void shouldReturnMaxCapacityWhenNoSlotExists() {
            // Given
            String slotId = SlotCapacity.generateId(spaceId, testDate, startTime);
            when(slotCapacityRepository.findById(slotId)).thenReturn(Optional.empty());

            // When
            int result = slotCapacityService.getAvailableCapacity(spaceId, testDate, startTime, 20);

            // Then
            assertEquals(20, result); // Full capacity available
        }

        @Test
        @DisplayName("Should return zero when fully booked")
        void shouldReturnZeroWhenFullyBooked() {
            // Given
            String slotId = SlotCapacity.generateId(spaceId, testDate, startTime);
            SlotCapacity fullyBookedSlot = SlotCapacity.builder()
                    .id(slotId)
                    .bookedCapacity(20)
                    .maxCapacity(20)
                    .build();

            when(slotCapacityRepository.findById(slotId))
                    .thenReturn(Optional.of(fullyBookedSlot));

            // When
            int result = slotCapacityService.getAvailableCapacity(spaceId, testDate, startTime, 20);

            // Then
            assertEquals(0, result);
        }
    }

    @Nested
    @DisplayName("getBookedCapacity tests")
    class GetBookedCapacityTests {

        @Test
        @DisplayName("Should return booked capacity when slot exists")
        void shouldReturnBookedCapacityWhenSlotExists() {
            // Given
            String slotId = SlotCapacity.generateId(spaceId, testDate, startTime);
            SlotCapacity existingSlot = SlotCapacity.builder()
                    .id(slotId)
                    .bookedCapacity(15)
                    .maxCapacity(20)
                    .build();

            when(slotCapacityRepository.findById(slotId))
                    .thenReturn(Optional.of(existingSlot));

            // When
            int result = slotCapacityService.getBookedCapacity(spaceId, testDate, startTime);

            // Then
            assertEquals(15, result);
        }

        @Test
        @DisplayName("Should return zero when slot doesn't exist")
        void shouldReturnZeroWhenSlotNotExists() {
            // Given
            String slotId = SlotCapacity.generateId(spaceId, testDate, startTime);
            when(slotCapacityRepository.findById(slotId)).thenReturn(Optional.empty());

            // When
            int result = slotCapacityService.getBookedCapacity(spaceId, testDate, startTime);

            // Then
            assertEquals(0, result);
        }
    }

    @Nested
    @DisplayName("syncSlotCapacity tests")
    class SyncSlotCapacityTests {

        @Test
        @DisplayName("Should sync slot capacity with correct values")
        void shouldSyncSlotCapacity() {
            // Given
            int totalBooked = 10;
            int maxCapacity = 20;

            // When
            slotCapacityService.syncSlotCapacity(
                    spaceId, testDate, startTime, endTime, totalBooked, maxCapacity);

            // Then
            verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(SlotCapacity.class));
        }
    }

    @Nested
    @DisplayName("SlotCapacity model tests")
    class SlotCapacityModelTests {

        @Test
        @DisplayName("Should generate correct compound ID")
        void shouldGenerateCorrectCompoundId() {
            // Given
            UUID testSpaceId = UUID.fromString("9cb34a37-d514-4103-bae9-b0ed1f7c7d09");
            LocalDate date = LocalDate.of(2024, 2, 15);
            String time = "17:00";

            // When
            String id = SlotCapacity.generateId(testSpaceId, date, time);

            // Then
            assertEquals("9cb34a37-d514-4103-bae9-b0ed1f7c7d09:2024-02-15:17:00", id);
        }

        @Test
        @DisplayName("Should calculate available capacity correctly")
        void shouldCalculateAvailableCapacity() {
            // Given
            SlotCapacity slot = SlotCapacity.builder()
                    .bookedCapacity(15)
                    .maxCapacity(20)
                    .build();

            // When
            int available = slot.getAvailableCapacity();

            // Then
            assertEquals(5, available);
        }

        @Test
        @DisplayName("Should check accommodation correctly")
        void shouldCheckAccommodation() {
            // Given
            SlotCapacity slot = SlotCapacity.builder()
                    .bookedCapacity(15)
                    .maxCapacity(20)
                    .build();

            // When & Then
            assertTrue(slot.canAccommodate(5));   // 15 + 5 = 20, OK
            assertFalse(slot.canAccommodate(6));  // 15 + 6 = 21, exceeds
            assertTrue(slot.canAccommodate(1));   // 15 + 1 = 16, OK
        }
    }
}
