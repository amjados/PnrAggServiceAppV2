// MongoDB Initialization Script for PNR Database

db = db.getSiblingDB('pnr_db');

// Create trips collection
db.createCollection('trips');

// Insert sample trip data
db.trips.insertMany([
    {
        "_id": "GHTW42",
        "bookingReference": "GHTW42",
        "cabinClass": "ECONOMY",
        "passengers": [
            {
                "firstName": "James",
                "middleName": "Morgan",
                "lastName": "McGill",
                "passengerNumber": 1,
                "customerId": null,
                "seat": "32D"
            },
            {
                "firstName": "Charles",
                "lastName": "McGill",
                "passengerNumber": 2,
                "customerId": "1216",
                "seat": "31D"
            }
        ],
        "flights": [
            {
                "flightNumber": "EK231",
                "departureAirport": "DXB",
                "departureTimeStamp": "2025-11-11T02:25:00+00:00",
                "arrivalAirport": "IAD",
                "arrivalTimeStamp": "2025-11-11T08:10:00+00:00"
            }
        ]
    },
    {
        "_id": "ABC123",
        "bookingReference": "ABC123",
        "cabinClass": "BUSINESS",
        "passengers": [
            {
                "firstName": "John",
                "middleName": "Robert",
                "lastName": "Smith",
                "passengerNumber": 1,
                "customerId": "5678",
                "seat": "2A"
            }
        ],
        "flights": [
            {
                "flightNumber": "BA456",
                "departureAirport": "LHR",
                "departureTimeStamp": "2025-12-01T14:30:00+00:00",
                "arrivalAirport": "JFK",
                "arrivalTimeStamp": "2025-12-01T17:45:00+00:00"
            }
        ]
    },
    {
        "_id": "XYZ789",
        "bookingReference": "XYZ789",
        "cabinClass": "FIRST",
        "passengers": [
            {
                "firstName": "Emma",
                "lastName": "Johnson",
                "passengerNumber": 1,
                "customerId": "9012",
                "seat": "1A"
            },
            {
                "firstName": "Michael",
                "middleName": "David",
                "lastName": "Johnson",
                "passengerNumber": 2,
                "customerId": "9013",
                "seat": "1B"
            }
        ],
        "flights": [
            {
                "flightNumber": "QF12",
                "departureAirport": "SYD",
                "departureTimeStamp": "2025-12-05T20:00:00+00:00",
                "arrivalAirport": "LAX",
                "arrivalTimeStamp": "2025-12-05T14:30:00+00:00"
            }
        ]
    },
    {
        "_id": "DEF456",
        "bookingReference": "DEF456",
        "cabinClass": "ECONOMY",
        "passengers": [
            {
                "firstName": "Sarah",
                "middleName": "Marie",
                "lastName": "Williams",
                "passengerNumber": 1,
                "customerId": null,
                "seat": "28C"
            },
            {
                "firstName": "Tom",
                "lastName": "Williams",
                "passengerNumber": 2,
                "customerId": "3456",
                "seat": "28D"
            },
            {
                "firstName": "Lisa",
                "lastName": "Williams",
                "passengerNumber": 3,
                "customerId": "3457",
                "seat": "28E"
            }
        ],
        "flights": [
            {
                "flightNumber": "LH401",
                "departureAirport": "FRA",
                "departureTimeStamp": "2025-12-10T10:15:00+00:00",
                "arrivalAirport": "ORD",
                "arrivalTimeStamp": "2025-12-10T13:45:00+00:00"
            }
        ]
    },
    {
        "_id": "PQR999",
        "bookingReference": "PQR999",
        "cabinClass": "PREMIUM_ECONOMY",
        "passengers": [
            {
                "firstName": "David",
                "lastName": "Brown",
                "passengerNumber": 1,
                "customerId": "7890",
                "seat": "15F"
            }
        ],
        "flights": [
            {
                "flightNumber": "SQ237",
                "departureAirport": "SIN",
                "departureTimeStamp": "2025-12-15T23:45:00+00:00",
                "arrivalAirport": "SFO",
                "arrivalTimeStamp": "2025-12-15T20:30:00+00:00"
            },
            {
                "flightNumber": "SQ238",
                "departureAirport": "SFO",
                "departureTimeStamp": "2025-12-20T01:00:00+00:00",
                "arrivalAirport": "SIN",
                "arrivalTimeStamp": "2025-12-21T08:15:00+00:00"
            }
        ]
    },
    {
        "_id": "GHR001",
        "bookingReference": "GHR001",
        "cabinClass": "ECONOMY",
        "passengers": [
            {
                "firstName": "Alice",
                "middleName": "Jane",
                "lastName": "Cooper",
                "passengerNumber": 1,
                "customerId": "1099",
                "seat": "20A"
            },
            {
                "firstName": "Bob",
                "lastName": "Cooper",
                "passengerNumber": 2,
                "customerId": "1022",
                "seat": "20B"
            },
            {
                "firstName": "Charlie",
                "lastName": "Cooper",
                "passengerNumber": 3,
                "customerId": "1023",
                "seat": "20C"
            }
        ],
        "flights": [
            {
                "flightNumber": "UA100",
                "departureAirport": "ORD",
                "departureTimeStamp": "2025-12-25T08:00:00+00:00",
                "arrivalAirport": "LAX",
                "arrivalTimeStamp": "2025-12-25T10:30:00+00:00"
            }
        ]
    },
    {
        "_id": "GHR002",
        "bookingReference": "GHR002",
        "cabinClass": "BUSINESS",
        "passengers": [
            {
                "firstName": "Diana",
                "middleName": "Rose",
                "lastName": "Martinez",
                "passengerNumber": 1,
                "customerId": "1099",
                "seat": "3D"
            },
            {
                "firstName": "Edward",
                "lastName": "Martinez",
                "passengerNumber": 2,
                "customerId": "1032",
                "seat": "3E"
            }
        ],
        "flights": [
            {
                "flightNumber": "AA200",
                "departureAirport": "MIA",
                "departureTimeStamp": "2025-12-28T15:30:00+00:00",
                "arrivalAirport": "ATL",
                "arrivalTimeStamp": "2025-12-28T17:45:00+00:00"
            }
        ]
    }
]);

