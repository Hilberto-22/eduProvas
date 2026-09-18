# Arquitetura do frontend

## Diagnóstico antes da refatoração

O frontend usava Angular 20.3 e bootstrap standalone, sem NgModules próprios.
`src/app.ts` (299 linhas) e `src/app.html` (334 linhas) concentravam todos os
fluxos. A navegação era um campo `page`, sem Router, guards ou lazy loading.
`Api` concentrava fetch, JWT, sessionStorage e o callback de expiração.
`models.ts` misturava contratos de todos os domínios; `MonitorRefresh` já
serializava/agrupava atualizações e tinha testes próprios.

Dependências identificadas antes das alterações:

| Fluxo | Dados / dependências | Contratos existentes |
| --- | --- | --- |
| Login e sessão | Api, User, token/user em sessionStorage | /auth/login |
| Turmas e matrículas | SchoolClass, Student, cadastro de usuário | /teacher/classes, /students, /enrollments |
| Avaliações e questões | Assessment, Question, Alternative; bloqueio após aplicação | /teacher/assessments, /questions |
| Aplicações | Avaliação + turma selecionadas; datas locais convertidas para UTC | /teacher/sessions, /publish |
| Visão geral | Totais das três listagens e cinco aplicações recentes | Mesmas listagens paginadas |
| Acompanhamento e correção | Session, Monitor, Attempt; fila STOMP privada + fallback HTTP | /teacher/sessions/:id/monitor, /teacher/attempts/:id, /grades/:questionId |
| Usuários | Account, perfil ADMIN; formulário também usado no cadastro de aluno | /admin/users |
| Prova do aluno | Attempt, Question, AnswerInput, histórico, relógio do servidor e fullscreen | /student/active-attempt, /join, /attempts, /status, /answers, /occurrences, /submit |

Riscos de regressão: respostas atrasadas após mudar turma/página, seleção que sai
da página atual, aplicações recentes independentes da página consultada,
rascunhos por usuário/tentativa, respostas editadas durante autosave, retomada
fora da primeira página do histórico, finalização remota e agrupamento dos
eventos WebSocket. Os dez testes e o build de produção passaram na linha de base.

Os controllers, DTOs, regras de autorização, serviços de prova, proxy e Nginx
foram inspecionados. O servidor continua responsável por prazo, matrícula,
gabaritos, notas, congelamento de questões e política de ocorrências. O Nginx
já contém fallback para index.html, necessário para URLs do Angular Router.

## Organização adotada

O código Angular fica em `frontend/src/app`. `core` contém autenticação,
configuração HTTP, guards, interceptors e apresentação de erros globais.
`shared` contém somente UI e utilitários usados por mais de um domínio.
`layout` contém a estrutura visual comum, sem acesso às APIs dos domínios.

As features são `auth`, `teaching` (gestão pedagógica), `users` e `student`.
Dentro de teaching, turmas, avaliações, aplicações e monitoramento têm serviços
separados; páginas de rota são separadas dos componentes internos. As rotas
carregam features e páginas sob demanda. Não há NgModules próprios.

