import { inject, Injectable, OnDestroy, signal } from "@angular/core";
import { Client } from "@stomp/stompjs";
import { Subject } from "rxjs";
import { AuthSession } from "../../../core/auth/auth-session.service";

@Injectable()
export class MonitorLiveService implements OnDestroy {
  private readonly auth = inject(AuthSession);
  private client?: Client;
  private readonly updates = new Subject<string>();
  private readonly connections = new Subject<void>();
  readonly changed = this.updates.asObservable();
  readonly reconnected = this.connections.asObservable();
  readonly live = signal(false);

  connect() {
    if (this.client?.active) return;
    this.client = new Client({
      brokerURL:
        (location.protocol === "https:" ? "wss://" : "ws://") +
        location.host +
        "/ws",
      connectHeaders: { Authorization: "Bearer " + this.auth.token() },
      reconnectDelay: 5000,
      onConnect: () => {
        this.live.set(true);
        this.client!.subscribe("/user/queue/monitor", (message) => {
          try {
            const event = JSON.parse(message.body);
            if (typeof event.sessionId === "string")
              this.updates.next(event.sessionId);
          } catch {
            /* Mensagens inválidas não interrompem a assinatura. */
          }
        });
        this.connections.next();
      },
      onWebSocketClose: () => this.live.set(false),
      onStompError: () => this.live.set(false),
    });
    this.client.activate();
  }
  ngOnDestroy() {
    this.live.set(false);
    this.updates.complete();
    this.connections.complete();
    void this.client?.deactivate();
  }
}
