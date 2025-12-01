package com.pnr.aggregator.service;

import com.pnr.aggregator.model.dto.BookingResponse;
import com.pnr.aggregator.model.dto.FlightDTO;
import com.pnr.aggregator.model.dto.PassengerDTO;
import com.pnr.aggregator.model.entity.*;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * -@Service: Marks this class as a Spring service component.
 * --Indicates this class contains business logic
 * --Makes it a candidate for component scanning and dependency
 * injection
 * --Registered as a bean in the Spring application context
 * --Can be autowired into other components (like controllers)
 * --WithoutIT: This class won't be discovered during component scanning;
 * ---dependency injection would fail for this service.
 * =========
 * -@Slf4j: Lombok annotation that generates a SLF4J Logger field
 * --Auto-creates: private static final Logger log =
 * LoggerFactory.getLogger(BookingAggregatorService.class)
 * --Enables logging with log.info(), log.error(), log.debug() without
 * manual logger creation
 */
@Service
@Slf4j
public class BookingAggregatorService {

    /**
     * -@Autowired: Dependency injection for TripService.
     * --Spring automatically injects the TripService bean instance
     * --No manual instantiation needed
     * --WithoutIT: tripService would be null;
     * ---NullPointerException when calling tripService methods.
     */
    @Autowired
    private TripService tripService;

    /**
     * -@Autowired: Dependency injection for BaggageService.
     * --Spring automatically injects the BaggageService bean instance
     * --WithoutIT: baggageService would be null;
     * ---NullPointerException when fetching baggage data.
     */
    @Autowired
    private BaggageService baggageService;

    /**
     * -@Autowired: Dependency injection for TicketService.
     * --Spring automatically injects the TicketService bean instance
     * --WithoutIT: ticketService would be null;
     * ---NullPointerException when fetching ticket data.
     */
    @Autowired
    private TicketService ticketService;

    /**
     * Get all bookings for a specific customer ID
     * 
     * Retrieves PNRs for the given customerId in a fully reactive way,
     * then calls aggregateBooking for each PNR in parallel without blocking.
     * 
     * BEHAVIOR:
     * - Searches MongoDB for ALL trips where ANY passenger has the given customerId
     * - For each matching PNR, retrieves complete booking details (all passengers,
     * not just the matching one)
     * - Aggregates all bookings in parallel for maximum performance
     * 
     * EXAMPLE SCENARIOS:
     * 1. Customer "1021" in booking "GHR001" (with passengers 1021, 1022, 1023)
     * → Returns complete booking GHR001 with all 3 passengers
     * 
     * 2. Customer "1031" in booking "GHR002" (with passengers 1031, 1032)
     * → Returns complete booking GHR002 with all 2 passengers
     * 
     * 3. Customer "1021" appears in BOTH "GHR001" and "GHR002"
     * → Returns both complete bookings (multiple PNRs for same customer)
     * 
     * REACTIVE FLOW:
     * 1. MongoDB query: passengers.customerId (non-blocking)
     * 2. Extract PNRs from matching trips
     * 3. Call aggregateBooking(pnr) for each PNR in PARALLEL
     * 4. Return List<BookingResponse> with all customer's bookings
     * 
     * -@param customerId The customer identifier to search for
     * -@return Future with list of complete booking responses (includes all
     * passengers per booking)
     */
    public Future<List<BookingResponse>> getBookingsByCustomerId(String customerId) {
        log.info("Searching bookings for Customer ID: {}", customerId);

        // Reactive: Get trips and extract PNRs without blocking
        return tripService.getTripsByCustomerId(customerId)
                .compose(trips -> {
                    if (trips.isEmpty()) {
                        log.info("No trips found for Customer ID: {}", customerId);
                        return Future.succeededFuture(List.<BookingResponse>of());
                    }

                    // Extract PNRs and initiate parallel aggregation
                    List<String> pnrs = trips.stream()
                            .map(trip -> trip.getBookingReference())
                            .collect(Collectors.toList());

                    log.debug("Found {} PNR(s) for Customer ID: {}: {}", pnrs.size(), customerId, pnrs);

                    // Reactive: Aggregate all bookings in parallel
                    List<Future<BookingResponse>> bookingFutures = pnrs.stream()
                            .map(this::aggregateBooking)
                            .collect(Collectors.toList());

                    // Wait for all aggregations to complete
                    return Future.all(bookingFutures)
                            .map(cf -> bookingFutures.stream()
                                    .map(Future::result)
                                    .collect(Collectors.toList()));
                })
                .onSuccess(bookings -> {
                    log.info("Successfully aggregated {} booking(s) for Customer ID: {}",
                            bookings.size(), customerId);
                })
                .onFailure(error -> {
                    log.error("Failed to aggregate bookings for Customer ID: {}", customerId, error);
                });
    }