// Create baggage collection
db.createCollection('baggage');

// Insert sample baggage data
db.baggage.insertMany([
    {
        "bookingReference": "GHTW42",
        "baggageAllowances": [
            {
                "passengerNumber": 1,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 25,
                "carryOnAllowanceValue": 7
            },
            {
                "passengerNumber": 2,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 25,
                "carryOnAllowanceValue": 7
            }
        ]
    },
    {
        "bookingReference": "ABC123",
        "baggageAllowances": [
            {
                "passengerNumber": 1,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 32,
                "carryOnAllowanceValue": 14
            }
        ]
    },
    {
        "bookingReference": "XYZ789",
        "baggageAllowances": [
            {
                "passengerNumber": 1,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 40,
                "carryOnAllowanceValue": 18
            },
            {
                "passengerNumber": 2,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 40,
                "carryOnAllowanceValue": 18
            }
        ]
    },
    {
        "bookingReference": "DEF456",
        "baggageAllowances": [
            {
                "passengerNumber": 1,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 23,
                "carryOnAllowanceValue": 8
            },
            {
                "passengerNumber": 2,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 23,
                "carryOnAllowanceValue": 8
            },
            {
                "passengerNumber": 3,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 23,
                "carryOnAllowanceValue": 8
            }
        ]
    },
    {
        "bookingReference": "PQR999",
        "baggageAllowances": [
            {
                "passengerNumber": 1,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 30,
                "carryOnAllowanceValue": 10
            }
        ]
    },
    {
        "bookingReference": "GHR001",
        "baggageAllowances": [
            {
                "passengerNumber": 1,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 23,
                "carryOnAllowanceValue": 7
            },
            {
                "passengerNumber": 2,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 23,
                "carryOnAllowanceValue": 7
            },
            {
                "passengerNumber": 3,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 23,
                "carryOnAllowanceValue": 7
            }
        ]
    },
    {
        "bookingReference": "GHR002",
        "baggageAllowances": [
            {
                "passengerNumber": 1,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 32,
                "carryOnAllowanceValue": 14
            },
            {
                "passengerNumber": 2,
                "allowanceUnit": "kg",
                "checkedAllowanceValue": 32,
                "carryOnAllowanceValue": 14
            }
        ]
    }
]);

// Create tickets collection
db.createCollection('tickets');