Referências consultadas: [organização e injeção no Angular 20](https://v20.angular.dev/style-guide)
e [rotas e lazy loading no Angular 20](https://v20.angular.dev/guide/routing/define-routes).

```text
src/app/
  app.ts, app.config.ts, app.routes.ts
  core/
    auth/          sessão JWT e identidade com Signals
    config/        URL base e timeout da API
    guards/        autenticação, perfil e saída da prova
    interceptors/  Bearer, timeout e erros HTTP
    errors/        feedback, operações de página e ErrorHandler
  shared/
    ui/            paginação e formulário de cadastro
    utils/         página, listagem paginada e opções selecionadas
  layout/          navegação, cabeçalho, mensagens e rodapé
  features/
    auth/          página de login, serviço e rotas
    teaching/      pages, components, services, models e rotas
    users/         página, serviço, modelo e rotas
    student/       página, componentes de prova/resultado, serviços, modelos e rotas
```

Não foram criadas pastas vazias para pipes/directives ou camadas sem uso.
Os contratos de Question/Session pertencem à gestão pedagógica e Attempt à
área do aluno. A correção e a prova reutilizam esses contratos por `import type`,
sem importar o código das páginas de outra feature ou gerar ciclos em runtime.

## Estado, I/O e ciclo de vida

- `AuthSession` guarda somente identidade e credenciais. O serviço de login
  chama a API; o interceptor funcional adiciona Bearer somente à API própria.
- Dados de listagem, seleção, conexão, tentativa e estado derivado usam Signals.
  Formulários template-driven mantêm os valores editáveis; não foi introduzida
  uma nova biblioteca de formulários nem gerenciamento de estado externo.
- HttpClient retorna Observables nos serviços. `firstValueFrom` mantém a
  sequência dos fluxos existentes; RxJS também trata timer, timeout, eventos de
  rota e notificações WebSocket. Escritas não recebem retries automáticos.
- Os serviços pedagógicos são fornecidos pelo componente de layout da feature:
  preservam as seleções entre suas páginas e são destruídos no logout. O
  formulário de usuários e os dados da prova são fornecidos nas suas páginas.
- `PagedList` ignora resultados fora de ordem ou de um contexto antigo;
  `SelectedOptions` retém a página atual e as opções selecionadas. Aplicações
  recentes ficam independentes da página consultada.
- `AttemptState` cuida de respostas, rascunho, cronômetro e derivados;
  `AttemptSync` cuida de autosave serializado, ocorrências, retomada, polling e
  entrega; `AttemptApiService` concentra os endpoints HTTP da prova.
- O cronômetro atualiza a cada segundo mesmo durante I/O. Continuam o polling
  leve a cada dez ticks e o fallback de monitoramento a cada 15 segundos.
  `MonitorRefresh` mantém agrupamento de 500 ms e ausência de sobreposição.
- DestroyRef/takeUntilDestroyed e ngOnDestroy encerram recursos; respostas
  atrasadas não podem sobrescrever rascunhos após uma troca de sessão.
- O guard de saída impede navegação interna durante prova ativa. A expiração
  permite ir ao login e preserva as pendências em sessionStorage. Guards
  controlam navegação; a autorização efetiva continua no backend.

## Rotas e correção do carregamento inicial

`/` redireciona para `/login`, `/overview` ou `/student`, conforme a sessão.
As URLs pedagógicas são `/classes`, `/assessments`, `/sessions`, `/monitor` e
`/users` (ADMIN). O acompanhamento aceita `?session=<id>`. O Nginx já suportava
acesso direto e recarregamento dessas URLs.

O teste de navegador reproduziu tela em branco no acesso à raiz com sessão de
professor/administrador: a primeira versão do layout lia `route.snapshot.data`
durante sua construção, antes de o Router fornecer o snapshot. O título agora
tem valor inicial seguro e só consulta o snapshot após `NavigationEnd`.
Identidades inválidas persistidas também são rejeitadas para evitar ciclos de
redirecionamento. Esses cenários fazem parte da suíte de navegador.

## Verificação

`npm test` executa regressões com DI, Signals e RxJS reais, simulando HTTP,
storage e transporte STOMP. Cobre os cenários anteriores e acrescenta autosave
concorrente, rascunhos, expiração, isolamento do Bearer e criação em UTC.
`npm run build` valida TypeScript e templates em modo de produção.

`npm run test:e2e` usa Playwright com API e WebSocket simulados: verifica a raiz
por perfil, login, sessão inválida, páginas pedagógicas, correção, cadastro,
seleções, guards, rascunho, fullscreen, autosave, entrega e expiração.
Não substitui os testes de integração com PostgreSQL real do backend.

Validação ao concluir a refatoração: 19 testes de lógica aprovados, 9 testes
de navegador aprovados no Edge acessando `http://localhost:4200`, build de
produção aprovado (328,89 kB iniciais, dentro do orçamento de 700 kB) e
`git diff --check` sem erros. As verificações de navegador usam os contratos
HTTP/STOMP simulados descritos acima; o backend não foi alterado.

Para executar os testes de navegador:

```sh
npx playwright install chromium
npm run test:e2e
```

Por padrão o teste inicia um servidor isolado na porta 4300. Para usar um
servidor já em execução, defina `E2E_BASE_URL`. No Windows com Edge instalado:

```powershell
$env:E2E_BASE_URL='http://localhost:4200'
$env:E2E_BROWSER_CHANNEL='msedge'
npm run test:e2e
```
