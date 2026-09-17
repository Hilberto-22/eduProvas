package br.edu.avaliacoes.realtime;

import java.util.*;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import br.edu.avaliacoes.repository.ExamSessionRepository;

@Component
public class LiveEvents {
    public record Changed(UUID sessionId) {
    }

    private final ApplicationEventPublisher events;
    private final SimpMessagingTemplate messaging;
    private final ExamSessionRepository sessions;

    public LiveEvents(ApplicationEventPublisher events, SimpMessagingTemplate messaging,
                      ExamSessionRepository sessions) {
        this.events = events;
        this.messaging = messaging;
        this.sessions = sessions;
    }

    public void changed(UUID sessionId) {
        events.publishEvent(new Changed(sessionId));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(Changed event) {
        UUID teacherId = sessions.findTeacherId(event.sessionId());
        messaging.convertAndSendToUser(teacherId.toString(), "/queue/monitor",
                Map.of("sessionId", event.sessionId()));
    }
}

