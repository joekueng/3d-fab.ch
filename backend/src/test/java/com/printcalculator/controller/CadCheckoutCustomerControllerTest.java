package com.printcalculator.controller;

import com.printcalculator.dto.ServiceQuoteDtos.Customer;
import com.printcalculator.service.quote.CadCheckoutCustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CadCheckoutCustomerControllerTest {
    @Test void prefillUsesItsOwnCredentialAndDisablesCaching() throws Exception {
        var service = mock(CadCheckoutCustomerService.class); var id = UUID.randomUUID();
        when(service.customer(id, "checkout-token")).thenReturn(new Customer("ada@example.test", "PRIVATE", "Ada", "Example", null, null, "Example 1", "6900", "Lugano", "CH", null));
        var mvc = MockMvcBuilders.standaloneSetup(new CadCheckoutCustomerController(service)).build();
        mvc.perform(get("/api/quote-sessions/{id}/checkout-customer", id).header("X-Cad-Checkout-Token", "checkout-token"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(jsonPath("$.email").value("ada@example.test"));
        verify(service).customer(id, "checkout-token");
    }
}
