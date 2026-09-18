import { inject, Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { API_URL } from "../../../core/config/api.config";
import type { Page } from "../../../shared/utils/page";
import type {
  AnswerInput,
  Attempt,
  AttemptStatus,
  History,
} from "../models/attempt.model";

@Injectable()
export class AttemptApiService {
  private readonly http = inject(HttpClient);
  private readonly url = inject(API_URL) + "/student";
  history(page: number, size: number) {
    return this.http.get<Page<History>>(this.url + "/attempts", {
      params: { page, size },
    });
  }
  active() {
    return this.http.get<{ id: string | null }>(this.url + "/active-attempt");
  }
  join(code: string) {
    return this.http.post<Attempt>(this.url + "/join", { code });
  }
  read(id: string) {
    return this.http.get<Attempt>(this.url + "/attempts/" + id);
  }
  status(id: string) {
    return this.http.get<AttemptStatus>(
      this.url + "/attempts/" + id + "/status",
    );
  }
  save(id: string, questionId: string, answer: AnswerInput) {
    return this.http.put<{ accepted: boolean }>(
      this.url + "/attempts/" + id + "/answers/" + questionId,
      answer,
    );
  }
  occurrence(id: string, event: { id: string; kind: string }) {
    return this.http.post<{ status: string; violations: number }>(
      this.url + "/attempts/" + id + "/occurrences",
      event,
    );
  }
  submit(id: string) {
    return this.http.post<Attempt>(
      this.url + "/attempts/" + id + "/submit",
      {},
    );
  }
}
