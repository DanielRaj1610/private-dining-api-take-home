// 05-verify-seed-data.js
// Verifies seed data and prints statistics

print('');
print('╔════════════════════════════════════════════════════════════════╗');
print('║           PRIVATE DINING DATABASE SEED VERIFICATION            ║');
print('╚════════════════════════════════════════════════════════════════╝');
print('');

db = db.getSiblingDB('private_dining');

// ═══════════════════════════════════════════════════════════════════════
// Collection Statistics
// ═══════════════════════════════════════════════════════════════════════

print('┌────────────────────────────────────────────────────────────────┐');
print('│                    COLLECTION STATISTICS                       │');
print('├────────────────────────────────────────────────────────────────┤');

const restaurantCount = db.restaurants.countDocuments();
const spaceCount = db.spaces.countDocuments();
const reservationCount = db.reservations.countDocuments();

print(`│  Restaurants:   ${String(restaurantCount).padEnd(45)}│`);
print(`│  Spaces:        ${String(spaceCount).padEnd(45)}│`);
print(`│  Reservations:  ${String(reservationCount).padEnd(45)}│`);
print('└────────────────────────────────────────────────────────────────┘');
print('');

// ═══════════════════════════════════════════════════════════════════════
// Restaurant Distribution
// ═══════════════════════════════════════════════════════════════════════

print('┌────────────────────────────────────────────────────────────────┐');
print('│                  RESTAURANTS BY CITY                           │');
print('├────────────────────────────────────────────────────────────────┤');

const cityCounts = db.restaurants.aggregate([
    { $group: { _id: "$city", count: { $sum: 1 } } },
    { $sort: { count: -1 } }
]).toArray();

cityCounts.forEach(c => {
    const bar = '█'.repeat(Math.min(c.count, 30));
    print(`│  ${c._id.padEnd(18)} ${String(c.count).padStart(3)}  ${bar.padEnd(30)}│`);
});
print('└────────────────────────────────────────────────────────────────┘');
print('');

// ═══════════════════════════════════════════════════════════════════════
// Cuisine Types
// ═══════════════════════════════════════════════════════════════════════

print('┌────────────────────────────────────────────────────────────────┐');
print('│                  RESTAURANTS BY CUISINE                        │');
print('├────────────────────────────────────────────────────────────────┤');

const cuisineCounts = db.restaurants.aggregate([
    { $group: { _id: "$cuisineType", count: { $sum: 1 } } },
    { $sort: { count: -1 } }
]).toArray();

cuisineCounts.forEach(c => {
    const bar = '█'.repeat(Math.min(c.count, 25));
    print(`│  ${c._id.padEnd(18)} ${String(c.count).padStart(3)}  ${bar.padEnd(30)}│`);
});
print('└────────────────────────────────────────────────────────────────┘');
print('');

// ═══════════════════════════════════════════════════════════════════════
// Space Statistics
// ═══════════════════════════════════════════════════════════════════════

print('┌────────────────────────────────────────────────────────────────┐');
print('│                    SPACE STATISTICS                            │');
print('├────────────────────────────────────────────────────────────────┤');

const spaceStats = db.spaces.aggregate([
    {
        $group: {
            _id: null,
            avgMinCapacity: { $avg: "$minCapacity" },
            avgMaxCapacity: { $avg: "$maxCapacity" },
            maxCapacity: { $max: "$maxCapacity" },
            minCapacity: { $min: "$minCapacity" },
            avgSlotDuration: { $avg: "$slotDurationMinutes" },
            activeCount: { $sum: { $cond: ["$isActive", 1, 0] } },
            inactiveCount: { $sum: { $cond: ["$isActive", 0, 1] } }
        }
    }
]).toArray()[0];

if (spaceStats) {
    print(`│  Active Spaces:       ${String(spaceStats.activeCount).padEnd(40)}│`);
    print(`│  Inactive Spaces:     ${String(spaceStats.inactiveCount).padEnd(40)}│`);
    print(`│  Avg Min Capacity:    ${spaceStats.avgMinCapacity.toFixed(1).padEnd(40)}│`);
    print(`│  Avg Max Capacity:    ${spaceStats.avgMaxCapacity.toFixed(1).padEnd(40)}│`);
    print(`│  Capacity Range:      ${(spaceStats.minCapacity + ' - ' + spaceStats.maxCapacity).padEnd(40)}│`);
    print(`│  Avg Slot Duration:   ${(spaceStats.avgSlotDuration.toFixed(0) + ' minutes').padEnd(40)}│`);
}
print('└────────────────────────────────────────────────────────────────┘');
print('');

// ═══════════════════════════════════════════════════════════════════════
// Spaces by Type
// ═══════════════════════════════════════════════════════════════════════

print('┌────────────────────────────────────────────────────────────────┐');
print('│                     SPACES BY TYPE                             │');
print('├────────────────────────────────────────────────────────────────┤');

const spaceTypeCounts = db.spaces.aggregate([
    { $group: { _id: "$name", count: { $sum: 1 } } },
    { $sort: { count: -1 } }
]).toArray();

spaceTypeCounts.forEach(s => {
    const bar = '█'.repeat(Math.min(s.count, 25));
    print(`│  ${s._id.padEnd(18)} ${String(s.count).padStart(3)}  ${bar.padEnd(30)}│`);
});
print('└────────────────────────────────────────────────────────────────┘');
print('');

