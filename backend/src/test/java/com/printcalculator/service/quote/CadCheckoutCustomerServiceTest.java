package com.printcalculator.service.quote;

import com.printcalculator.dto.ServiceQuoteDtos.Customer;
import com.printcalculator.dto.ServiceQuoteReference;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.entity.ServiceQuoteAcceptance;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.repository.ServiceQuoteAcceptanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CadCheckoutCustomerServiceTest {
    private final QuoteSessionRepository sessions = mock(QuoteSessionRepository.class);
    private final ServiceQuoteAcceptanceRepository acceptances = mock(ServiceQuoteAcceptanceRepository.class);
    private final CadCheckoutCustomerService service = new CadCheckoutCustomerService(sessions, acceptances);

    private QuoteSession session() {
        var session = new QuoteSession(); session.setId(UUID.randomUUID()); session.setStatus("CAD_ACTIVE");
        var quoteId = UUID.randomUUID(); var revisionId = UUID.randomUUID(); var acceptanceId = UUID.randomUUID();
        session.setServiceQuoteId(quoteId);
        session.setServiceQuoteReference(new ServiceQuoteReference(quoteId, revisionId, acceptanceId, 1, "Design", BigDecimal.TEN, BigDecimal.TEN, "PERCENTAGE", BigDecimal.ZERO));
        session.setCadCheckoutCustomerToken(CadCheckoutCustomerService.newToken());
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        return session;
    }

    @Test void onlyTheDedicatedCredentialReturnsTheAcceptedCustomer() {
        var session = session();
        var acceptance = new ServiceQuoteAcceptance(); acceptance.setRevisionId(session.getServiceQuoteReference().revisionId());
        var customer = new Customer("ada@example.test", "BUSINESS", null, null, "Example AG", "Ada Example", "Example 1", "6900", "Lugano", "CH", null);
        acceptance.setCustomer(customer);
        when(acceptances.findById(session.getServiceQuoteReference().acceptanceId())).thenReturn(Optional.of(acceptance));
        assertEquals(customer, service.customer(session.getId(), session.getCadCheckoutCustomerToken()));
        assertThrows(ResponseStatusException.class, () -> service.customer(session.getId(), null));
        assertThrows(ResponseStatusException.class, () -> service.customer(session.getId(), "wrong-token"));
        assertThrows(ResponseStatusException.class, () -> service.customer(UUID.randomUUID(), session.getCadCheckoutCustomerToken()));
    }

    @Test void detachedConvertedAndDeletedSessionsCannotReturnCustomerData() {
        var session = session(); var token = session.getCadCheckoutCustomerToken();
        session.setConvertedOrderId(UUID.randomUUID());
        assertThrows(ResponseStatusException.class, () -> service.customer(session.getId(), token));
        session.setConvertedOrderId(null); session.setStatus("CONVERTED");
        assertThrows(ResponseStatusException.class, () -> service.customer(session.getId(), token));
        session.setStatus("CAD_DELETED");
        assertThrows(ResponseStatusException.class, () -> service.customer(session.getId(), token));
        session.setStatus("CAD_ACTIVE"); session.setServiceQuoteReference(null);
        assertThrows(ResponseStatusException.class, () -> service.customer(session.getId(), token));
        verifyNoInteractions(acceptances);
    }

    @Test void acceptanceFromADifferentRevisionIsRejected() {
        var session = session(); var acceptance = new ServiceQuoteAcceptance(); acceptance.setRevisionId(UUID.randomUUID());
        when(acceptances.findById(session.getServiceQuoteReference().acceptanceId())).thenReturn(Optional.of(acceptance));
        assertThrows(ResponseStatusException.class, () -> service.customer(session.getId(), session.getCadCheckoutCustomerToken()));
    }
}
