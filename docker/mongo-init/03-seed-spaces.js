// 03-seed-spaces.js
// Seeds the spaces collection with private dining rooms for each restaurant

print('=== Seeding spaces collection ===');

db = db.getSiblingDB('private_dining');

// Space types with configurations
const spaceTypes = [
    {
        name: "Intimate Room",
        description: "Perfect for romantic dinners and small celebrations",
        minCapacity: 2,
        maxCapacity: 6,
        slotDuration: 90,
        bufferMinutes: 15
    },
    {
        name: "Private Alcove",
        description: "A cozy corner for intimate gatherings",
        minCapacity: 4,
        maxCapacity: 8,
        slotDuration: 90,
        bufferMinutes: 15
    },
    {
        name: "Garden Room",
        description: "Elegant space with garden views for medium-sized groups",
        minCapacity: 6,
        maxCapacity: 16,
        slotDuration: 120,
        bufferMinutes: 20
    },
    {
        name: "Wine Cellar",
        description: "Atmospheric wine cellar setting for special occasions",
        minCapacity: 8,
        maxCapacity: 20,
        slotDuration: 120,
        bufferMinutes: 20
    },
    {
        name: "Chef's Table",
        description: "Premium experience with direct kitchen views",
        minCapacity: 4,
        maxCapacity: 12,
        slotDuration: 150,
        bufferMinutes: 30
    },
    {
        name: "Grand Ballroom",
        description: "Large venue for corporate events and celebrations",
        minCapacity: 20,
        maxCapacity: 80,
        slotDuration: 180,
        bufferMinutes: 30
    },
    {
        name: "Terrace Suite",
        description: "Outdoor covered terrace with city views",
        minCapacity: 10,
        maxCapacity: 30,
        slotDuration: 120,
        bufferMinutes: 20
    },
    {
        name: "Library Room",
        description: "Classic setting surrounded by vintage books",
        minCapacity: 6,
        maxCapacity: 14,
        slotDuration: 90,
        bufferMinutes: 15
    }
];

function generateUUID() {
    // Generate a UUID string
    const uuidString = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
    // Return as proper MongoDB UUID binary type
    return UUID(uuidString);
}

function randomBetween(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function shuffleArray(array) {
    const shuffled = [...array];
    for (let i = shuffled.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    return shuffled;
}

// Get all restaurants
const restaurants = db.restaurants.find({}).toArray();
print(`Found ${restaurants.length} restaurants`);

const spaces = [];
const now = new Date();

// Create 2-5 spaces per restaurant
restaurants.forEach((restaurant, index) => {
    const numSpaces = randomBetween(2, 5);
    const selectedTypes = shuffleArray(spaceTypes).slice(0, numSpaces);

    selectedTypes.forEach((spaceType, spaceIndex) => {
        // Add some variation to capacity
        const capacityVariation = randomBetween(-2, 2);
        const minCap = Math.max(1, spaceType.minCapacity + capacityVariation);
        const maxCap = Math.max(minCap + 2, spaceType.maxCapacity + capacityVariation);

        spaces.push({
            _id: generateUUID(),
            restaurantId: restaurant._id.toString(),
            name: spaceType.name,
            description: `${spaceType.description} at ${restaurant.name}`,
            minCapacity: minCap,
            maxCapacity: maxCap,
            slotDurationMinutes: spaceType.slotDuration,
            bufferMinutes: spaceType.bufferMinutes,
            isActive: Math.random() > 0.05, // 95% are active
            amenities: generateAmenities(spaceType.name),
            createdAt: now,
            updatedAt: now
        });
    });
});

function generateAmenities(spaceTypeName) {
    const baseAmenities = ["Private Entrance", "Dedicated Server"];
    const optionalAmenities = [
        "AV Equipment",
        "Projector",
        "Sound System",
        "Fireplace",
        "Private Bar",
        "Custom Menu",
        "Microphone",
        "Whiteboard",
        "Video Conferencing"
    ];

    // Add type-specific amenities
    if (spaceTypeName.includes("Wine")) {
        baseAmenities.push("Wine Selection", "Sommelier Service");
    }
    if (spaceTypeName.includes("Chef")) {
        baseAmenities.push("Kitchen View", "Chef Interaction");
    }
    if (spaceTypeName.includes("Terrace") || spaceTypeName.includes("Garden")) {
        baseAmenities.push("Outdoor Heating", "Weather Protection");
    }
    if (spaceTypeName.includes("Ballroom") || spaceTypeName.includes("Grand")) {
        baseAmenities.push("Dance Floor", "Stage Area");
    }

    // Add 1-3 random optional amenities
    const numOptional = randomBetween(1, 3);
    const selectedOptional = shuffleArray(optionalAmenities).slice(0, numOptional);

    return [...baseAmenities, ...selectedOptional];
}

// Insert all spaces
db.spaces.insertMany(spaces);

print(`Inserted ${spaces.length} spaces for ${restaurants.length} restaurants`);
print('Average spaces per restaurant: ' + (spaces.length / restaurants.length).toFixed(1));
print('=== Space seeding complete ===');
