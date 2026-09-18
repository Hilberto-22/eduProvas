import { Component, inject, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { Router, RouterLink } from "@angular/router";
import { AsyncAction } from "../../../../core/errors/async-action";
import { ClassesService } from "../../services/classes.service";
import { AssessmentsService } from "../../services/assessments.service";
import { SessionsService } from "../../services/sessions.service";

@Component({
  selector: "app-overview",
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: "./overview.html",
})
export class OverviewPage implements OnInit {
  readonly classes = inject(ClassesService);
  readonly assessments = inject(AssessmentsService);
  readonly sessions = inject(SessionsService);
  readonly action = new AsyncAction();
  private readonly router = inject(Router);
  ngOnInit() {
    void this.action.run(() =>
      Promise.all([
        this.classes.classes.load(),
        this.assessments.assessments.load(),
        this.sessions.sessions.load(),
      ]),
    );
  }
  monitor(id: string) {
    return this.router.navigate(["/monitor"], { queryParams: { session: id } });
  }
}
