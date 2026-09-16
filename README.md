# Avalia — MVP de avaliações escolares

Java 21 / Spring Boot 3.5, Angular 20, PostgreSQL, Flyway, Spring Security/JWT, WebSocket/STOMP e Nginx. Código em `backend/` e `frontend/`.

## Executar com Docker

Pré-requisitos: Docker Engine/Desktop **em execução**, com Compose v2.

1. Copie `.env.example` para `.env`.
2. Substitua `DB_PASSWORD`, `JWT_SECRET` (mínimo 32 bytes aleatórios) e `ADMIN_PASSWORD` (12–72 caracteres). Não use os exemplos em produção.
3. Execute na raiz:

```sh
docker compose up --build -d
docker compose logs -f backend
```

Abra http://localhost:8088 e entre com `ADMIN_EMAIL` / `ADMIN_PASSWORD`. O administrador é criado no primeiro início; mudar a variável não altera uma senha já cadastrada. Nenhum aluno/professor ou senha de demonstração é incluído nas migrations.

O banco usa volume persistente e não expõe portas. Para parar sem apagar dados: `docker compose down`.

## Primeiro fluxo

1. ADMIN: em **Usuários**, cadastre um PROFESSOR.
2. Entre como PROFESSOR. Em **Turmas e alunos**, crie uma turma e cadastre/matricule alunos. Para matricular um cadastro existente, use seu ID.
3. Em **Avaliações**, crie uma avaliação e adicione questões. Objetivas têm um único gabarito; discursivas têm pontuação e correção manual.
4. Em **Aplicações**, escolha avaliação/turma, período, duração e política de ocorrências. Crie e publique. A criação da aplicação congela as questões da avaliação.
5. Em outro perfil de navegador, entre como ALUNO matriculado e use o código. A entrada inicia a tentativa; reentradas recuperam a mesma tentativa.
6. O aluno responde em tela cheia, aguarda a confirmação do autosave e entrega. O professor acompanha e corrige em **Acompanhamento**. O aluno reabre/atualiza o resultado para ver a nota.

ADMIN também pode criar suas próprias turmas/avaliações. O painel pedagógico de cada usuário exibe somente os registros de sua autoria; ADMIN não assume automaticamente os registros dos professores.

## Desenvolvimento local

