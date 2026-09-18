import { computed, inject, Injectable, OnDestroy, signal } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { firstValueFrom } from "rxjs";
import { API_URL } from "../../../core/config/api.config";
import { Page } from "../../../shared/utils/page";
import { PagedList } from "../../../shared/utils/paged-list";
import { SelectedOptions } from "../../../shared/utils/selected-options";
import {
  emptyUserForm,
  UserFormValue,
} from "../../../shared/ui/user-form/user-form.model";
import type { SchoolClass, Student } from "../models/school-class.model";

@Injectable()
export class ClassesService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly url = inject(API_URL) + "/teacher/classes";
  private readonly selection = signal("");
  readonly sessionClassId = signal("");
  private readonly selections = new SelectedOptions<SchoolClass>();
  readonly classes = new PagedList(
    (page, size) =>
      this.http.get<Page<SchoolClass>>(this.url, { params: { page, size } }),
    undefined,
    (result) =>
      this.selections.remember(result.items, [
        this.classId,
        this.sessionClassId(),
      ]),
  );
  readonly students = new PagedList(
    (page, size) =>
      this.http.get<Page<Student>>(
        this.url + "/" + this.classId + "/students",
        { params: { page, size } },
      ),
    () => this.classId,
  );
  readonly classOptions = computed(() =>
    this.selections.options(this.classes.items(), [
      this.classId,
      this.sessionClassId(),
    ]),
  );
  className = "";
  enrollmentId = "";
  newUser = emptyUserForm();
  get classId() {
    return this.selection();
  }
  set classId(id: string) {
    this.selection.set(id);
  }

  async create() {
    await firstValueFrom(this.http.post(this.url, { name: this.className }));
    this.className = "";
    await this.classes.load();
  }
  async selectClass() {
    this.students.reset();
    if (this.classId) await this.students.load(0);
  }
  async createStudent(value: UserFormValue) {
    await firstValueFrom(
      this.http.post(this.url + "/" + this.classId + "/students", {
        ...value,
        role: "ALUNO",
      }),
    );
    this.newUser = emptyUserForm();
    await Promise.all([this.selectClass(), this.classes.load()]);
  }
  async enroll() {
    await firstValueFrom(
      this.http.post(this.url + "/" + this.classId + "/enrollments", {
        studentId: this.enrollmentId,
      }),
    );
    await Promise.all([this.selectClass(), this.classes.load()]);
    this.enrollmentId = "";
  }
  ngOnDestroy() {
    this.classes.reset();
    this.students.reset();
  }
}
