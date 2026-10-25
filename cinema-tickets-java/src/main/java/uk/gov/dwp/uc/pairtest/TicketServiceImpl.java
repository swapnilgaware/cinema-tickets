package uk.gov.dwp.uc.pairtest;

import java.util.Objects;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.paymentgateway.TicketPaymentServiceImpl;
import thirdparty.seatbooking.SeatReservationService;
import thirdparty.seatbooking.SeatReservationServiceImpl;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

public class TicketServiceImpl implements TicketService {
    private static final int MAX_TICKETS = 25;
    private static final int ADULT_PRICE = 25;
    private static final int CHILD_PRICE = 15;

    private final TicketPaymentService paymentService;
    private final SeatReservationService seatReservationService;

    public TicketServiceImpl() {
        this(new TicketPaymentServiceImpl(), new SeatReservationServiceImpl());
    }

    public TicketServiceImpl(
            TicketPaymentService paymentService,
            SeatReservationService seatReservationService) {
        this.paymentService = Objects.requireNonNull(paymentService, "Payment service is required");
        this.seatReservationService = Objects.requireNonNull(
                seatReservationService, "Seat reservation service is required");
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests)
            throws InvalidPurchaseException {
        validatePurchaseRequest(accountId, ticketTypeRequests);

        int adults = 0;
        int children = 0;
        int totalTickets = 0;

        for (TicketTypeRequest request : ticketTypeRequests) {
            validateTicketRequest(request, totalTickets);
            int quantity = request.getNoOfTickets();
            totalTickets += quantity;

            switch (request.getTicketType()) {
                case ADULT -> adults += quantity;
                case CHILD -> children += quantity;
                case INFANT -> {}
            }
        }

        validateAdultTickets(adults);

        int totalAmountToPay = adults * ADULT_PRICE + children * CHILD_PRICE;
        int totalSeatsToAllocate = adults + children;

        paymentService.makePayment(accountId, totalAmountToPay);
        seatReservationService.reserveSeat(accountId, totalSeatsToAllocate);
    }

    private void validatePurchaseRequest(Long accountId, TicketTypeRequest[] ticketTypeRequests) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Account ID must be greater than zero");
        }
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException("At least one ticket request is required");
        }
    }

    private void validateTicketRequest(TicketTypeRequest request, int totalTickets) {
        if (request == null || request.getTicketType() == null) {
            throw new InvalidPurchaseException("Each ticket request must have a ticket type");
        }

        int quantity = request.getNoOfTickets();
        if (quantity <= 0) {
            throw new InvalidPurchaseException("Ticket quantities must be greater than zero");
        }
        if (quantity > MAX_TICKETS - totalTickets) {
            throw new InvalidPurchaseException(
                    "A purchase cannot contain more than 25 tickets, including infants");
        }
    }

    private void validateAdultTickets(int adults) {
        if (adults == 0) {
            throw new InvalidPurchaseException("At least one adult ticket is required");
        }
    }
}
