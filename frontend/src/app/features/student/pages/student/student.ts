import { Component, HostListener, inject, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { AuthSession } from "../../../../core/auth/auth-session.service";
import { AsyncAction } from "../../../../core/errors/async-action";
import { AppLayout } from "../../../../layout/app-layout";
import { Pagination } from "../../../../shared/ui/pagination/pagination";
import { AttemptApiService } from "../../services/attempt-api.service";
import { AttemptState } from "../../services/attempt-state.service";
import { AttemptSync } from "../../services/attempt-sync.service";
import { ExamRunner } from "../../components/exam-runner/exam-runner";
import { AttemptResult } from "../../components/attempt-result/attempt-result";

@Component({
  selector: "app-student",
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    AppLayout,
    Pagination,
    ExamRunner,
    AttemptResult,
  ],
  providers: [AttemptApiService, AttemptState, AttemptSync],
  templateUrl: "./student.html",
})
export class StudentPage implements OnInit {
  readonly state = inject(AttemptState);
  readonly sync = inject(AttemptSync);
  readonly action = new AsyncAction();
  private readonly auth = inject(AuthSession);
  code = "";
  ngOnInit() {
    void this.action.run(() => this.sync.load());
  }
  join() {
    return this.action.run(async () => {
      if (!document.fullscreenElement)
        await document.documentElement.requestFullscreen();
      this.state.fullscreen.set(!!document.fullscreenElement);
      try {
        await this.sync.join(this.code);
      } catch (error) {
        if (document.fullscreenElement) await document.exitFullscreen();
        throw error;
      }
    });
  }
  openAttempt(id: string) {
    return this.action.run(() => this.sync.openAttempt(id));
  }
  backToHistory() {
    return this.action.run(() => this.sync.backToHistory());
  }
  changePage(delta: number) {
    return this.action.run(() => this.sync.history.change(delta));
  }
  canLeave() {
    return (
      !this.auth.authenticated() ||
      (!this.state.active() && !this.action.busy())
    );
  }
  @HostListener("window:blur") blur() {
    this.sync.record("FOCO_PERDIDO");
  }
  @HostListener("document:visibilitychange") visibility() {
    if (document.hidden) this.sync.record("ABA_OCULTA");
  }
  @HostListener("document:fullscreenchange") fullscreenChanged() {
    this.state.fullscreen.set(!!document.fullscreenElement);
    if (!this.state.fullscreen()) this.sync.record("SAIDA_FULLSCREEN");
  }
  @HostListener("window:beforeunload", ["$event"]) beforeUnload(
    event: BeforeUnloadEvent,
  ) {
    if (this.state.active()) {
      this.state.persist();
      event.preventDefault();
      event.returnValue = "";
    }
  }
}
