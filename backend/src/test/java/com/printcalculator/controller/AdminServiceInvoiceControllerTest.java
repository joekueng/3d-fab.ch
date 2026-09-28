package com.printcalculator.controller;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.printcalculator.controller.admin.AdminOperationsController;
import com.printcalculator.service.admin.AdminOperationsControllerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminServiceInvoiceControllerTest {
    @Test
    void rejectsInvalidCustomRowsBeforeCallingService() throws Exception {
        var service = mock(AdminOperationsControllerService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AdminOperationsController(service)).build();
        var mapper = JsonMapper.builder().build();
        for (var line : List.of(
                Map.of("description", "", "billingType", "HOURLY", "quantity", 1, "unitPriceChf", 80),
                Map.of("description", "Review", "billingType", "OTHER", "quantity", 1, "unitPriceChf", 80),
                Map.of("description", "Review", "billingType", "HOURLY", "quantity", -1, "unitPriceChf", 80),
                Map.of("description", "Review", "billingType", "HOURLY", "quantity", 1, "unitPriceChf", -80),
                Map.of("description", "Review", "billingType", "HOURLY", "quantity", 1.001, "unitPriceChf", 80),
                Map.of("description", "Review", "billingType", "FIXED", "quantity", 2, "unitPriceChf", 80)
        )) {
            mvc.perform(post("/api/admin/cad-invoices").contentType("application/json")
                    .content(mapper.writeValueAsBytes(Map.of("serviceLines", List.of(line)))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/admin/cad-invoices").contentType("application/json")
                .content("{\"serviceLines\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/cad-invoices").contentType("application/json")
                .content("{\"serviceLines\":[null]}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void acceptsLongCustomListWithoutPredefinedCategoriesOrRowLimit() throws Exception {
        var service = mock(AdminOperationsControllerService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AdminOperationsController(service)).build();
        var line = Map.of("description", "Custom finishing / iteration details",
                "billingType", "FIXED", "quantity", 1, "unitPriceChf", 10);
        mvc.perform(post("/api/admin/cad-invoices").contentType("application/json")
                .content(JsonMapper.builder().build().writeValueAsBytes(
                        Map.of("serviceLines", Collections.nCopies(250, line)))))
                .andExpect(status().isOk());
        verify(service).createOrUpdateCadInvoice(argThat(request -> request.getServiceLines().size() == 250));
    }
}
