import { inject, Injectable, OnDestroy } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { firstValueFrom } from "rxjs";
import { API_URL } from "../../../core/config/api.config";
import { Page } from "../../../shared/utils/page";
import { PagedList } from "../../../shared/utils/paged-list";
import {
  emptyUserForm,
  UserFormValue,
} from "../../../shared/ui/user-form/user-form.model";
import type { Account } from "../models/account.model";

@Injectable()
export class UsersService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly url = inject(API_URL) + "/admin/users";
  readonly users = new PagedList((page, size) =>
    this.http.get<Page<Account>>(this.url, { params: { page, size } }),
  );
  newUser = emptyUserForm();

  async create(value: UserFormValue) {
    await firstValueFrom(this.http.post(this.url, value));
    this.newUser = emptyUserForm();
    await this.users.load();
  }
  ngOnDestroy() {
    this.users.reset();
  }
}
