package com.printcalculator.service.quote;

import com.printcalculator.dto.ServiceQuoteDtos.Customer;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.repository.ServiceQuoteAcceptanceRepository;
import com.printcalculator.service.servicequote.ServiceQuoteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import static org.springframework.http.HttpStatus.FORBIDDEN;

/** Customer data is available only through the separately shared CAD checkout credential. */
@Service
public class CadCheckoutCustomerService {
    private final QuoteSessionRepository sessions;
    private final ServiceQuoteAcceptanceRepository acceptances;

    public CadCheckoutCustomerService(QuoteSessionRepository sessions, ServiceQuoteAcceptanceRepository acceptances) {
        this.sessions = sessions;
        this.acceptances = acceptances;
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @Transactional(readOnly = true)
    public Customer customer(UUID sessionId, String token) {
        var session = sessions.findById(sessionId).orElseThrow(CadCheckoutCustomerService::unavailable);
        var reference = session.getServiceQuoteReference();
        if (!"CAD_ACTIVE".equals(session.getStatus()) || session.getConvertedOrderId() != null
                || reference == null || !reference.quoteId().equals(session.getServiceQuoteId())
                || !ServiceQuoteService.constantEquals(session.getCadCheckoutCustomerToken(), token)) {
            throw unavailable();
        }
        var acceptance = acceptances.findById(reference.acceptanceId()).orElseThrow(CadCheckoutCustomerService::unavailable);
        if (!reference.revisionId().equals(acceptance.getRevisionId())) throw unavailable();
        return acceptance.getCustomer();
    }

    private static ResponseStatusException unavailable() { return new ResponseStatusException(FORBIDDEN, "CHECKOUT_CUSTOMER_UNAVAILABLE"); }
}