    /**
     * -@Autowired: Dependency injection for Vert.x instance
     * --Vert.x is configured in VertxConfig and injected here
     * --Used for event bus communication and async operations
     * --WithoutIT: vertx would be null;
     * ---event broadcasting to WebSocket clients would fail.
     */
    @Autowired
    private Vertx vertx;

    public Future<BookingResponse> aggregateBooking(String pnr) {
        log.info("Aggregating booking for PNR: {}", pnr);

        return tripService.getTripInfo(pnr)
                .compose(trip -> {
                    // PARALLEL: Fetch baggage + all tickets
                    Future<Baggage> baggageFuture = baggageService.getBaggageInfo(pnr);

                    // PARALLEL: Fetch tickets for each passenger
                    List<Future<Ticket>> ticketFutures = trip.getPassengers().stream()
                            .map(p -> ticketService.getTicket(pnr, p.getPassengerNumber())
                                    .recover(err -> {
                                        // Missing ticket is OK - not all passengers have tickets
                                        log.debug("Ticket not found for passenger {}, continuing",
                                                p.getPassengerNumber());
                                        return Future.succeededFuture(null);
                                    }))
                            .collect(Collectors.toList());

                    // Wait for all parallel operations
                    // CompositeFuture.all()
                    return Future.all(baggageFuture, Future.all(ticketFutures))
                            .map(cf -> {
                                BookingResponse response = mergeData(trip, baggageFuture.result(), ticketFutures);
                                publishPnrEvent(pnr, response.getStatus());
                                return response;
                            });
                })
                .onSuccess(response -> {
                    log.info("Successfully aggregated booking for PNR: {} with status: {}", pnr, response.getStatus());
                })
                .onFailure(error -> {
                    log.error("Failed to aggregate booking for PNR: {}", pnr, error);
                });
    }

