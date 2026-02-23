package com.printcalculator.service;

import com.printcalculator.entity.Order;
import net.codecrete.qrbill.generator.Bill;
import net.codecrete.qrbill.generator.GraphicsFormat;
import net.codecrete.qrbill.generator.QRBill;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class QrBillService {

    public byte[] generateQrBillSvg(Order order) {
        Bill bill = createBillFromOrder(order);
        return QRBill.generate(bill);
    }
    
    public Bill createBillFromOrder(Order order) {
        Bill bill = new Bill();

        // Creditor (Merchant)
        bill.setAccount("CH7409000000154821581"); // TODO: Configurable IBAN
        bill.setCreditor(createAddress(
                "Küng, Joe",
                "Via G. Pioda 29a",
                "6710",
                "Biasca",
                "CH"
        ));

        // Debtor (Customer)
        String debtorName;
        if ("BUSINESS".equals(order.getBillingCustomerType())) {
            debtorName = order.getBillingCompanyName();
        } else {
            debtorName = order.getBillingFirstName() + " " + order.getBillingLastName();
        }
        
        bill.setDebtor(createAddress(
                debtorName,
                order.getBillingAddressLine1(), // Assuming simple address for now. Splitting might be needed if street/house number are separate
                order.getBillingZip(),
                order.getBillingCity(),
                order.getBillingCountryCode()
        ));

        // Amount
        bill.setAmount(order.getTotalChf());
        bill.setCurrency("CHF");

        // Reference
        // bill.setReference(QRBill.createCreditorReference("...")); // If using QRR
        String orderRef = order.getOrderNumber() != null ? order.getOrderNumber() : order.getId().toString();
        bill.setUnstructuredMessage("Order " + orderRef);

        return bill;
    }

    private net.codecrete.qrbill.generator.Address createAddress(String name, String street, String zip, String city, String country) {
        net.codecrete.qrbill.generator.Address address = new net.codecrete.qrbill.generator.Address();
        address.setName(name);
        address.setStreet(street);
        address.setPostalCode(zip);
        address.setTown(city);
        address.setCountryCode(country);
        return address;
    }
}
