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
    }
]);

print("MongoDB initialization complete!");
print("Collections created: trips, baggage, tickets");
print("Sample data inserted for PNR: GHTW42, ABC123, XYZ789, DEF456, PQR999");
