package com.printcalculator.event;

import com.printcalculator.entity.Order;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrderShippedEvent extends ApplicationEvent {

    private final Order order;

    public OrderShippedEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }
}
