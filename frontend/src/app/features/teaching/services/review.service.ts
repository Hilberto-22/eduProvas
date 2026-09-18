import { inject, Injectable, OnDestroy, signal } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { firstValueFrom } from "rxjs";
import { API_URL } from "../../../core/config/api.config";
import type { Attempt } from "../../student/models/attempt.model";

@Injectable()
export class ReviewService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly url = inject(API_URL) + "/teacher/attempts";
  private request = 0;
  readonly review = signal<Attempt | null>(null);
  grades: Record<string, { score: number | null; feedback: string }> = {};

  async open(id: string) {
    const request = ++this.request;
    const review = await firstValueFrom(
      this.http.get<Attempt>(this.url + "/" + id),
    );
    if (request !== this.request) return;
    this.grades = {};
    for (const answer of review.answers)
      this.grades[answer.question_id] = {
        score: answer.score,
        feedback: answer.feedback || "",
      };
    this.review.set(review);
  }
  reviewAnswer(id: string) {
    return this.review()?.answers.find((answer) => answer.question_id === id);
  }
  async grade(id: string) {
    const reviewId = this.review()!.id;
    const request = this.request;
    await firstValueFrom(
      this.http.put<void>(
        this.url + "/" + reviewId + "/grades/" + id,
        this.grades[id],
      ),
    );
    const result = await firstValueFrom(
      this.http.get<Attempt>(this.url + "/" + reviewId),
    );
    if (request === this.request) this.review.set(result);
  }
  close() {
    this.request++;
    this.review.set(null);
    this.grades = {};
  }
  ngOnDestroy() {
    this.close();
  }
}
