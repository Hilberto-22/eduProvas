import { Component, inject, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { AsyncAction } from "../../../../core/errors/async-action";
import { FeedbackService } from "../../../../core/errors/feedback.service";
import { Pagination } from "../../../../shared/ui/pagination/pagination";
import { ClassesService } from "../../services/classes.service";
import { AssessmentsService } from "../../services/assessments.service";
import { SessionsService } from "../../services/sessions.service";

@Component({
  selector: "app-sessions",
  standalone: true,
  imports: [CommonModule, FormsModule, Pagination],
  templateUrl: "./sessions.html",
})
export class SessionsPage implements OnInit {
  readonly store = inject(SessionsService);
  readonly classes = inject(ClassesService);
  readonly assessments = inject(AssessmentsService);
  readonly action = new AsyncAction();
  private readonly router = inject(Router);
  private readonly feedback = inject(FeedbackService);
  ngOnInit() {
    void this.action.run(() =>
      Promise.all([
        this.store.sessions.load(),
        this.classes.classes.load(),
        this.assessments.assessments.load(),
      ]),
    );
  }
  createSession() {
    return this.action.run(async () => {
      await this.store.create();
      this.feedback.notice.set(
        "Aplicação criada. Publique para liberar o código aos alunos.",
      );
    });
  }
  publish(id: string) {
    return this.action.run(() => this.store.publish(id));
  }
  monitor(id: string) {
    return this.router.navigate(["/monitor"], { queryParams: { session: id } });
  }
  changePage(key: "classes" | "assessments" | "sessions", delta: number) {
    return this.action.run(() =>
      key === "classes"
        ? this.classes.classes.change(delta)
        : key === "assessments"
          ? this.assessments.assessments.change(delta)
          : this.store.sessions.change(delta),
    );
  }
}
