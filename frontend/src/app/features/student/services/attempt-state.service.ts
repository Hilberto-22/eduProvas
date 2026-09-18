import { computed, inject, Injectable, signal } from "@angular/core";
import { AuthSession } from "../../../core/auth/auth-session.service";
import { FeedbackService } from "../../../core/errors/feedback.service";
import type { AnswerInput, Attempt } from "../models/attempt.model";
import type { Question } from "../../teaching/models/assessment.model";

@Injectable()
export class AttemptState {
  private readonly auth = inject(AuthSession);
  private readonly feedback = inject(FeedbackService);
  readonly attempt = signal<Attempt | null>(null);
  readonly answers = signal<Record<string, AnswerInput>>({});
  readonly active = computed(
    () =>
      this.auth.authenticated() && this.attempt()?.status === "EM_ANDAMENTO",
  );
  readonly remaining = signal(0);
  readonly fullscreen = signal(!!document.fullscreenElement);
  readonly confirmSubmit = signal(false);
  readonly saveState = signal("Respostas salvas");
  readonly answered = computed(
    () =>
      Object.values(this.answers()).filter(
        (answer) => answer.alternativeId || answer.text?.trim(),
      ).length,
  );
  readonly timer = computed(
    () =>
      Math.floor(this.remaining() / 60)
        .toString()
        .padStart(2, "0") +
      ":" +
      (this.remaining() % 60).toString().padStart(2, "0"),
  );
  pending: Record<string, AnswerInput> = {};
  events: { id: string; kind: string }[] = [];
  private clockOffset = 0;
  get draftKey() {
    return "draft:" + this.auth.user()?.id + ":" + this.attempt()?.id;
  }

  setAttempt(attempt: Attempt, restore = false) {
    this.attempt.set(attempt);
    this.clockOffset = Date.parse(attempt.server_now) - Date.now();
    if (restore) {
      const answers: Record<string, AnswerInput> = {};
      this.pending = {};
      this.events = [];
      for (const answer of attempt.answers)
        answers[answer.question_id] = {
          alternativeId: answer.alternative_id,
          text: answer.text_value,
        };
      if (this.active()) {
        try {
          const draft = JSON.parse(
            sessionStorage.getItem(this.draftKey) || "{}",
          );
          this.pending = draft.pending || {};
          this.events = draft.events || [];
        } catch {
          /* Um rascunho inválido não impede recuperar os dados do servidor. */
        }
        Object.assign(answers, this.pending);
      }
      this.answers.set(answers);
    }
    if (!this.active()) {
      this.confirmSubmit.set(false);
      if (Object.keys(this.pending).length)
        this.feedback.error.set(
          "A prova foi encerrada. Alterações que não chegaram ao servidor não foram incluídas no resultado.",
        );
      this.pending = {};
      this.events = [];
      sessionStorage.removeItem(this.draftKey);
      if (document.fullscreenElement)
        void document.exitFullscreen().catch(() => {});
    }
    this.updateClock();
  }
  updateClock() {
    const attempt = this.attempt();
    if (attempt)
      this.remaining.set(
        Math.max(
          0,
          Math.ceil(
            (Date.parse(attempt.deadline) - Date.now() - this.clockOffset) /
              1000,
          ),
        ),
      );
  }
  change(question: Question, value: string) {
    const answer =
      question.kind === "OBJETIVA"
        ? { alternativeId: value, text: null }
        : { alternativeId: null, text: value };
    this.answers.update((answers) => ({ ...answers, [question.id]: answer }));
    this.pending[question.id] = { ...answer };
    this.saveState.set("Alterações aguardando envio");
    this.persist();
  }
  persist() {
    sessionStorage.setItem(
      this.draftKey,
      JSON.stringify({ pending: this.pending, events: this.events }),
    );
  }
}