// Insert sample ticket data (Note: Passenger 1 of GHTW42 has NO ticket)
db.tickets.insertMany([
    {
        "bookingReference": "GHTW42",
        "passengerNumber": 2,
        "ticketUrl": "emirates.com?ticket=someTicketRef"
    },
    {
        "bookingReference": "ABC123",
        "passengerNumber": 1,
        "ticketUrl": "britishairways.com?ticket=BA456TICKET"
    },
    {
        "bookingReference": "XYZ789",
        "passengerNumber": 1,
        "ticketUrl": "qantas.com?ticket=QF12TICKET001"
    },
    {
        "bookingReference": "XYZ789",
        "passengerNumber": 2,
        "ticketUrl": "qantas.com?ticket=QF12TICKET002"
    },
    {
        "bookingReference": "DEF456",
        "passengerNumber": 2,
        "ticketUrl": "lufthansa.com?ticket=LH401TICKET"
    },
    {
        "bookingReference": "DEF456",
        "passengerNumber": 3,
        "ticketUrl": "lufthansa.com?ticket=LH401TICKET3"
    },
    {
        "bookingReference": "PQR999",
        "passengerNumber": 1,
        "ticketUrl": "singaporeair.com?ticket=SQ237TICKET"
    },
    {
        "bookingReference": "GHR001",
        "passengerNumber": 1,
        "ticketUrl": "united.com?ticket=UA100TICKET001"
    },
    {
        "bookingReference": "GHR001",
        "passengerNumber": 2,
        "ticketUrl": "united.com?ticket=UA100TICKET002"
    },
    {
        "bookingReference": "GHR001",
        "passengerNumber": 3,
        "ticketUrl": "united.com?ticket=UA100TICKET003"
    },
    {
        "bookingReference": "GHR002",
        "passengerNumber": 1,
        "ticketUrl": "aa.com?ticket=AA200TICKET001"
    },
    {
        "bookingReference": "GHR002",
        "passengerNumber": 2,
        "ticketUrl": "aa.com?ticket=AA200TICKET002"
    }
]);

// =============================================================================
// SHARDING CONFIGURATION (For Production/Scaled Environments)
// =============================================================================
// Purpose: Enable horizontal scaling by distributing data across multiple shards
// When to use: When data size exceeds single server capacity (500GB+) or need 
//              high availability and geographic distribution
// =============================================================================

// -------------------------------------------------------------------------
// PHASE 1: Create Indexes (REQUIRED before sharding)
// -------------------------------------------------------------------------
// WHY: MongoDB requires an index on the shard key before sharding a collection
// WITHOUT THIS: sh.shardCollection() will fail with "index not found" error
// -------------------------------------------------------------------------

print("Creating indexes for sharding...");

// trips collection: Compound index for time-based + PNR queries
// - bookingReference: Primary query pattern (targeted shard lookup)
// - departureDate: Secondary for time-based queries and data archival
// WITHOUT THIS INDEX: Cannot shard trips collection, queries will be slower
db.trips.createIndex({ bookingReference: 1, departureDate: 1 });

// baggage collection: Simple index on bookingReference
// - All baggage queries use bookingReference (one-to-one with trip)
// WITHOUT THIS INDEX: Cannot shard baggage collection
db.baggage.createIndex({ bookingReference: 1 });

// tickets collection: Simple index on bookingReference
// - All ticket queries use bookingReference (one-to-many with trip)
// WITHOUT THIS INDEX: Cannot shard tickets collection
db.tickets.createIndex({ bookingReference: 1 });

print("Indexes created successfully!");

// -------------------------------------------------------------------------
// PHASE 2: Shard Collections (Enable distributed data storage)
// -------------------------------------------------------------------------
// NOTE: Uncomment these commands when deploying to a sharded cluster
// NOTE: These will FAIL on standalone MongoDB (current setup)
// -------------------------------------------------------------------------

