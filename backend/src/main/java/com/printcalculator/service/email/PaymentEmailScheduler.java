package com.printcalculator.service.email;

import com.printcalculator.repository.PaymentEmailJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEmailScheduler {
    private final PaymentEmailJobRepository jobs;
    private final PaymentEmailDelivery delivery;

    @Scheduled(fixedDelayString = "${app.payment.email-poll-ms:1000}", scheduler = "paymentEmailTaskScheduler")
    public void tick() {
        jobs.findByStatusAndAttemptedAtBefore("SENDING", OffsetDateTime.now().minusMinutes(15), PageRequest.of(0, 25))
                .forEach(j -> delivery.markUncertain(j.getId()));
        for (var job : jobs.findByStatusAndDueAtLessThanEqualOrderByDueAtAsc("PENDING", OffsetDateTime.now(), PageRequest.of(0, 25))) {
            try {
                if (delivery.claim(job.getId(), job.getOrderId())) delivery.deliver(job.getId(), job.getOrderId());
            } catch (Exception e) {
                log.error("Payment email job {} interrupted ({})", job.getId(), e.getClass().getSimpleName());
                delivery.markUncertain(job.getId());
            }
        }
    }
}
