const { test } = require("node:test");
const assert = require("node:assert/strict");
const { harness } = require("./support/harness.cjs");

test("bearer token is attached only to protected API requests", async (t) => {
  const h = await harness(t);
  const { authInterceptor } = h.load("./core/interceptors/auth.interceptor");
  for (const [url, expected] of [
    ["/api/student/attempts", "Bearer test"],
    ["/api/auth/login", null],
    ["https://other.test/data", null],
  ]) {
    let authorization;
    const request = new h.http.HttpRequest("GET", url);
    h.core.runInInjectionContext(h.injector, () =>
      authInterceptor(request, (outgoing) => {
        authorization = outgoing.headers.get("Authorization");
        return h.rxjs.EMPTY;
      }),
    );
    assert.equal(authorization, expected);
  }
});

test("401 on login preserves session, while protected 401 clears it and redirects", async (t) => {
  const h = await harness(t);
  const { Router } = await import("@angular/router");
  const navigations = [];
  const injector = h.core.createEnvironmentInjector(
    [
      {
        provide: Router,
        useValue: {
          navigateByUrl: (url) => {
            navigations.push(url);
            return Promise.resolve(true);
          },
        },
      },
    ],
    h.injector,
  );
  t.after(() => injector.destroy());
  const { apiErrorInterceptor } = h.load(
    "./core/interceptors/api-error.interceptor",
  );
  for (const url of ["/api/auth/login", "/api/student/attempts"]) {
    const response = h.core.runInInjectionContext(injector, () =>
      apiErrorInterceptor(new h.http.HttpRequest("GET", url), () =>
        h.rxjs.throwError(
          () => new h.http.HttpErrorResponse({ status: 401, error: {} }),
        ),
      ),
    );
    await assert.rejects(h.rxjs.firstValueFrom(response));
    assert.equal(h.session.authenticated(), url.endsWith("/login"));
  }
  assert.deepEqual(navigations, ["/login"]);
});

test("an old 401 response does not clear a new authenticated session", async (t) => {
  const h = await harness(t);
  const { Router } = await import("@angular/router");
  const injector = h.core.createEnvironmentInjector(
    [
      {
        provide: Router,
        useValue: { navigateByUrl: () => assert.fail("unexpected redirect") },
      },
    ],
    h.injector,
  );
  t.after(() => injector.destroy());
  const { apiErrorInterceptor } = h.load(
    "./core/interceptors/api-error.interceptor",
  );
  const source = new h.rxjs.Subject();
  const response = h.core.runInInjectionContext(injector, () =>
    apiErrorInterceptor(
      new h.http.HttpRequest("GET", "/api/teacher/classes"),
      () => source,
    ),
  );
  const pending = h.rxjs.firstValueFrom(response);
  h.session.token.set("new-token");
  source.error(new h.http.HttpErrorResponse({ status: 401 }));
  await assert.rejects(pending);
  assert.equal(h.session.authenticated(), true);
});

test("session restoration rejects corrupt JSON and unknown roles", async (t) => {
  const h = await harness(t);
  const { AuthSession } = h.load("./core/auth/auth-session.service");
  h.storage.set("token", "token");
  for (const value of [
    "invalid JSON",
    "{}",
    JSON.stringify({ id: "id", name: "name", role: "UNKNOWN" }),
  ]) {
    h.storage.set("user", value);
    assert.equal(new AuthSession().authenticated(), false);
  }
  h.storage.set(
    "user",
    JSON.stringify({ id: "id", name: "name", role: "ALUNO" }),
  );
  assert.equal(new AuthSession().home(), "/student");
});
