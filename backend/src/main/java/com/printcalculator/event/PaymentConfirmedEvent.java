package com.printcalculator.event;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.Payment;
import org.springframework.context.ApplicationEvent;

public class PaymentConfirmedEvent extends ApplicationEvent {
    private final Order order;
    private final Payment payment;

    public PaymentConfirmedEvent(Object source, Order order, Payment payment) {
        super(source);
        this.order = order;
        this.payment = payment;
    }

    public Order getOrder() {
        return order;
    }

    public Payment getPayment() {
        return payment;
    }
}
