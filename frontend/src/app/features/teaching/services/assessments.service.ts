import { computed, inject, Injectable, OnDestroy, signal } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { firstValueFrom } from "rxjs";
import { API_URL } from "../../../core/config/api.config";
import { AuthSession } from "../../../core/auth/auth-session.service";
import { Page } from "../../../shared/utils/page";
import { PagedList } from "../../../shared/utils/paged-list";
import { SelectedOptions } from "../../../shared/utils/selected-options";
import type { Assessment, Question } from "../models/assessment.model";

@Injectable()
export class AssessmentsService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthSession);
  private readonly url = inject(API_URL) + "/teacher/assessments";
  private readonly selection = signal("");
  readonly sessionAssessmentId = signal("");
  private readonly selections = new SelectedOptions<Assessment>();
  private questionRequest = 0;
  readonly assessments = new PagedList(
    (page, size) =>
      this.http.get<Page<Assessment>>(this.url, { params: { page, size } }),
    undefined,
    (result) =>
      this.selections.remember(result.items, [
        this.assessmentId,
        this.sessionAssessmentId(),
      ]),
  );
  readonly assessmentOptions = computed(() =>
    this.selections.options(this.assessments.items(), [
      this.assessmentId,
      this.sessionAssessmentId(),
    ]),
  );
  readonly selectedAssessment = computed(() =>
    this.selections.get(this.assessmentId),
  );
  readonly questions = signal<Question[]>([]);
  title = "";
  question = { prompt: "", kind: "OBJETIVA", points: 1 };
  options = ["", ""];
  correct = 0;
  get assessmentId() {
    return this.selection();
  }
  set assessmentId(id: string) {
    this.selection.set(id);
  }

  async create() {
    const title = this.title.trim();
    const result = await firstValueFrom(
      this.http.post<{ id: string }>(this.url, { title }),
    );
    this.assessmentId = result.id;
    this.selections.set({
      id: result.id,
      title,
      teacher_id: this.auth.user()!.id,
      locked: false,
    });
    this.title = "";
    await this.assessments.load();
    await this.selectAssessment();
  }
  async selectAssessment() {
    const id = this.assessmentId;
    const request = ++this.questionRequest;
    this.questions.set([]);
    if (!id) return;
    const questions = await firstValueFrom(
      this.http.get<Question[]>(this.url + "/" + id + "/questions"),
    );
    if (request === this.questionRequest && id === this.assessmentId)
      this.questions.set(questions);
  }
  async addQuestion() {
    await firstValueFrom(
      this.http.post(this.url + "/" + this.assessmentId + "/questions", {
        ...this.question,
        alternatives:
          this.question.kind === "OBJETIVA"
            ? this.options.map((label, i) => ({
                label,
                correct: i === Number(this.correct),
              }))
            : [],
      }),
    );
    this.question = { prompt: "", kind: "OBJETIVA", points: 1 };
    this.options = ["", ""];
    this.correct = 0;
    await this.selectAssessment();
  }
  markLocked(id: string) {
    const assessment = this.selections.get(id);
    if (assessment) this.selections.set({ ...assessment, locked: true });
  }
  ngOnDestroy() {
    this.questionRequest++;
    this.assessments.reset();
  }
}
