// 01-create-indexes.js
// Creates indexes for the private_dining database

print('=== Creating indexes for private_dining database ===');

// Switch to the private_dining database
db = db.getSiblingDB('private_dining');

// Create indexes for restaurants collection
print('Creating indexes for restaurants collection...');
db.restaurants.createIndex({ "name": 1 });
db.restaurants.createIndex({ "city": 1 });
db.restaurants.createIndex({ "isActive": 1 });
db.restaurants.createIndex({ "cuisineType": 1 });

// Create indexes for spaces collection
print('Creating indexes for spaces collection...');
db.spaces.createIndex({ "restaurantId": 1 });
db.spaces.createIndex({ "restaurantId": 1, "isActive": 1 });
db.spaces.createIndex({ "maxCapacity": 1 });
db.spaces.createIndex(
    { "restaurantId": 1, "minCapacity": 1, "maxCapacity": 1, "isActive": 1 },
    { name: "idx_space_capacity_search" }
);

// Create indexes for reservations collection
print('Creating indexes for reservations collection...');
db.reservations.createIndex(
    { "spaceId": 1, "reservationDate": 1, "status": 1 },
    { name: "idx_reservation_space_date_status" }
);
db.reservations.createIndex(
    { "spaceId": 1, "reservationDate": 1, "startTime": 1, "endTime": 1 },
    { name: "idx_reservation_availability" }
);
db.reservations.createIndex(
    { "restaurantId": 1, "reservationDate": 1 },
    { name: "idx_reservation_restaurant_date" }
);
db.reservations.createIndex({ "customerEmail": 1 });
db.reservations.createIndex({ "status": 1 });
db.reservations.createIndex({ "reservationDate": 1 });

print('=== Index creation complete ===');
