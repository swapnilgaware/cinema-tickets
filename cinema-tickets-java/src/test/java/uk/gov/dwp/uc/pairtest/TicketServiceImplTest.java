package uk.gov.dwp.uc.pairtest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.ADULT;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.CHILD;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.INFANT;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

class TicketServiceImplTest {
    private TicketPaymentService paymentService;
    private SeatReservationService seatReservationService;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        paymentService = mock(TicketPaymentService.class);
        seatReservationService = mock(SeatReservationService.class);
        ticketService = new TicketServiceImpl(paymentService, seatReservationService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPurchases")
    void chargesAndReservesExactlyOnce(
            String scenario,
            Long accountId,
            TicketTypeRequest[] requests,
            int expectedAmount,
            int expectedSeats) {
        ticketService.purchaseTickets(accountId, requests);

        InOrder calls = inOrder(paymentService, seatReservationService);
        calls.verify(paymentService).makePayment(accountId, expectedAmount);
        calls.verify(seatReservationService).reserveSeat(accountId, expectedSeats);
        verifyNoMoreInteractions(paymentService, seatReservationService);
    }

    private static Stream<Arguments> validPurchases() {
        return Stream.of(
                Arguments.of("one adult", 1L, requests(ADULT, 1), 25, 1),
                Arguments.of("multiple adults", 42L, requests(ADULT, 3), 75, 3),
                Arguments.of("adults and children", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(ADULT, 2), new TicketTypeRequest(CHILD, 3)
                }, 95, 5),
                Arguments.of("infant has no charge or seat", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(INFANT, 1)
                }, 25, 1),
                Arguments.of("more infants than adults", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(INFANT, 2)
                }, 25, 1),
                Arguments.of("multiple infant requests with one adult", 42L,
                        new TicketTypeRequest[] {
                            new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(INFANT, 1),
                            new TicketTypeRequest(INFANT, 1)
                        }, 25, 1),
                Arguments.of("one adult and 24 infants", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(INFANT, 24)
                }, 25, 1),
                Arguments.of("all ticket types", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(ADULT, 2), new TicketTypeRequest(CHILD, 3),
                        new TicketTypeRequest(INFANT, 3)
                }, 95, 5),
                Arguments.of("adult requests can come last", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(INFANT, 1), new TicketTypeRequest(CHILD, 2),
                        new TicketTypeRequest(ADULT, 1)
                }, 55, 3),
                Arguments.of("repeated types are combined", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(CHILD, 1),
                        new TicketTypeRequest(INFANT, 1), new TicketTypeRequest(ADULT, 2),
                        new TicketTypeRequest(CHILD, 2), new TicketTypeRequest(INFANT, 2)
                }, 120, 6),
                Arguments.of("25 adults", 42L, requests(ADULT, 25), 625, 25),
                Arguments.of("25 tickets including infants", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(ADULT, 12), new TicketTypeRequest(CHILD, 1),
                        new TicketTypeRequest(INFANT, 12)
                }, 315, 13),
                Arguments.of("one adult can accompany multiple children", 42L,
                        new TicketTypeRequest[] {
                            new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(CHILD, 24)
                        }, 385, 25),
                Arguments.of("largest positive account ID", Long.MAX_VALUE,
                        requests(ADULT, 1), 25, 1)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPurchases")
    void rejectsInvalidPurchasesWithoutPaymentOrReservation(
            String scenario, Long accountId, TicketTypeRequest[] requests) {
        InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(accountId, requests));

        assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank());
        verifyNoInteractions(paymentService, seatReservationService);
    }

    private static Stream<Arguments> invalidPurchases() {
        Stream<Arguments> malformed = Stream.of(
                Arguments.of("null account ID", null, requests(ADULT, 1)),
                Arguments.of("zero account ID", 0L, requests(ADULT, 1)),
                Arguments.of("negative account ID", -1L, requests(ADULT, 1)),
                Arguments.of("smallest account ID", Long.MIN_VALUE, requests(ADULT, 1)),
                Arguments.of("null request array", 42L, (Object) null),
                Arguments.of("empty purchase", 42L, new TicketTypeRequest[0]),
                Arguments.of("null request", 42L, new TicketTypeRequest[] {null}),
                Arguments.of("null type", 42L, requests(null, 1)),
                Arguments.of("invalid request after valid request", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(ADULT, 1), null
                }),
                Arguments.of("null type after valid request", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(null, 1)
                }),
                Arguments.of("children without adults", 42L, requests(CHILD, 1)),
                Arguments.of("infants without adults", 42L, requests(INFANT, 1)),
                Arguments.of("children and infants without adults", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(CHILD, 1), new TicketTypeRequest(INFANT, 1)
                }),
                Arguments.of("26 adults", 42L, requests(ADULT, 26)),
                Arguments.of("26 tickets across repeated requests", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(ADULT, 13), new TicketTypeRequest(ADULT, 13)
                }),
                Arguments.of("26 tickets across different types", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(CHILD, 25)
                }),
                Arguments.of("infants count towards the limit", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(INFANT, 25)
                }),
                Arguments.of("huge quantity", 42L, requests(ADULT, Integer.MAX_VALUE)),
                Arguments.of("quantities that would overflow an int", 42L, new TicketTypeRequest[] {
                        new TicketTypeRequest(ADULT, 1),
                        new TicketTypeRequest(CHILD, Integer.MAX_VALUE),
                        new TicketTypeRequest(INFANT, Integer.MAX_VALUE)
                })
        );
        Stream<Arguments> invalidQuantities = Stream.of(TicketTypeRequest.Type.values())
                .flatMap(type -> Stream.of(0, -1, Integer.MIN_VALUE)
                        .map(quantity -> Arguments.of(type + " quantity " + quantity, 42L,
                                new TicketTypeRequest[] {
                                        new TicketTypeRequest(ADULT, 1),
                                        new TicketTypeRequest(type, quantity)
                                })));
        return Stream.concat(malformed, invalidQuantities);
    }

    @Test
    void purchasesDoNotShareCounts() {
        ticketService.purchaseTickets(
                10L, new TicketTypeRequest(ADULT, 2), new TicketTypeRequest(INFANT, 2));
        ticketService.purchaseTickets(
                20L, new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(CHILD, 1));

        InOrder calls = inOrder(paymentService, seatReservationService);
        calls.verify(paymentService).makePayment(10L, 50);
        calls.verify(seatReservationService).reserveSeat(10L, 2);
        calls.verify(paymentService).makePayment(20L, 40);
        calls.verify(seatReservationService).reserveSeat(20L, 2);
        verifyNoMoreInteractions(paymentService, seatReservationService);
    }

    @Test
    void rejectedPurchaseDoesNotAffectTheNextPurchase() {
        assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(42L,
                new TicketTypeRequest(INFANT, 2)));
        verifyNoInteractions(paymentService, seatReservationService);

        ticketService.purchaseTickets(
                42L, new TicketTypeRequest(ADULT, 1), new TicketTypeRequest(INFANT, 2));

        InOrder calls = inOrder(paymentService, seatReservationService);
        calls.verify(paymentService).makePayment(42L, 25);
        calls.verify(seatReservationService).reserveSeat(42L, 1);
        verifyNoMoreInteractions(paymentService, seatReservationService);
    }

    private static TicketTypeRequest[] requests(TicketTypeRequest.Type type, int quantity) {
        return new TicketTypeRequest[] {new TicketTypeRequest(type, quantity)};
    }
}
