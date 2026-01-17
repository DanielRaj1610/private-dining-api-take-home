// 02-seed-restaurants.js
// Seeds the restaurants collection with sample data

print('=== Seeding restaurants collection ===');

db = db.getSiblingDB('private_dining');

// Helper function to generate ObjectId
function generateObjectId() {
    return new ObjectId();
}

// City data with timezones
const cities = [
    { city: "New York", state: "NY", timezone: "America/New_York" },
    { city: "Los Angeles", state: "CA", timezone: "America/Los_Angeles" },
    { city: "Chicago", state: "IL", timezone: "America/Chicago" },
    { city: "San Francisco", state: "CA", timezone: "America/Los_Angeles" },
    { city: "Miami", state: "FL", timezone: "America/New_York" },
    { city: "Seattle", state: "WA", timezone: "America/Los_Angeles" },
    { city: "Boston", state: "MA", timezone: "America/New_York" },
    { city: "Denver", state: "CO", timezone: "America/Denver" },
    { city: "Austin", state: "TX", timezone: "America/Chicago" },
    { city: "Nashville", state: "TN", timezone: "America/Chicago" }
];

// Restaurant name components
const prefixes = ["The", "Le", "La", "Casa", "Maison", "", "", ""];
const mainNames = ["Golden", "Garden", "Harbor", "River", "Mountain", "Sunset", "Oak", "Maple",
                   "Blue", "Red", "Green", "Silver", "Crystal", "Royal", "Grand", "Noble"];
const suffixes = ["Kitchen", "Bistro", "Grill", "House", "Table", "Room", "Restaurant", "Eatery",
                  "Tavern", "Cafe", "Brasserie", "Trattoria"];

const cuisineTypes = ["American", "Italian", "French", "Japanese", "Mediterranean",
                      "Steakhouse", "Seafood", "Farm-to-Table", "Contemporary", "Asian Fusion"];

// Operating hours patterns
const operatingPatterns = {
    standard: [
        { dayOfWeek: 0, openTime: "10:00", closeTime: "21:00", isClosed: false }, // Sunday
        { dayOfWeek: 1, openTime: "11:00", closeTime: "22:00", isClosed: false }, // Monday
        { dayOfWeek: 2, openTime: "11:00", closeTime: "22:00", isClosed: false }, // Tuesday
        { dayOfWeek: 3, openTime: "11:00", closeTime: "22:00", isClosed: false }, // Wednesday
        { dayOfWeek: 4, openTime: "11:00", closeTime: "22:00", isClosed: false }, // Thursday
        { dayOfWeek: 5, openTime: "11:00", closeTime: "23:00", isClosed: false }, // Friday
        { dayOfWeek: 6, openTime: "11:00", closeTime: "23:00", isClosed: false }  // Saturday
    ],
    fineDining: [
        { dayOfWeek: 0, openTime: null, closeTime: null, isClosed: true },        // Sunday closed
        { dayOfWeek: 1, openTime: null, closeTime: null, isClosed: true },        // Monday closed
        { dayOfWeek: 2, openTime: "17:00", closeTime: "23:00", isClosed: false }, // Tuesday
        { dayOfWeek: 3, openTime: "17:00", closeTime: "23:00", isClosed: false }, // Wednesday
        { dayOfWeek: 4, openTime: "17:00", closeTime: "23:00", isClosed: false }, // Thursday
        { dayOfWeek: 5, openTime: "17:00", closeTime: "23:00", isClosed: false }, // Friday
        { dayOfWeek: 6, openTime: "17:00", closeTime: "23:00", isClosed: false }  // Saturday
    ],
    allDay: [
        { dayOfWeek: 0, openTime: "09:00", closeTime: "23:00", isClosed: false },
        { dayOfWeek: 1, openTime: "09:00", closeTime: "23:00", isClosed: false },
        { dayOfWeek: 2, openTime: "09:00", closeTime: "23:00", isClosed: false },
        { dayOfWeek: 3, openTime: "09:00", closeTime: "23:00", isClosed: false },
        { dayOfWeek: 4, openTime: "09:00", closeTime: "23:00", isClosed: false },
        { dayOfWeek: 5, openTime: "09:00", closeTime: "23:00", isClosed: false },
        { dayOfWeek: 6, openTime: "09:00", closeTime: "23:00", isClosed: false }
    ],
    brunchFocus: [
        { dayOfWeek: 0, openTime: "09:00", closeTime: "22:00", isClosed: false }, // Sunday brunch
        { dayOfWeek: 1, openTime: "11:00", closeTime: "22:00", isClosed: false },
        { dayOfWeek: 2, openTime: "11:00", closeTime: "22:00", isClosed: false },
        { dayOfWeek: 3, openTime: "11:00", closeTime: "22:00", isClosed: false },
        { dayOfWeek: 4, openTime: "11:00", closeTime: "22:00", isClosed: false },
        { dayOfWeek: 5, openTime: "11:00", closeTime: "23:00", isClosed: false },
        { dayOfWeek: 6, openTime: "09:00", closeTime: "23:00", isClosed: false }  // Saturday brunch
    ]
};

