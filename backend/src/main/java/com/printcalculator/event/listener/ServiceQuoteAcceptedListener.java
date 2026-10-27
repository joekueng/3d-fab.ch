package com.printcalculator.event.listener;
import com.printcalculator.event.ServiceQuoteAcceptedEvent;
import com.printcalculator.service.servicequote.ServiceQuoteDocumentService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j @Component @RequiredArgsConstructor
public class ServiceQuoteAcceptedListener {
    private final ServiceQuoteDocumentService documents;
    @Async @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void accepted(ServiceQuoteAcceptedEvent event) {
        try{documents.process(event.acceptanceId());}
        catch(Exception e){log.warn("Quote document processing needs recovery: {}",event.acceptanceId());}
    }
}