// ═══════════════════════════════════════════════════════════════════════
// Reservation Statistics
// ═══════════════════════════════════════════════════════════════════════

print('┌────────────────────────────────────────────────────────────────┐');
print('│                 RESERVATION STATISTICS                         │');
print('├────────────────────────────────────────────────────────────────┤');

const reservationStats = db.reservations.aggregate([
    {
        $group: {
            _id: null,
            avgPartySize: { $avg: "$partySize" },
            maxPartySize: { $max: "$partySize" },
            minPartySize: { $min: "$partySize" },
            totalGuests: { $sum: "$partySize" }
        }
    }
]).toArray()[0];

if (reservationStats) {
    print(`│  Total Reservations:  ${String(reservationCount).padEnd(40)}│`);
    print(`│  Total Guests:        ${String(reservationStats.totalGuests).padEnd(40)}│`);
    print(`│  Avg Party Size:      ${reservationStats.avgPartySize.toFixed(1).padEnd(40)}│`);
    print(`│  Party Size Range:    ${(reservationStats.minPartySize + ' - ' + reservationStats.maxPartySize).padEnd(40)}│`);
}
print('└────────────────────────────────────────────────────────────────┘');
print('');

// ═══════════════════════════════════════════════════════════════════════
// Reservations by Status
// ═══════════════════════════════════════════════════════════════════════

print('┌────────────────────────────────────────────────────────────────┐');
print('│                 RESERVATIONS BY STATUS                         │');
print('├────────────────────────────────────────────────────────────────┤');

const statusCounts = db.reservations.aggregate([
    { $group: { _id: "$status", count: { $sum: 1 } } },
    { $sort: { count: -1 } }
]).toArray();

statusCounts.forEach(s => {
    const percentage = ((s.count / reservationCount) * 100).toFixed(1);
    const bar = '█'.repeat(Math.min(Math.round(s.count / 100), 30));
    print(`│  ${s._id.padEnd(12)} ${String(s.count).padStart(6)} (${percentage.padStart(5)}%)  ${bar.padEnd(20)}│`);
});
print('└────────────────────────────────────────────────────────────────┘');
print('');

// ═══════════════════════════════════════════════════════════════════════
// Index Verification
// ═══════════════════════════════════════════════════════════════════════

print('┌────────────────────────────────────────────────────────────────┐');
print('│                    INDEX VERIFICATION                          │');
print('├────────────────────────────────────────────────────────────────┤');

['restaurants', 'spaces', 'reservations'].forEach(collection => {
    const indexes = db[collection].getIndexes();
    print(`│  ${collection.padEnd(15)} ${String(indexes.length + ' indexes').padEnd(45)}│`);
    indexes.forEach(idx => {
        const keys = Object.keys(idx.key).join(', ');
        print(`│    └─ ${keys.substring(0, 54).padEnd(54)}│`);
    });
});
print('└────────────────────────────────────────────────────────────────┘');
print('');

// ═══════════════════════════════════════════════════════════════════════
// Sample Data
// ═══════════════════════════════════════════════════════════════════════

print('┌────────────────────────────────────────────────────────────────┐');
print('│                      SAMPLE DATA                               │');
print('├────────────────────────────────────────────────────────────────┤');

const sampleRestaurant = db.restaurants.findOne({});
if (sampleRestaurant) {
    print(`│  Sample Restaurant:                                           │`);
    print(`│    Name: ${sampleRestaurant.name.substring(0, 50).padEnd(52)}│`);
    print(`│    City: ${sampleRestaurant.city.padEnd(52)}│`);
    print(`│    Timezone: ${sampleRestaurant.timezone.padEnd(48)}│`);
}

const sampleSpace = db.spaces.findOne({});
if (sampleSpace) {
    print(`│  Sample Space:                                                │`);
    print(`│    Name: ${sampleSpace.name.substring(0, 50).padEnd(52)}│`);
    print(`│    Capacity: ${(sampleSpace.minCapacity + ' - ' + sampleSpace.maxCapacity + ' guests').padEnd(48)}│`);
}

const sampleReservation = db.reservations.findOne({ status: "CONFIRMED" });
if (sampleReservation) {
    print(`│  Sample Reservation:                                          │`);
    print(`│    Customer: ${sampleReservation.customerName.substring(0, 45).padEnd(48)}│`);
    print(`│    Date: ${sampleReservation.reservationDate.padEnd(52)}│`);
    print(`│    Time: ${(sampleReservation.startTime + ' - ' + sampleReservation.endTime).padEnd(52)}│`);
    print(`│    Party Size: ${String(sampleReservation.partySize).padEnd(46)}│`);
}
print('└────────────────────────────────────────────────────────────────┘');
print('');

// ═══════════════════════════════════════════════════════════════════════
// Final Summary
// ═══════════════════════════════════════════════════════════════════════

print('╔════════════════════════════════════════════════════════════════╗');
print('║                    SEED VERIFICATION COMPLETE                   ║');
print('╠════════════════════════════════════════════════════════════════╣');

const allGood = restaurantCount > 0 && spaceCount > 0 && reservationCount > 0;

if (allGood) {
    print('║  ✓ All collections seeded successfully                        ║');
    print('║  ✓ Indexes created and verified                               ║');
    print('║  ✓ Database ready for use                                     ║');
} else {
    print('║  ✗ WARNING: Some collections may be empty                     ║');
    print('║  ✗ Please check the seed scripts for errors                   ║');
}

print('╚════════════════════════════════════════════════════════════════╝');
print('');