Requisitos: JDK 21, Maven 3.9+, Node 20.19+ ou 22.12+, npm e PostgreSQL. As versões compatíveis são descritas nas documentações oficiais de [Spring Boot](https://docs.spring.io/spring-boot/3.5/system-requirements.html) e [Angular](https://angular.dev/reference/versions).

Configure as variáveis de ambiente antes de iniciar o backend:

| Variável | Valor / finalidade |
| --- | --- |
| DB_URL | `jdbc:postgresql://localhost:5432/avaliacoes` |
| DB_USER | `avaliacoes` |
| DB_PASSWORD | Senha do banco — obrigatória |
| JWT_SECRET | Segredo de assinatura — obrigatório, ≥32 bytes |
| ADMIN_EMAIL | Padrão `admin@escola.local` |
| ADMIN_PASSWORD | Senha inicial obrigatória, 12–72 caracteres |
| APP_ORIGIN | Origem exata do frontend; local `http://localhost:4200` |
| SERVER_PORT | Opcional, padrão 8080 |

O Spring **não lê automaticamente** o arquivo `.env` fora do Docker Compose. Exporte as variáveis no terminal ou configure-as na IDE.

```sh
cd backend
mvn spring-boot:run
```

Em outro terminal:

```sh
cd frontend
npm ci
npm start
```

Abra http://localhost:4200. O servidor Angular encaminha `/api` e `/ws` para o backend. Não é necessário CORS aberto. Datas são enviadas em UTC e exibidas no fuso do navegador.

## Testes e compilação

```sh
cd frontend
npm ci
npm run build
```

Os testes HTTP de integração usam PostgreSQL real. Crie **um banco exclusivo de teste** e configure as variáveis acima apontando para ele, além de `RUN_INTEGRATION_TESTS=true`:

```sh
cd backend
mvn test
mvn package -DskipTests
```

Em PowerShell, use `$env:RUN_INTEGRATION_TESTS='true'`. Sem essa variável os testes de integração ficam explicitamente ignorados. Os testes inserem usuários e registros; não use banco de produção. A CI em `.github/workflows/ci.yml` executa os testes com PostgreSQL 17 e compila o frontend.

## Publicação em VPS

1. Instale Docker/Compose e envie o projeto com um `.env` próprio.
2. Defina `APP_ORIGIN=https://provas.seudominio.com` (sem barra final). Mantenha `BIND_ADDRESS=127.0.0.1` e `HTTP_PORT=8088`.
3. Execute `docker compose up --build -d`.
4. Configure o proxy HTTPS do servidor. Há um exemplo em `deploy/nginx-tls.conf.example`; substitua domínio e caminhos dos certificados existentes, valide com `nginx -t` e recarregue o Nginx.
5. Libere somente 80/443 no firewall da VPS. O proxy precisa encaminhar WebSocket. Verifique login, indicador **Ao vivo** e o modo prova no domínio final.

Para acesso HTTP temporário em rede local, ajuste `BIND_ADDRESS=0.0.0.0` e `APP_ORIGIN` para o endereço exato. Em produção use HTTPS; recursos de navegador como identificadores seguros e tela cheia dependem do ambiente e do navegador.

O rate limit de login fica no Nginx interno (5/minuto por IP, burst 10). Quando há proxy externo, configure confiança e IP real de acordo com a rede do seu servidor antes de uma aplicação com muitos acessos simultâneos.

Backup: use `pg_dump` no serviço `db` e mantenha cópia externa do dump e dos segredos. Antes de atualizar, faça backup e teste a restauração. Não use `docker compose down -v` se deseja conservar dados.

## Organização e limites operacionais

- `api/`: endpoints e validação; `security/`: JWT e senhas BCrypt; `service/`: regras e persistência Spring JDBC; `realtime/`: notificações privadas do professor.
- Flyway aplica as migrations na inicialização. As operações críticas usam transações e bloqueio da tentativa. O banco controla início/prazo, independentemente do relógio do aluno.
- O servidor encerra tentativas expiradas a cada 5 segundos e também valida prazo em cada escrita. Autosave após o prazo é recusado. A nota objetiva é calculada no servidor; gabaritos não são enviados aos alunos.
- Uma tentativa por aluno/aplicação. O código só funciona para matriculados. Respostas são imutáveis após entrega; notas discursivas podem ser corrigidas pelo professor.
- Autosave a cada segundo, serializado. Pendências e IDs de eventos ficam no `sessionStorage` desta aba para reenvio/recarregamento. Fechar a aba pode perder pendências. O tempo não pausa sem rede; respostas que não chegarem antes do prazo não são aceitas.
- Perda de foco, ocultação de aba e saída de fullscreen geram eventos. Eventos recebidos em até 2 segundos do primeiro contam como um episódio, mantendo todos os registros. Política: apenas registrar ou finalizar ao atingir o limite. Isso é detecção, **não bloqueio absoluto nem prova de fraude**. Um cliente adulterado pode omitir eventos.
- JWT dura 8 horas. Se expirar, o aluno precisa autenticar-se novamente e retomar a tentativa dentro do prazo.
- Acompanhamento via fila privada STOMP autenticada, com consulta HTTP de recuperação a cada 15 segundos. O broker em memória pressupõe **uma instância do backend**. Não escale horizontalmente sem adaptar o broker.
- MVP sem recuperação de senha por e-mail, importação em lote, anexos, edição/remoção de questões já salvas ou publicação automática na internet. Credenciais iniciais são entregues pela escola.

