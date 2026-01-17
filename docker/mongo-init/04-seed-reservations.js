// 04-seed-reservations.js
// Seeds the reservations collection with sample booking data

print('=== Seeding reservations collection ===');

db = db.getSiblingDB('private_dining');

// Customer name components
const firstNames = [
    "James", "Mary", "John", "Patricia", "Robert", "Jennifer", "Michael", "Linda",
    "William", "Elizabeth", "David", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
    "Thomas", "Sarah", "Charles", "Karen", "Christopher", "Lisa", "Daniel", "Nancy",
    "Matthew", "Betty", "Anthony", "Margaret", "Mark", "Sandra", "Donald", "Ashley",
    "Steven", "Kimberly", "Paul", "Emily", "Andrew", "Donna", "Joshua", "Michelle"
];

const lastNames = [
    "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
    "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
    "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson",
    "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson", "Walker",
    "Young", "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores"
];

const specialRequests = [
    "Anniversary celebration - please prepare a special dessert",
    "Birthday party - need a cake brought out at 8pm",
    "Business dinner - quiet area preferred",
    "Vegetarian guests - please have plant-based options ready",
    "Wine pairing requested with dinner",
    "One guest has nut allergy",
    "Proposing tonight - need help with setup",
    "Client dinner - bill to company card",
    "Celebrating promotion",
    "Retirement party for colleague",
    "First wedding anniversary",
    "Family reunion dinner",
    "High chair needed for toddler",
    "Guest of honor arriving at 7:30pm",
    "Need parking validation for 4 cars",
    null, null, null, null, null // ~50% have no special requests
];

const reservationStatuses = ["CONFIRMED", "CONFIRMED", "CONFIRMED", "CONFIRMED", "CANCELLED"];

