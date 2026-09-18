const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");

// DI, Signals e RxJS reais; apenas HTTP, storage e transporte STOMP simulados.
async function harness(t, role = "ALUNO", request = async () => undefined) {
  await import("@angular/compiler");
  const core = await import("@angular/core");
  const http = await import("@angular/common/http");
  const rxjs = await import("rxjs");
  const angular = {
    "@angular/core": core,
    "@angular/core/rxjs-interop": await import("@angular/core/rxjs-interop"),
    "@angular/common": await import("@angular/common"),
    "@angular/common/http": http,
    "@angular/forms": await import("@angular/forms"),
    "@angular/router": await import("@angular/router"),
    rxjs,
  };
  const storage = new Map();
  const clients = [];
  const document = {
    fullscreenElement: null,
    exitFullscreen: async () => {
      document.fullscreenElement = null;
    },
  };
  const context = vm.createContext({
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    console,
    document,
    crypto: globalThis.crypto,
    location: { protocol: "http:", host: "localhost" },
    sessionStorage: {
      getItem: (key) => storage.get(key),
      setItem: (key, value) => storage.set(key, value),
      removeItem: (key) => storage.delete(key),
    },
  });
  const modules = new Map();
  function load(name, parent = path.resolve("src/app/entry.ts")) {
    if (angular[name]) return angular[name];
    if (name === "@stomp/stompjs")
      return {
        Client: class {
          constructor(options) {
            this.options = options;
            clients.push(this);
          }
          activate() {
            this.active = true;
          }
          subscribe(destination, callback) {
            this.callback = callback;
          }
          async deactivate() {
            this.active = false;
            this.deactivated = true;
          }
        },
      };
    const file = path.resolve(path.dirname(parent), name + ".ts");
    if (modules.has(file)) return modules.get(file);
    const exports = {};
    modules.set(file, exports);
    const source = ts.transpileModule(fs.readFileSync(file, "utf8"), {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2022,
        experimentalDecorators: true,
      },
    }).outputText;
    vm.runInContext("(function(require,exports){" + source + "\n})", context, {
      filename: file,
    })((name) => load(name, file), exports);
    return exports;
  }
  const { AuthSession } = load("./core/auth/auth-session.service");
  const { FeedbackService } = load("./core/errors/feedback.service");
  const { API_URL } = load("./core/config/api.config");
  const session = {
    user: core.signal({ id: "user", name: "Test", role }),
    token: core.signal("test"),
  };
  session.authenticated = core.computed(
    () => !!session.user() && !!session.token(),
  );
  session.home = core.computed(() =>
    session.user()?.role === "ALUNO" ? "/student" : "/overview",
  );
  session.clear = () => {
    session.user.set(null);
    session.token.set("");
  };
  const calls = [];
  const send = (method, url, body, options) =>
    rxjs.defer(() => {
      const params = options?.params
        ? "?" + new URLSearchParams(options.params)
        : "";
      const call = { method, path: url.replace(/^\/api/, "") + params, body };
      calls.push(call);
      return Promise.resolve(request(call.path, method, body));
    });
  const mockHttp = {
    get: (url, options) => send("GET", url, undefined, options),
    post: (url, body) => send("POST", url, body),
    put: (url, body) => send("PUT", url, body),
  };
  const providers = [
    { provide: AuthSession, useValue: session },
    FeedbackService,
    { provide: API_URL, useValue: "/api" },
    { provide: http.HttpClient, useValue: mockHttp },
  ];
  const types = {};
  for (const [file, type] of [
    ["teaching/services/classes.service", "ClassesService"],
    ["teaching/services/assessments.service", "AssessmentsService"],
    ["teaching/services/sessions.service", "SessionsService"],
    ["teaching/services/monitor-live.service", "MonitorLiveService"],
    ["teaching/services/monitor.service", "MonitorService"],
    ["teaching/services/review.service", "ReviewService"],
    ["student/services/attempt-api.service", "AttemptApiService"],
    ["student/services/attempt-state.service", "AttemptState"],
    ["student/services/attempt-sync.service", "AttemptSync"],
  ]) {
    types[type] = load("./features/" + file)[type];
    providers.push(types[type]);
  }
  const injector = core.createEnvironmentInjector(providers);
  t.after(() => injector.destroy());
  return {
    get: (name) => injector.get(types[name]),
    load,
    core,
    http,
    rxjs,
    injector,
    session,
    storage,
    clients,
    document,
    calls,
    feedback: injector.get(FeedbackService),
  };
}
const page = (items, index = 0, total = items.length) => ({
  items,
  page: index,
  size: 10,
  total,
});
const active = () => ({
  id: "attempt",
  status: "EM_ANDAMENTO",
  deadline: new Date(Date.now() + 60000).toISOString(),
  server_now: new Date().toISOString(),
  questions: [{ id: "question", kind: "DISCURSIVA" }],
  answers: [],
  session: {},
  violations: 0,
});
module.exports = { harness, page, active };
