import { computed, inject, Injectable, OnDestroy, signal } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { firstValueFrom } from "rxjs";
import { API_URL } from "../../../core/config/api.config";
import { Page } from "../../../shared/utils/page";
import { PagedList } from "../../../shared/utils/paged-list";
import { SelectedOptions } from "../../../shared/utils/selected-options";
import { emptySessionForm, Session } from "../models/session.model";
import { AssessmentsService } from "./assessments.service";
import { ClassesService } from "./classes.service";

@Injectable()
export class SessionsService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly url = inject(API_URL) + "/teacher/sessions";
  private readonly assessments = inject(AssessmentsService);
  private readonly classes = inject(ClassesService);
  private readonly selections = new SelectedOptions<Session>();
  readonly monitorId = signal("");
  readonly recentSessions = signal<Session[]>([]);
  readonly sessions = new PagedList(
    (page, size) =>
      this.http.get<Page<Session>>(this.url, { params: { page, size } }),
    undefined,
    (result) => {
      this.selections.remember(result.items, [this.monitorId()]);
      if (result.page === 0) this.recentSessions.set(result.items.slice(0, 5));
    },
  );
  readonly sessionOptions = computed(() =>
    this.selections.options(this.sessions.items(), [this.monitorId()]),
  );
  readonly form = emptySessionForm();

  selectClass(id: string) {
    this.form.classId = id;
    this.classes.sessionClassId.set(id);
  }
  selectAssessment(id: string) {
    this.form.assessmentId = id;
    this.assessments.sessionAssessmentId.set(id);
  }

  async create() {
    const assessmentId = this.form.assessmentId;
    await firstValueFrom(
      this.http.post(this.url, {
        ...this.form,
        startsAt: new Date(this.form.startsAt).toISOString(),
        endsAt: new Date(this.form.endsAt).toISOString(),
      }),
    );
    this.assessments.markLocked(assessmentId);
    await Promise.all([
      this.sessions.load(0),
      this.assessments.assessments.load(),
    ]);
  }
  async publish(id: string) {
    await firstValueFrom(
      this.http.post<void>(this.url + "/" + id + "/publish", {}),
    );
    await this.sessions.load();
  }
  ngOnDestroy() {
    this.sessions.reset();
  }
}
