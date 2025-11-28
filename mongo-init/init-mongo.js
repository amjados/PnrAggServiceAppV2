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
    }
]);

print("MongoDB initialization complete!");
print("Collections created: trips, baggage, tickets");
print("Sample data inserted for PNR: GHTW42, ABC123");
