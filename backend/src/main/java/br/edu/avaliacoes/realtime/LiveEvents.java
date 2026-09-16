package br.edu.avaliacoes.realtime;

import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import br.edu.avaliacoes.service.Store;

@Component
public class LiveEvents {
    public record Changed(UUID sessionId) {}
    private final ApplicationEventPublisher events;
    private final SimpMessagingTemplate messaging;
    private final Store db;
    public LiveEvents(ApplicationEventPublisher events,SimpMessagingTemplate messaging,Store db) { this.events=events; this.messaging=messaging; this.db=db; }
    public void changed(UUID sessionId) { events.publishEvent(new Changed(sessionId)); }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void publish(Changed event) {
        var owner=db.one("SELECT a.teacher_id FROM exam_session s JOIN assessment a ON a.id=s.assessment_id WHERE s.id=?",event.sessionId());
        messaging.convertAndSendToUser(owner.get("teacher_id").toString(),"/queue/monitor",Map.of("sessionId",event.sessionId()));
    }
}