/*
// Enable sharding on database
// WHY: Must enable sharding at database level before sharding collections
// WITHOUT THIS: Cannot shard any collections in this database
sh.enableSharding("pnr_db");

// Shard trips collection with compound key
// - bookingReference: "hashed" = Even distribution across shards (no hotspots)
// - departureDate: Range-based = Efficient time-based queries and archival
// WHY COMPOUND KEY: 
//   1. Hash on bookingReference prevents hotspots (all shards get equal load)
//   2. Range on departureDate allows efficient date-based queries
//   3. Can archive old data by migrating date ranges to cold storage
// WITHOUT SHARDING: All data on one server, limited by single server capacity
sh.shardCollection("pnr_db.trips", { 
    bookingReference: "hashed",
    departureDate: 1 
});

// Shard baggage collection (simple hashed key)
// WHY HASHED: Even distribution, all queries include bookingReference
// WITHOUT SHARDING: Limited to single server storage/performance
sh.shardCollection("pnr_db.baggage", { bookingReference: "hashed" });

// Shard tickets collection (simple hashed key)
// WHY HASHED: Even distribution, all queries include bookingReference
// WITHOUT SHARDING: Limited to single server storage/performance
sh.shardCollection("pnr_db.tickets", { bookingReference: "hashed" });

print("Collections sharded successfully!");
print("Shard distribution:");
print("- trips: Hashed by bookingReference, ranged by departureDate");
print("- baggage: Hashed by bookingReference");
print("- tickets: Hashed by bookingReference");
*/

// =============================================================================
// PHASE 3: Customer Bookings Index Collection (For optimized customer queries)
// =============================================================================
// Purpose: Enable fast customer->bookings lookup without scatter-gather queries
// Trade-off: Adds slight overhead on writes, but dramatically speeds up reads
// Use case: When /customer/{customerId} endpoint has high traffic
// =============================================================================

print("Creating customer_bookings index collection...");

// Create customer_bookings collection
// WHY: Provides O(1) lookup from customerId to their bookingReferences
// WITHOUT THIS: Must query ALL shards to find customer's bookings (slow)
db.createCollection("customer_bookings");

// Create index on customerId (primary lookup field)
// WHY: Fast lookup when querying by customerId
// WITHOUT THIS: Full collection scan on every customer query
db.customer_bookings.createIndex({ customerId: 1 });

// Populate customer_bookings from existing trips data
// WHY: Build the index by extracting customer->booking relationships
// WHAT IT DOES:
//   1. $unwind: Flattens passengers array (one doc per passenger)
//   2. $group: Groups by customerId, collects all their bookingReferences
//   3. $out: Writes results to customer_bookings collection
// WITHOUT THIS: customer_bookings collection would be empty (no data)
print("Populating customer_bookings from trips data...");
db.trips.aggregate([
    // Step 1: Flatten passengers array
    // BEFORE: { bookingReference: "GHTW42", passengers: [{customerId: "1216"}, {customerId: null}] }
    // AFTER:  { bookingReference: "GHTW42", passengers: {customerId: "1216"} }
    //         { bookingReference: "GHTW42", passengers: {customerId: null} }
    { $unwind: "$passengers" },

    // Step 2: Group by customerId and collect booking references
    // RESULT: { _id: "1216", bookings: ["GHTW42", "GHR002"] }
    //         { _id: "5678", bookings: ["ABC123"] }
    {
        $group: {
            _id: "$passengers.customerId",           // Group by customerId
            bookings: { $addToSet: "$bookingReference" }  // Collect unique PNRs
        }
    },

    // Step 3: Rename _id to customerId for clarity
    {
        $project: {
            customerId: "$_id",
            bookings: 1,
            _id: 0
        }
    },

    // Step 4: Write results to customer_bookings collection
    // WITHOUT THIS: Results are only temporary (not persisted)
    { $out: "customer_bookings" }
]);

print("customer_bookings collection populated!");

// Show sample data for verification
print("Sample customer_bookings documents:");
db.customer_bookings.find({ customerId: { $ne: null } }).limit(3).forEach(printjson);

// -------------------------------------------------------------------------
// Shard customer_bookings collection (for scaled environments)
// -------------------------------------------------------------------------
// NOTE: Uncomment when deploying to sharded cluster
/*
// Shard by customerId (hashed for even distribution)
// WHY HASHED: Customers distributed evenly across shards (no hotspots)
// QUERY PATTERN: Always query by customerId (targeted to 1 shard)
// WITHOUT SHARDING: All customer lookups hit single server
sh.shardCollection("pnr_db.customer_bookings", { customerId: "hashed" });

print("customer_bookings collection sharded successfully!");
*/

print("=====================================");
print("MongoDB initialization complete!");
print("Collections created: trips, baggage, tickets, customer_bookings");
print("Sample data inserted for PNR: GHTW42, ABC123, XYZ789, DEF456, PQR999, GHR001, GHR002");
print("Indexes created for sharding readiness");
print("customer_bookings collection ready for fast customer queries");
print("=====================================");
