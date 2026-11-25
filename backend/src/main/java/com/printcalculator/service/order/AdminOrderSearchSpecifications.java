package com.printcalculator.service.order;

import com.printcalculator.dto.AdminOrderSearchQuery;
import com.printcalculator.dto.AdminOrderSearchQuery.OrderType;
import com.printcalculator.dto.AdminOrderSearchQuery.PaymentStatus;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.Payment;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

final class AdminOrderSearchSpecifications {
    private AdminOrderSearchSpecifications() {
    }

    static Specification<Order> from(AdminOrderSearchQuery filters) {
        return Specification.allOf(
                matchesSearch(filters.q()),
                matchesOrderStatus(filters.orderStatus()),
                matchesPaymentStatus(filters.paymentStatus()),
                matchesOrderType(filters.orderType()));
    }

    private static Specification<Order> matchesSearch(String search) {
        if (search == null || search.isBlank()) {
            return Specification.unrestricted();
        }

        String term = search.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String pattern = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return (order, query, cb) -> {
            var firstName = cb.trim(cb.coalesce(order.<String>get("billingFirstName"), ""));
            var lastName = cb.trim(cb.coalesce(order.<String>get("billingLastName"), ""));
            var fullName = cb.concat(cb.concat(firstName, " "), lastName);
            // The displayed order number is the first part of the UUID.
            return cb.or(
                    cb.like(cb.lower(order.get("id").cast(String.class)), pattern, '!'),
                    cb.like(cb.lower(order.get("customerEmail")), pattern, '!'),
                    cb.like(cb.lower(fullName), pattern, '!'),
                    cb.like(cb.lower(order.get("billingCompanyName")), pattern, '!'),
                    cb.like(cb.lower(order.get("billingContactPerson")), pattern, '!'));
        };
    }

    private static Specification<Order> matchesOrderStatus(AdminOrderSearchQuery.OrderStatus status) {
        if (status == null || status == AdminOrderSearchQuery.OrderStatus.ALL) {
            return Specification.unrestricted();
        }
        return (order, query, cb) -> cb.equal(cb.upper(order.get("status")), status.name());
    }

    private static Specification<Order> matchesPaymentStatus(PaymentStatus status) {
        if (status == null || status == PaymentStatus.ALL) {
            return Specification.unrestricted();
        }
        return (order, query, cb) -> {
            var payments = query.subquery(Integer.class);
            var payment = payments.from(Payment.class);
            var paymentStatus = cb.upper(payment.<String>get("status"));
            Predicate matchingStatus = cb.equal(paymentStatus, status.name());
            if (status != PaymentStatus.PENDING) {
                return cb.exists(payments.select(cb.literal(1))
                        .where(cb.equal(payment.get("order"), order), matchingStatus));
            }

            var anyPayment = query.subquery(Integer.class);
            var any = anyPayment.from(Payment.class);
            anyPayment.select(cb.literal(1)).where(cb.equal(any.get("order"), order));
            payments.select(cb.literal(1)).where(cb.equal(payment.get("order"), order),
                    cb.or(matchingStatus, cb.equal(paymentStatus, "")));
            return cb.or(cb.exists(payments), cb.not(cb.exists(anyPayment)));
        };
    }

    private static Specification<Order> matchesOrderType(OrderType type) {
        if (type == null || type == OrderType.ALL) {
            return Specification.unrestricted();
        }
        return (order, query, cb) -> {
            Predicate isCad = cb.isTrue(cb.coalesce(order.<Boolean>get("isCadOrder"), false));
            return switch (type) {
                case CAD -> isCad;
                case CALCULATOR -> cb.and(cb.not(isCad), cb.not(hasItems(order, query, cb, true)));
                case SHOP -> cb.and(cb.not(isCad), hasItems(order, query, cb, true),
                        cb.not(hasItems(order, query, cb, false)));
                case MIXED -> cb.and(cb.not(isCad), hasItems(order, query, cb, true),
                        hasItems(order, query, cb, false));
                case ALL -> cb.conjunction();
            };
        };
    }

    private static Predicate hasItems(Root<Order> order, CriteriaQuery<?> query, CriteriaBuilder cb, boolean shop) {
        var items = query.subquery(Integer.class);
        var item = items.from(OrderItem.class);
        Predicate isShop = cb.equal(cb.upper(cb.coalesce(item.<String>get("itemType"), "PRINT_FILE")), "SHOP_PRODUCT");
        items.select(cb.literal(1)).where(cb.equal(item.get("order"), order), shop ? isShop : cb.not(isShop));
        return cb.exists(items);
    }
}
