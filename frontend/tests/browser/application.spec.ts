import { test, expect, Page } from "@playwright/test";

const user = (role: string) => ({ id: "user", name: "Pessoa de teste", role });
const paged = (items: unknown[]) => ({
  items,
  page: 0,
  size: 10,
  total: items.length,
});
const assessment = {
  id: "assessment",
  title: "Matemática",
  teacher_id: "user",
  locked: false,
};
const schoolClass = {
  id: "class",
  name: "Turma A",
  teacher_id: "user",
  students: 1,
};
const session = {
  id: "session",
  assessment_id: "assessment",
  class_id: "class",
  code: "ABC123",
  status: "PUBLICADA",
  title: "Matemática",
  class_name: "Turma A",
  starts_at: "2026-09-01T12:00:00Z",
  ends_at: "2026-10-01T12:00:00Z",
  duration_minutes: 60,
  max_violations: 3,
  violation_action: "REGISTRAR",
};
const question = {
  id: "question",
  assessment_id: "assessment",
  kind: "DISCURSIVA",
  prompt: "Explique sua resposta",
  points: 2,
  position: 1,
  alternatives: [],
};
const attempt = () => ({
  id: "attempt",
  status: "EM_ANDAMENTO",
  session,
  questions: [question],
  answers: [],
  occurrences: [],
  server_now: new Date().toISOString(),
  deadline: new Date(Date.now() + 600_000).toISOString(),
  violations: 0,
  score: { total: 0, pending: 0 },
  max_score: 2,
});

async function setup(page: Page, role?: string, resume = false) {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => {
    if (
      message.type() === "error" &&
      !message.text().startsWith("Failed to load resource:")
    )
      errors.push(message.text());
  });
  if (role)
    await page.addInitScript((identity) => {
      sessionStorage.setItem("token", "test-token");
      sessionStorage.setItem("user", JSON.stringify(identity));
    }, user(role));
  await page.routeWebSocket("**/ws", (socket) => {
    socket.onMessage((message) => {
      if (message.toString().startsWith("CONNECT"))
        socket.send("CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0");
    });
  });
  const writes: { path: string; body: any }[] = [];
  let finished = false;
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (request.method() !== "GET")
      writes.push({ path, body: request.postDataJSON() });
    let data: unknown = {};
    if (path === "/api/auth/login")
      data = { token: "test-token", user: user(role || "ADMIN") };
    else if (path === "/api/teacher/classes")
      data =
        request.method() === "GET" ? paged([schoolClass]) : { id: "class" };
    else if (path === "/api/teacher/assessments")
      data =
        request.method() === "GET" ? paged([assessment]) : { id: "assessment" };
    else if (path.endsWith("/questions"))
      data = request.method() === "GET" ? [question] : { id: "question" };
    else if (path === "/api/teacher/sessions")
      data = request.method() === "GET" ? paged([session]) : { id: "session" };
    else if (path.endsWith("/students"))
      data =
        request.method() === "GET"
          ? paged([
              { id: "student", name: "Aluno", email: "aluno@example.test" },
            ])
          : { id: "student" };
    else if (path.endsWith("/monitor"))
      data = paged([
        {
          student_id: "student",
          name: "Aluno",
          id: "attempt",
          status: "FINALIZADA",
          answered: 1,
          violations: 0,
        },
      ]);
    else if (path === "/api/teacher/attempts/attempt")
      data = {
        ...attempt(),
        status: "FINALIZADA",
        answers: [
          {
            question_id: "question",
            text_value: "Resposta",
            score: null,
            feedback: null,
          },
        ],
      };
    else if (path === "/api/admin/users")
      data =
        request.method() === "GET"
          ? paged([{ ...user("ALUNO"), email: "aluno@example.test" }])
          : { id: "new-user" };
    else if (path === "/api/student/attempts")
      data = paged([
        {
          id: "attempt",
          status: finished ? "FINALIZADA" : "EM_ANDAMENTO",
          title: "Matemática",
        },
      ]);
    else if (path === "/api/student/active-attempt")
      data = { id: resume ? "attempt" : null };
    else if (path.endsWith("/occurrences"))
      data = { status: "EM_ANDAMENTO", violations: 1 };
    else if (path.includes("/answers/")) data = { accepted: true };
    else if (path.endsWith("/submit")) {
      finished = true;
      data = { ...attempt(), status: "FINALIZADA" };
    } else if (
      path.startsWith("/api/student/attempts/") ||
      path === "/api/student/join"
    )
      data = { ...attempt(), status: finished ? "FINALIZADA" : "EM_ANDAMENTO" };
    await route.fulfill({ json: data });
  });
  return { errors, writes };
}

test("root loads login and login navigates to the admin overview", async ({
  page,
}) => {
  const { errors } = await setup(page);
  await page.goto("/");
  await expect(page).toHaveURL(/\/login$/);
  await expect(
    page.getByRole("heading", { name: "Entre na sua escola" }),
  ).toBeVisible();
  await page.getByLabel("E-mail", { exact: true }).fill("admin@example.test");
  await page.getByLabel("Senha", { exact: true }).fill("a-test-password");
  await page.getByRole("button", { name: /Entrar/ }).click();
  await expect(
    page.getByRole("heading", { name: "Visão geral", exact: true }),
  ).toBeVisible();
  expect(errors).toEqual([]);
});