const patternKeys = Object.keys(operatingPatterns);

function randomElement(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function randomBetween(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function generateRestaurantName() {
    const prefix = randomElement(prefixes);
    const main = randomElement(mainNames);
    const suffix = randomElement(suffixes);
    return prefix ? `${prefix} ${main} ${suffix}` : `${main} ${suffix}`;
}

function generatePhone(areaCode) {
    return `(${areaCode}) ${randomBetween(200, 999)}-${randomBetween(1000, 9999)}`;
}

// Generate restaurants
const restaurants = [];
const now = new Date();

// Generate 50 procedural restaurants
for (let i = 0; i < 50; i++) {
    const cityData = randomElement(cities);
    const areaCode = randomBetween(200, 999);
    const name = generateRestaurantName();
    const pattern = randomElement(patternKeys);

    restaurants.push({
        _id: generateObjectId(),
        name: name,
        address: `${randomBetween(100, 9999)} ${randomElement(["Main", "Oak", "Park", "Broadway", "Market", "First", "Second"])} ${randomElement(["Street", "Avenue", "Boulevard", "Road"])}`,
        city: cityData.city,
        state: cityData.state,
        zipCode: String(randomBetween(10000, 99999)),
        phone: generatePhone(areaCode),
        email: name.toLowerCase().replace(/[^a-z0-9]/g, '') + "@restaurant.com",
        cuisineType: randomElement(cuisineTypes),
        timezone: cityData.timezone,
        operatingHours: operatingPatterns[pattern],
        isActive: true,
        createdAt: now,
        updatedAt: now
    });
}

// Add famous restaurants
const famousRestaurants = [
    { name: "Per Se", city: "New York", state: "NY", cuisine: "French", pattern: "fineDining" },
    { name: "The French Laundry", city: "San Francisco", state: "CA", cuisine: "French", pattern: "fineDining" },
    { name: "Alinea", city: "Chicago", state: "IL", cuisine: "Contemporary", pattern: "fineDining" },
    { name: "Eleven Madison Park", city: "New York", state: "NY", cuisine: "Contemporary", pattern: "fineDining" },
    { name: "Le Bernardin", city: "New York", state: "NY", cuisine: "Seafood", pattern: "fineDining" },
    { name: "Canlis", city: "Seattle", state: "WA", cuisine: "Contemporary", pattern: "fineDining" },
    { name: "Commander's Palace", city: "Miami", state: "FL", cuisine: "American", pattern: "brunchFocus" },
    { name: "Frasca Food and Wine", city: "Denver", state: "CO", cuisine: "Italian", pattern: "standard" },
    { name: "Franklin Barbecue", city: "Austin", state: "TX", cuisine: "American", pattern: "allDay" },
    { name: "The Catbird Seat", city: "Nashville", state: "TN", cuisine: "Contemporary", pattern: "fineDining" }
];

famousRestaurants.forEach(fr => {
    const cityData = cities.find(c => c.city === fr.city);
    restaurants.push({
        _id: generateObjectId(),
        name: fr.name,
        address: `${randomBetween(1, 500)} ${randomElement(["Main", "Columbus", "Madison", "Fifth"])} Avenue`,
        city: fr.city,
        state: fr.state,
        zipCode: String(randomBetween(10000, 99999)),
        phone: generatePhone(randomBetween(200, 999)),
        email: fr.name.toLowerCase().replace(/[^a-z0-9]/g, '') + "@restaurant.com",
        cuisineType: fr.cuisine,
        timezone: cityData.timezone,
        operatingHours: operatingPatterns[fr.pattern],
        isActive: true,
        createdAt: now,
        updatedAt: now
    });
});

// Insert all restaurants
db.restaurants.insertMany(restaurants);

print(`Inserted ${restaurants.length} restaurants`);
print('=== Restaurant seeding complete ===');
