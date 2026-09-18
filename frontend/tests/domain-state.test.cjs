const { test } = require("node:test");
const assert = require("node:assert/strict");
const { harness, page, active } = require("./support/harness.cjs");

test("status polling preserves local answers and exam content without fetching a full snapshot", async (t) => {
  const attempt = active();
  const h = await harness(t, "ALUNO", async () => ({
    id: attempt.id,
    status: attempt.status,
    deadline: attempt.deadline,
    server_now: new Date().toISOString(),
    violations: 2,
  }));
  const state = h.get("AttemptState");
  const sync = h.get("AttemptSync");
  state.setAttempt(attempt);
  state.change(attempt.questions[0], "unsaved");
  await sync.refreshAttemptStatus();
  assert.deepEqual(
    h.calls.map((call) => call.path),
    ["/student/attempts/attempt/status"],
  );
  assert.equal(state.pending.question.text, "unsaved");
  assert.equal(state.attempt().questions, attempt.questions);
  assert.equal(state.attempt().violations, 2);
});

test("completion fetches the full result once", async (t) => {
  const attempt = active();
  const h = await harness(t, "ALUNO", async () => ({
    ...attempt,
    status: "FINALIZADA",
    score: { total: 2, pending: 0 },
    max_score: 2,
  }));
  const state = h.get("AttemptState");
  state.setAttempt(attempt);
  await h.get("AttemptSync").refreshAttemptStatus();
  assert.deepEqual(
    h.calls.map((call) => call.path),
    ["/student/attempts/attempt/status", "/student/attempts/attempt"],
  );
  assert.equal(state.attempt().score.total, 2);
});

test("selected classes remain available when navigating to another page", async (t) => {
  const h = await harness(t, "PROFESSOR", async (path) =>
    path.includes("page=1")
      ? page([{ id: "second", name: "Second" }], 1, 26)
      : page([{ id: "first", name: "First" }], 0, 26),
  );
  const store = h.get("ClassesService");
  await store.classes.load();
  store.classId = "first";
  await store.classes.load(1);
  assert.equal(
    store
      .classOptions()
      .map((item) => item.id)
      .join(","),
    "first,second",
  );
  assert.equal(store.classes.data().total, 26);
  assert.equal(store.classes.items().length, 1);
});

test("a late response for a previous class cannot overwrite the selected class", async (t) => {
  let release;
  const h = await harness(t, "PROFESSOR", (path) =>
    path.includes("/old/")
      ? new Promise((resolve) => (release = resolve))
      : page([{ id: "new-student" }]),
  );
  const store = h.get("ClassesService");
  store.classId = "old";
  const old = store.students.load();
  store.classId = "new";
  await store.students.load();
  release(page([{ id: "old-student" }]));
  await old;
  assert.equal(store.students.items()[0].id, "new-student");
});

test("overview retains recent sessions when the applications list changes page", async (t) => {
  const h = await harness(t, "PROFESSOR", async (path) =>
    path.includes("page=1")
      ? page([{ id: "older" }], 1, 26)
      : page([{ id: "recent" }], 0, 26),
  );
  const store = h.get("SessionsService");
  await store.sessions.load();
  await store.sessions.load(1);
  assert.equal(store.sessions.items()[0].id, "older");
  assert.equal(store.recentSessions()[0].id, "recent");
  assert.equal(store.sessionOptions().length, 1);
});

test("WebSocket ignores other sessions and groups notifications for the selected session", async (t) => {
  const h = await harness(t, "PROFESSOR", async () => page([]));
  const monitor = h.get("MonitorService");
  const live = h.get("MonitorLiveService");
  monitor.monitorId = "selected";
  let schedules = 0;
  monitor.refresh = {
    schedule() {
      schedules++;
    },
    async flush() {},
    stop() {},
  };
  await monitor.activate();
  live.connect();
  h.clients[0].options.onConnect();
  schedules = 0;
  h.clients[0].callback({ body: JSON.stringify({ sessionId: "another" }) });
  assert.equal(schedules, 0);
  h.clients[0].callback({ body: JSON.stringify({ sessionId: "selected" }) });
  assert.equal(schedules, 1);
  monitor.deactivate();
  h.clients[0].callback({ body: JSON.stringify({ sessionId: "selected" }) });
  assert.equal(schedules, 1);
  live.ngOnDestroy();
  assert.equal(h.clients[0].deactivated, true);
});