for (const role of ["ADMIN", "PROFESSOR", "ALUNO"]) {
  test(`root restores ${role} and opens its initial page`, async ({ page }) => {
    const { errors } = await setup(page, role);
    await page.goto("/");
    await expect(page).toHaveURL(
      role === "ALUNO" ? /\/student$/ : /\/overview$/,
    );
    await expect(
      page.getByRole("heading", {
        name: role === "ALUNO" ? "Minhas avaliações" : "Visão geral",
        exact: true,
      }),
    ).toBeVisible();
    expect(errors).toEqual([]);
  });
}

test("invalid persisted identity falls back to login without redirect loops", async ({
  page,
}) => {
  await page.addInitScript(() => {
    sessionStorage.setItem("token", "test");
    sessionStorage.setItem("user", "{}");
  });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Entre na sua escola" }),
  ).toBeVisible();
});

test("all teacher pages and admin user registration render and preserve selections", async ({
  page,
}) => {
  const { errors, writes } = await setup(page, "ADMIN");
  await page.goto("/classes");
  await page.getByLabel("Selecione uma turma").selectOption("class");
  await expect(page.getByText("aluno@example.test")).toBeVisible();
  await page.getByRole("button", { name: "Avaliações", exact: true }).click();
  await page
    .getByRole("combobox", { name: "Avaliação", exact: true })
    .selectOption("assessment");
  await expect(page.getByText(question.prompt)).toBeVisible();
  await expect(
    page.getByRole("button", { name: /Adicionar alternativa/ }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Aplicações", exact: true }).click();
  await expect(page.getByText("ABC123")).toBeVisible();
  await page.getByRole("button", { name: "Acompanhar", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Ver / corrigir" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Ver / corrigir" }).click();
  await page.getByLabel("Nota (0 a 2)").fill("1.5");
  await page.getByRole("button", { name: "Salvar correção" }).click();
  await expect(page.getByText("Correção salva.")).toBeVisible();
  await page.getByRole("button", { name: "Usuários", exact: true }).click();
  await page.getByLabel("Nome", { exact: true }).fill("Novo usuário");
  await page.getByLabel("E-mail", { exact: true }).fill("novo@example.test");
  await page.getByLabel(/Senha inicial/).fill("a-test-password");
  await page
    .getByRole("button", { name: "Cadastrar usuário", exact: true })
    .click();
  await expect(page.getByText("Cadastro concluído.")).toBeVisible();
  expect(
    writes.some((write) => write.path === "/api/admin/users"),
  ).toBeTruthy();
  await page
    .getByRole("button", { name: "Turmas e alunos", exact: true })
    .click();
  await expect(page.getByLabel("Selecione uma turma")).toHaveValue("class");
  expect(errors).toEqual([]);
});

test("student restores draft, autosaves and submits the exam", async ({
  page,
}) => {
  const { errors, writes } = await setup(page, "ALUNO", true);
  await page.addInitScript(() =>
    sessionStorage.setItem(
      "draft:user:attempt",
      JSON.stringify({
        pending: { question: { alternativeId: null, text: "Meu rascunho" } },
        events: [],
      }),
    ),
  );
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Retome o modo prova" }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Sair da conta" })).toHaveCount(
    0,
  );
  await page.getByRole("button", { name: "Voltar à tela cheia" }).click();
  await expect(page.getByLabel("Sua resposta")).toHaveValue("Meu rascunho");
  await page.getByLabel("Sua resposta").fill("Resposta final");
  await expect(
    page.getByText("Respostas salvas", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: /Finalizar avaliação/ }).click();
  await page.getByRole("button", { name: "Confirmar entrega" }).click();
  await expect(
    page.getByText("AVALIAÇÃO ENTREGUE", { exact: true }),
  ).toBeVisible();
  expect(
    writes.some(
      (write) =>
        write.path.endsWith("/answers/question") &&
        write.body.text === "Resposta final",
    ),
  ).toBeTruthy();
  expect(writes.some((write) => write.path.endsWith("/submit"))).toBeTruthy();
  expect(errors).toEqual([]);
});

test("role guards keep a teacher out of administration and student pages", async ({
  page,
}) => {
  await setup(page, "PROFESSOR");
  await page.goto("/users");
  await expect(page).toHaveURL(/\/overview$/);
  await page.goto("/student");
  await expect(page).toHaveURL(/\/overview$/);
});

test("expired authentication returns to login and keeps the draft", async ({
  page,
}) => {
  await setup(page, "ALUNO");
  await page.addInitScript(() =>
    sessionStorage.setItem(
      "draft:user:attempt",
      '{"pending":{"question":{"text":"draft"}}}',
    ),
  );
  await page.route("**/api/student/active-attempt", (route) =>
    route.fulfill({ status: 401, json: {} }),
  );
  await page.goto("/");
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("alert")).toContainText("sessão expirou");
  expect(
    await page.evaluate(() => sessionStorage.getItem("draft:user:attempt")),
  ).toContain("draft");
});
