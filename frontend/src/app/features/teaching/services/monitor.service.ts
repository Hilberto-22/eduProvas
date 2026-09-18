import { inject, Injectable, OnDestroy } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { filter, interval, Subscription } from "rxjs";
import { API_URL } from "../../../core/config/api.config";
import { Page } from "../../../shared/utils/page";
import { PagedList } from "../../../shared/utils/paged-list";
import type { Monitor } from "../models/session.model";
import { MonitorRefresh } from "./monitor-refresh";
import { MonitorLiveService } from "./monitor-live.service";
import { SessionsService } from "./sessions.service";

@Injectable()
export class MonitorService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly url = inject(API_URL) + "/teacher/sessions";
  private readonly sessions = inject(SessionsService);
  private readonly live = inject(MonitorLiveService);
  private readonly subscriptions = new Subscription();
  private enabled = false;
  private monitorPage = 0;
  readonly roster = new PagedList(
    (page, size) =>
      this.http.get<Page<Monitor>>(
        this.url + "/" + this.monitorId + "/monitor",
        { params: { page, size } },
      ),
    () => this.monitorId + ":" + this.monitorPage + ":" + this.enabled,
  );
  private readonly refresh = new MonitorRefresh(async () => {
    if (this.enabled && this.monitorId)
      await this.roster.load(this.monitorPage);
  });
  get monitorId() {
    return this.sessions.monitorId();
  }
  set monitorId(id: string) {
    this.sessions.monitorId.set(id);
  }

  constructor() {
    this.subscriptions.add(
      this.live.changed
        .pipe(filter((id) => id === this.monitorId))
        .subscribe(() => this.schedule()),
    );
    this.subscriptions.add(
      this.live.reconnected.subscribe(() => this.schedule()),
    );
    this.subscriptions.add(interval(15_000).subscribe(() => this.schedule()));
  }
  async activate() {
    this.enabled = true;
    await this.refresh.flush();
  }
  deactivate() {
    this.enabled = false;
    this.roster.reset();
  }
  async selectMonitor() {
    this.monitorPage = 0;
    this.roster.reset();
    await this.refresh.flush();
  }
  async changePage(delta: number) {
    const page = this.roster.data();
    const next = page.page + delta;
    if (next < 0 || next * page.size >= page.total) return;
    this.monitorPage = next;
    await this.refresh.flush();
  }
  private schedule() {
    if (this.enabled && this.monitorId) this.refresh.schedule();
  }
  ngOnDestroy() {
    this.deactivate();
    this.refresh.stop();
    this.subscriptions.unsubscribe();
  }
}