test("resuming an active attempt does not depend on the first history page", async (t) => {
  const h = await harness(t, "ALUNO", async (path) =>
    path === "/student/active-attempt"
      ? { id: "older-active" }
      : path === "/student/attempts/older-active"
        ? { ...active(), id: "older-active" }
        : page([{ id: "recent", status: "FINALIZADA" }]),
  );
  await h.get("AttemptSync").load();
  assert.equal(h.get("AttemptState").attempt().id, "older-active");
});

test("an answer changed during autosave remains pending until its latest value is accepted", async (t) => {
  let release;
  const h = await harness(
    t,
    "ALUNO",
    () => new Promise((resolve) => (release = resolve)),
  );
  const state = h.get("AttemptState");
  const sync = h.get("AttemptSync");
  const attempt = active();
  state.setAttempt(attempt);
  state.change(attempt.questions[0], "first");
  const saving = sync.flush();
  state.change(attempt.questions[0], "latest");
  release({ accepted: true });
  await saving;
  assert.equal(state.pending.question.text, "latest");
  assert.equal(state.answers().question.text, "latest");
  assert.equal(
    JSON.parse(h.storage.get(state.draftKey)).pending.question.text,
    "latest",
  );
});

test("draft restoration keeps answer and occurrence IDs and does not change the deadline", async (t) => {
  const h = await harness(t);
  const state = h.get("AttemptState");
  const attempt = active();
  h.storage.set(
    "draft:user:attempt",
    JSON.stringify({
      pending: { question: { alternativeId: null, text: "draft" } },
      events: [{ id: "event-id", kind: "FOCO_PERDIDO" }],
    }),
  );
  state.setAttempt(attempt, true);
  assert.equal(state.answers().question.text, "draft");
  assert.equal(state.events[0].id, "event-id");
  assert.equal(state.attempt().deadline, attempt.deadline);
  assert.equal(state.answered(), 1);
});

test("a response after session expiration cannot modify or erase the saved draft", async (t) => {
  let release;
  const h = await harness(
    t,
    "ALUNO",
    () => new Promise((resolve) => (release = resolve)),
  );
  const state = h.get("AttemptState");
  const sync = h.get("AttemptSync");
  const attempt = active();
  state.setAttempt(attempt);
  state.change(attempt.questions[0], "draft");
  const key = state.draftKey;
  const saving = sync.flush();
  h.session.clear();
  release({ accepted: true });
  await saving;
  assert.equal(state.active(), false);
  assert.equal(JSON.parse(h.storage.get(key)).pending.question.text, "draft");
  assert.equal(h.storage.has("draft:undefined:attempt"), false);
});

test("submit refuses pending answers after a network failure", async (t) => {
  const h = await harness(t, "ALUNO", async () => {
    throw new Error("offline");
  });
  const state = h.get("AttemptState");
  const attempt = active();
  state.setAttempt(attempt);
  state.change(attempt.questions[0], "pending");
  await assert.rejects(h.get("AttemptSync").submit(), /envios pendentes/);
  assert.equal(
    h.calls.some((call) => call.path.endsWith("/submit")),
    false,
  );
});

test("session creation sends UTC dates and locks the selected assessment", async (t) => {
  const h = await harness(t, "PROFESSOR", async (path, method) =>
    method === "POST"
      ? { id: "session" }
      : page([{ id: "assessment", locked: true }]),
  );
  const assessments = h.get("AssessmentsService");
  assessments.selections.set({
    id: "assessment",
    title: "Test",
    teacher_id: "user",
    locked: false,
  });
  assessments.assessmentId = "assessment";
  const sessions = h.get("SessionsService");
  Object.assign(sessions.form, {
    assessmentId: "assessment",
    classId: "class",
    startsAt: "2026-09-20T10:00",
    endsAt: "2026-09-20T11:00",
  });
  await sessions.create();
  assert.equal(
    h.calls[0].body.startsAt,
    new Date("2026-09-20T10:00").toISOString(),
  );
  assert.equal(assessments.selectedAssessment().locked, true);
});