function randomElement(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function randomBetween(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function generateEmail(firstName, lastName) {
    const domains = ["gmail.com", "yahoo.com", "outlook.com", "company.com", "work.org"];
    const formats = [
        () => `${firstName.toLowerCase()}.${lastName.toLowerCase()}@${randomElement(domains)}`,
        () => `${firstName.toLowerCase()}${lastName.toLowerCase()}@${randomElement(domains)}`,
        () => `${firstName.toLowerCase()[0]}${lastName.toLowerCase()}@${randomElement(domains)}`,
        () => `${lastName.toLowerCase()}.${firstName.toLowerCase()}@${randomElement(domains)}`
    ];
    return randomElement(formats)();
}

function generatePhone() {
    const areaCode = randomBetween(200, 999);
    const exchange = randomBetween(200, 999);
    const number = randomBetween(1000, 9999);
    return `+1-${areaCode}-${exchange}-${number}`;
}

function addMinutesToTime(timeStr, minutes) {
    const [hours, mins] = timeStr.split(':').map(Number);
    const totalMinutes = hours * 60 + mins + minutes;
    const newHours = Math.floor(totalMinutes / 60) % 24;
    const newMins = totalMinutes % 60;
    return `${String(newHours).padStart(2, '0')}:${String(newMins).padStart(2, '0')}`;
}

function generateValidStartTime(operatingHours, slotDuration) {
    // Find operating hours for a valid day (not closed)
    const validDays = operatingHours.filter(oh => !oh.isClosed);
    if (validDays.length === 0) return null;

    const selectedDay = randomElement(validDays);
    if (!selectedDay.openTime || !selectedDay.closeTime) return null;

    const [openHour, openMin] = selectedDay.openTime.split(':').map(Number);
    const [closeHour, closeMin] = selectedDay.closeTime.split(':').map(Number);

    const openMinutes = openHour * 60 + openMin;
    const closeMinutes = closeHour * 60 + closeMin;

    // Latest possible start time (must end before close)
    const latestStart = closeMinutes - slotDuration;
    if (latestStart <= openMinutes) return null;

    // Round to nearest slot boundary (e.g., every 30 minutes)
    const slotBoundary = 30;
    const startOptions = [];
    for (let m = openMinutes; m <= latestStart; m += slotBoundary) {
        startOptions.push(m);
    }

    if (startOptions.length === 0) return null;

    const selectedMinutes = randomElement(startOptions);
    const hours = Math.floor(selectedMinutes / 60);
    const mins = selectedMinutes % 60;

    return {
        dayOfWeek: selectedDay.dayOfWeek,
        startTime: `${String(hours).padStart(2, '0')}:${String(mins).padStart(2, '0')}`
    };
}

// Get all active spaces with their restaurant data
const spaces = db.spaces.find({ isActive: true }).toArray();
print(`Found ${spaces.length} active spaces`);

// Create a map of restaurants for quick lookup
const restaurantMap = {};
db.restaurants.find({}).forEach(r => {
    restaurantMap[r._id.toString()] = r;
});

const reservations = [];
const now = new Date();

// Generate reservations for the next 90 days and past 30 days
const startDate = new Date(now);
startDate.setDate(startDate.getDate() - 30);

const endDate = new Date(now);
endDate.setDate(endDate.getDate() + 90);

// For each space, generate 30-100 reservations
spaces.forEach((space, spaceIndex) => {
    const restaurant = restaurantMap[space.restaurantId];
    if (!restaurant || !restaurant.operatingHours) {
        print(`Skipping space ${space.name} - no restaurant or operating hours`);
        return;
    }

    const numReservations = randomBetween(30, 100);

    for (let i = 0; i < numReservations; i++) {
        // Generate a random date
        const daysOffset = randomBetween(-30, 90);
        const reservationDate = new Date(now);
        reservationDate.setDate(reservationDate.getDate() + daysOffset);

        // Get day of week (0=Sunday)
        const dayOfWeek = reservationDate.getDay();

        // Find operating hours for this day
        const dayHours = restaurant.operatingHours.find(oh => oh.dayOfWeek === dayOfWeek);
        if (!dayHours || dayHours.isClosed) continue;

        // Generate valid start time
        const timeInfo = generateValidStartTime(restaurant.operatingHours, space.slotDurationMinutes);
        if (!timeInfo) continue;

        const startTime = timeInfo.startTime;
        const endTime = addMinutesToTime(startTime, space.slotDurationMinutes);

        // Generate party size within space capacity
        const partySize = randomBetween(space.minCapacity, space.maxCapacity);

        // Generate customer info
        const firstName = randomElement(firstNames);
        const lastName = randomElement(lastNames);

        // Determine status - past reservations more likely to be completed/cancelled
        let status = randomElement(reservationStatuses);
        if (daysOffset < 0) {
            // Past reservations
            status = Math.random() < 0.85 ? "COMPLETED" : "CANCELLED";
        } else if (daysOffset === 0) {
            status = "CONFIRMED";
        }

        const reservationDateStr = reservationDate.toISOString().split('T')[0];

        reservations.push({
            _id: new ObjectId(),
            restaurantId: restaurant._id,
            spaceId: space._id,
            reservationDate: reservationDateStr,
            startTime: startTime,
            endTime: endTime,
            partySize: partySize,
            customerName: `${firstName} ${lastName}`,
            customerEmail: generateEmail(firstName, lastName),
            customerPhone: generatePhone(),
            status: status,
            specialRequests: randomElement(specialRequests),
            cancellationReason: status === "CANCELLED" ?
                randomElement([
                    "Schedule conflict",
                    "Changed plans",
                    "Found different venue",
                    "Emergency",
                    "Weather concerns"
                ]) : null,
            cancelledAt: status === "CANCELLED" ?
                new Date(reservationDate.getTime() - randomBetween(1, 7) * 24 * 60 * 60 * 1000) : null,
            version: 0,
            createdAt: new Date(reservationDate.getTime() - randomBetween(1, 30) * 24 * 60 * 60 * 1000),
            updatedAt: now
        });
    }
});

// Insert in batches to avoid memory issues
const batchSize = 1000;
let insertedCount = 0;

for (let i = 0; i < reservations.length; i += batchSize) {
    const batch = reservations.slice(i, Math.min(i + batchSize, reservations.length));
    db.reservations.insertMany(batch);
    insertedCount += batch.length;
    print(`Inserted batch: ${insertedCount}/${reservations.length} reservations`);
}

print(`Total inserted: ${reservations.length} reservations`);
print('=== Reservation seeding complete ===');