    /**
     * Merges data from Trip, Baggage, and Ticket services into BookingResponse
     * 
     * Handles fallback scenarios by:
     * 1. Setting PNR-level fallback messages from Trip (if from cache)
     * 2. Setting passenger-level fallback messages from Baggage and Ticket services
     * 3. Setting flight-level fallback messages (if trip from cache)
     * 
     * -@param trip Trip data (may be from cache)
     * -@param baggage Baggage data (may be default allowance)
     * -@param ticketFutures Ticket futures for all passengers
     * -@return Complete BookingResponse with appropriate fallback messages
     */
    private BookingResponse mergeData(Trip trip, Baggage baggage,
            List<Future<Ticket>> ticketFuturesList) {
        BookingResponse response = new BookingResponse();
        response.setPnr(trip.getBookingReference());
        response.setCabinClass(trip.getCabinClass());

        // Determine status based on data sources
        boolean hasCache = trip.isFromCache() || baggage.isFromCache();
        boolean hasDefault = baggage.isFromDefault();

        if (hasCache || hasDefault) {
            response.setStatus("DEGRADED");
            response.setFromCache(hasCache);
            if (hasCache) {
                response.setCacheTimestamp(trip.getCacheTimestamp());
            }
        } else {
            response.setStatus("SUCCESS");
        }

        // Set PNR-level fallback messages from Trip
        if (trip.getPnrFallbackMsg() != null && !trip.getPnrFallbackMsg().isEmpty()) {
            response.setPnrFallbackMsg(trip.getPnrFallbackMsg());
        }

        // Map passengers with tickets, baggage, and their fallback messages
        List<PassengerDTO> passengerDTOs = new ArrayList<>();
        for (Passenger p : trip.getPassengers()) {
            PassengerDTO dto = new PassengerDTO();
            dto.setPassengerNumber(p.getPassengerNumber());
            dto.setFullName(buildFullName(p));
            dto.setSeat(p.getSeat());
            dto.setCustomerId(p.getCustomerId());

            // Find matching ticket
            Ticket ticket = ticketFuturesList.stream()
                    .map(Future::result)
                    .filter(t -> t != null && t.getPassengerNumber() == p.getPassengerNumber())
                    .findFirst()
                    .orElse(null);

            if (ticket != null) {
                dto.setTicketUrl(ticket.getTicketUrl());
            }
            // If no ticket, ticketUrl field is not set (null)

            // Find matching baggage allowance
            if (baggage != null && baggage.getAllowances() != null) {
                baggage.getAllowances().stream()
                        .filter(ba -> ba.getPassengerNumber() != null &&
                                ba.getPassengerNumber().equals(p.getPassengerNumber()))
                        .findFirst()
                        .ifPresent(ba -> {
                            dto.setAllowanceUnit(ba.getAllowanceUnit());
                            dto.setCheckedAllowanceValue(ba.getCheckedAllowanceValue());
                            dto.setCarryOnAllowanceValue(ba.getCarryOnAllowanceValue());
                        });

                // If no passenger-specific allowance found, use default (first one without
                // passengerNumber)
                if (dto.getAllowanceUnit() == null) {
                    baggage.getAllowances().stream()
                            .filter(ba -> ba.getPassengerNumber() == null)
                            .findFirst()
                            .ifPresent(ba -> {
                                dto.setAllowanceUnit(ba.getAllowanceUnit());
                                dto.setCheckedAllowanceValue(ba.getCheckedAllowanceValue());
                                dto.setCarryOnAllowanceValue(ba.getCarryOnAllowanceValue());
                            });
                }
            }

            // Collect all fallback messages for this passenger
            List<String> passengerMessages = new ArrayList<>();

            // Add baggage fallback messages (applies to all passengers)
            if (baggage.getBaggageFallbackMsg() != null && !baggage.getBaggageFallbackMsg().isEmpty()) {
                passengerMessages.addAll(baggage.getBaggageFallbackMsg());
            }

            // Add ticket-specific fallback messages
            if (ticket != null && ticket.getTicketFallbackMsg() != null && !ticket.getTicketFallbackMsg().isEmpty()) {
                passengerMessages.addAll(ticket.getTicketFallbackMsg());
            }

            // Set passenger fallback messages only if any exist
            if (!passengerMessages.isEmpty()) {
                dto.setPassengerFallbackMsg(passengerMessages);
            }

            passengerDTOs.add(dto);
        }
        response.setPassengers(passengerDTOs);

        // Map flights with their fallback messages
        List<FlightDTO> flightDTOs = trip.getFlights().stream()
                .map(f -> {
                    FlightDTO dto = new FlightDTO();
                    dto.setFlightNumber(f.getFlightNumber());
                    dto.setDepartureAirport(f.getDepartureAirport());
                    dto.setDepartureTimeStamp(f.getDepartureTimeStamp());
                    dto.setArrivalAirport(f.getArrivalAirport());
                    dto.setArrivalTimeStamp(f.getArrivalTimeStamp());

                    // If trip is from cache, add flight-specific fallback messages
                    if (trip.isFromCache()) {
                        dto.setFlightFallbackMsg(List.of(
                                "Flight " + f.getFlightNumber() + " data from cache",
                                "Flight data may not be up-to-date"));
                    }

                    return dto;
                })
                .collect(Collectors.toList());
        response.setFlights(flightDTOs);

        return response;
    }

    private String buildFullName(Passenger p) {
        StringBuilder name = new StringBuilder(p.getFirstName());
        if (p.getMiddleName() != null && !p.getMiddleName().isEmpty()) {
            name.append(" ").append(p.getMiddleName());
        }
        name.append(" ").append(p.getLastName());
        return name.toString();
    }

    private void publishPnrEvent(String pnr, String status) {
        JsonObject event = new JsonObject()
                .put("pnr", pnr)
                .put("timestamp", Instant.now().toString())
                .put("status", status);

        vertx.eventBus().publish("pnr.fetched", event);
    }

}
