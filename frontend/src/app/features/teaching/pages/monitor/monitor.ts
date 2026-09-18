import { Component, inject, OnDestroy, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute } from "@angular/router";
import { AsyncAction } from "../../../../core/errors/async-action";
import { Pagination } from "../../../../shared/ui/pagination/pagination";
import { AttemptReview } from "../../components/attempt-review/attempt-review";
import { MonitorService } from "../../services/monitor.service";
import { ReviewService } from "../../services/review.service";
import { SessionsService } from "../../services/sessions.service";

@Component({
  selector: "app-monitor",
  standalone: true,
  imports: [CommonModule, FormsModule, Pagination, AttemptReview],
  providers: [ReviewService],
  templateUrl: "./monitor.html",
})
export class MonitorPage implements OnInit, OnDestroy {
  readonly store = inject(MonitorService);
  readonly sessions = inject(SessionsService);
  readonly action = new AsyncAction();
  private readonly review = inject(ReviewService);
  private readonly route = inject(ActivatedRoute);
  ngOnInit() {
    void this.action.run(async () => {
      const id = this.route.snapshot.queryParamMap.get("session");
      if (id) {
        this.store.monitorId = id;
        await this.store.selectMonitor();
      }
      await Promise.all([this.sessions.sessions.load(), this.store.activate()]);
    });
  }
  selectMonitor() {
    this.review.close();
    return this.action.run(() => this.store.selectMonitor());
  }
  openReview(id: string) {
    return this.action.run(() => this.review.open(id));
  }
  changePage(key: "sessions" | "roster", delta: number) {
    return this.action.run(() =>
      key === "sessions"
        ? this.sessions.sessions.change(delta)
        : this.store.changePage(delta),
    );
  }
  ngOnDestroy() {
    this.store.deactivate();
  }
}
