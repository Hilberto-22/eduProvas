import { Component, inject, OnInit } from "@angular/core";
import { toSignal } from "@angular/core/rxjs-interop";
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterOutlet,
} from "@angular/router";
import { filter, map } from "rxjs";
import { AppLayout } from "../../../../layout/app-layout";
import { ClassesService } from "../../services/classes.service";
import { AssessmentsService } from "../../services/assessments.service";
import { SessionsService } from "../../services/sessions.service";
import { MonitorLiveService } from "../../services/monitor-live.service";
import { MonitorService } from "../../services/monitor.service";

@Component({
  selector: "app-teaching-shell",
  standalone: true,
  imports: [AppLayout, RouterOutlet],
  providers: [
    ClassesService,
    AssessmentsService,
    SessionsService,
    MonitorLiveService,
    MonitorService,
  ],
  template:
    '<app-layout [title]="title()" [live]="live.live()"><router-outlet /></app-layout>',
})
export class TeachingShell implements OnInit {
  readonly live = inject(MonitorLiveService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly title = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => {
        let route = this.route;
        while (route.firstChild) route = route.firstChild;
        return (route.snapshot?.data["heading"] as string) || "Visão geral";
      }),
    ),
    { initialValue: "Visão geral" },
  );
  ngOnInit() {
    this.live.connect();
  }
}
