import { DestroyRef, inject, Injectable, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { exhaustMap, firstValueFrom, interval, tap } from "rxjs";
import { AuthSession } from "../../../core/auth/auth-session.service";
import { FeedbackService } from "../../../core/errors/feedback.service";
import { PagedList } from "../../../shared/utils/paged-list";
import { AttemptApiService } from "./attempt-api.service";
import { AttemptState } from "./attempt-state.service";

@Injectable()
export class AttemptSync {
  private readonly api = inject(AttemptApiService);
  private readonly state = inject(AttemptState);
  private readonly auth = inject(AuthSession);
  private readonly feedback = inject(FeedbackService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly token = this.auth.token();
  private ticks = 0;
  readonly flushing = signal(false);
  readonly history = new PagedList(
    (page, size) => this.api.history(page, size),
    () => this.auth.token(),
  );

  constructor() {
    interval(1000)
      .pipe(
        tap(() => this.state.updateClock()),
        exhaustMap(() => this.tick()),
        takeUntilDestroyed(),
      )
      .subscribe();
    this.destroyRef.onDestroy(() => this.history.reset());
  }
  private current(id?: string) {
    return (
      !this.destroyRef.destroyed &&
      this.auth.token() === this.token &&
      (!id || this.state.attempt()?.id === id)
    );
  }
  async load() {
    await this.history.load(0);
    const active = await firstValueFrom(this.api.active());
    if (this.current() && active.id) await this.openAttempt(active.id);
  }
  async join(code: string) {
    const attempt = await firstValueFrom(this.api.join(code));
    if (this.current()) this.state.setAttempt(attempt, true);
  }
  async openAttempt(id: string) {
    const attempt = await firstValueFrom(this.api.read(id));
    if (this.current()) this.state.setAttempt(attempt, true);
  }
  async refreshAttemptStatus() {
    const id = this.state.attempt()?.id;
    if (!id) return;
    const status = await firstValueFrom(this.api.status(id));
    if (!this.current(id) || !this.state.active()) return;
    if (status.status === "FINALIZADA") {
      const result = await firstValueFrom(this.api.read(id));
      if (this.current(id)) this.state.setAttempt(result);
    } else {
      // Preserva as edições locais e o conteúdo imutável da prova.
      this.state.setAttempt({ ...this.state.attempt()!, ...status });
    }
  }
  async flush() {
    if (this.flushing() || !this.state.active() || !this.current()) return;
    const id = this.state.attempt()!.id;
    this.flushing.set(true);
    try {
      while (this.state.events.length && this.state.active()) {
        const event = this.state.events[0];
        const result = await firstValueFrom(this.api.occurrence(id, event));
        if (!this.current(id)) return;
        this.state.events.shift();
        this.state.persist();
        this.state.attempt.update((attempt) =>
          attempt ? { ...attempt, violations: result.violations } : null,
        );
        if (result.status === "FINALIZADA") {
          await this.refreshResult(id);
          break;
        }
      }
      for (const [questionId, value] of Object.entries(this.state.pending)) {
        if (!this.state.active()) break;
        const result = await firstValueFrom(
          this.api.save(id, questionId, value),
        );
        if (!this.current(id)) return;
        if (!result.accepted) {
          await this.refreshResult(id);
          break;
        }
        if (
          JSON.stringify(this.state.pending[questionId]) ===
          JSON.stringify(value)
        )
          delete this.state.pending[questionId];
        this.state.persist();
      }
      this.state.saveState.set(
        Object.keys(this.state.pending).length
          ? "Alterações aguardando envio"
          : "Respostas salvas",
      );
    } catch {
      this.state.saveState.set(
        "Sem confirmação do servidor. Tentando novamente…",
      );
    } finally {
      this.flushing.set(false);
    }
  }
  private async refreshResult(id: string) {
    const result = await firstValueFrom(this.api.read(id));
    if (this.current(id)) this.state.setAttempt(result);
  }
  record(kind: string) {
    if (!this.state.active()) return;
    this.state.events.push({ id: crypto.randomUUID(), kind });
    this.state.persist();
    this.feedback.notice.set("Saída do modo prova detectada e registrada.");
    void this.flush();
  }
  private async tick() {
    this.ticks++;
    if (!this.state.active()) return;
    await this.flush();
    if (!this.state.active()) return;
    if (this.ticks % 10 === 0 || this.state.remaining() === 0) {
      try {
        await this.refreshAttemptStatus();
      } catch {
        this.state.saveState.set(
          "Conexão indisponível. O prazo continua correndo no servidor.",
        );
      }
    }
  }
  async submit() {
    if (this.flushing())
      throw new Error("Aguarde o salvamento em andamento e tente novamente.");
    await this.flush();
    if (Object.keys(this.state.pending).length || this.state.events.length)
      throw new Error(
        "Há envios pendentes. Reconecte e aguarde o salvamento antes de finalizar.",
      );
    if (this.state.active()) {
      const id = this.state.attempt()!.id;
      const attempt = await firstValueFrom(this.api.submit(id));
      if (this.current(id)) this.state.setAttempt(attempt);
    }
    if (this.current()) await this.history.load();
  }
  async backToHistory() {
    this.state.attempt.set(null);
    await this.history.load();
  }
}
