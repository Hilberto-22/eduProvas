# Consultas: implementação e validação

## Mudanças

1. **Questões e alternativas em lote.** `AssessmentRepository.findQuestions` faz duas consultas para uma avaliação com questões: uma para as questões e outra para todas as alternativas. Agrupa os resultados por questão, preserva a ordem das alternativas e omite o gabarito nas consultas do aluno. Avaliações vazias fazem apenas uma consulta.
2. **Atualização leve da tentativa.** `GET /api/student/attempts/{id}/status` retorna somente `id`, `status`, `deadline`, `server_now`, `finish_reason` e `violations`. O cliente mantém questões, alternativas e rascunhos locais durante a prova. Ao detectar finalização, busca o resultado completo. O endpoint continua verificando propriedade e prazo no servidor.
3. **Acompanhamento com atualizações agrupadas.** O cliente considera somente notificações da aplicação selecionada, agrupa rajadas em uma janela de 500 ms e permite uma requisição por vez, preservando uma atualização posterior caso cheguem eventos durante uma requisição. O polling de recuperação permanece em aproximadamente 15 segundos, apenas com a tela de acompanhamento aberta. Respostas atrasadas de outra seleção são descartadas.
4. **Paginação no banco e na interface.** Usuários, turmas, alunos matriculados, avaliações, aplicações, acompanhamento e histórico recebem `page` (início em zero) e `size` (padrão 25, máximo 100). Retornam `{items, page, size, total}`. A ordenação inclui ID para desempatar. A interface tem botões Anterior/Próxima, mostra totais reais e mantém opções já selecionadas ao trocar páginas. A retomada de prova usa `/api/student/active-attempt`, independentemente da página do histórico. Questões da prova e detalhes de uma tentativa continuam completos.
5. **Índices.** A migration V4 adiciona índices para professor/nome da turma, professor/título da avaliação, aplicações por avaliação, histórico por aluno/data, usuários por nome e ocorrências por tentativa/data. Os índices complementam as chaves e índices já existentes.
6. **Bloqueios mais curtos.** A entrada não bloqueia a aplicação inteira: a unicidade `(session_id,student_id)` e `ON CONFLICT DO NOTHING` resolvem entradas concorrentes do mesmo aluno. Escritas continuam bloqueando a tentativa e verificando autorização e prazo. A montagem de respostas completas ocorre após liberar o bloqueio, em uma transação de leitura `REPEATABLE_READ`, para evitar uma resposta com versões incompatíveis. O encerramento automático processa até dez lotes de cem tentativas, cada lote em sua própria transação, com `SKIP LOCKED`.
7. **Contratos explícitos.** Controllers retornam records tipados de `Responses` e `PageResponse`. As consultas selecionam colunas explicitamente. O frontend usa interfaces e chamadas HTTP genéricas. Os mapas permanecem como representação interna nos repositórios/services; não são expostos diretamente pelas respostas de sucesso dos controllers.

## Evidência de redução de trabalho

Para uma prova ativa com 20 questões, antes do vencimento:

| Operação | Antes | Depois |
|---|---:|---:|
| Buscar questões e alternativas | 21 consultas | 2 consultas |
| Atualização periódica da tentativa | 32 comandos SQL, resposta completa | 5 comandos SQL, resposta de estado |

Os números contam chamadas SQL do código, sem incluir controle de transação. Vencimento, entrega e notificações podem acrescentar comandos. Os cinco comandos da atualização leve são bloqueio autorizado, consulta de horário para validar prazo, atualização de presença, horário da resposta e contagem de ocorrências.

## Planos de execução

Executado localmente em PostgreSQL 16, em uma instância temporária isolada. O script `backend/src/test/resources/query-plans.sql` cria dados sintéticos dentro de uma transação: mil professores, mil alunos, cinquenta mil turmas, avaliações, aplicações e tentativas. Compara as mesmas consultas paginadas com os índices V4 e sem quatro desses índices, e termina com rollback, restaurando dados e índices. A base também continha os pequenos registros dos testes de integração.

| Consulta, primeira página com 25 registros | Sem os índices comparados | Com os índices |
|---|---:|---:|
| Turmas do professor | 3,904 ms | 0,259 ms |
| Avaliações do professor e existência de aplicação | 15,806 ms | 0,227 ms |
| Histórico do aluno | 4,468 ms | 0,201 ms |

Os planos passaram de varreduras sequenciais nos filtros principais para buscas por índice. O relatório bruto está em `docs/query-plans.txt`. Esta é uma execução sintética, sujeita a cache e distribuição dos dados; não representa latência HTTP nem teste de carga e não garante os mesmos ganhos em produção. Os índices de usuários e ocorrências não participaram desta comparação de tempos.

Para repetir após aplicar as migrations, use exclusivamente um banco de teste:

```sh
psql "$TEST_DATABASE_URL" -X -f backend/src/test/resources/query-plans.sql
```

## Validação e compatibilidade

- Testes do backend cobrem autorização, ocultação de gabarito, correção, prazo, ocorrências, WebSocket, paginação, retorno leve, entradas simultâneas e salvamento enquanto uma leitura completa permanece aberta.
- Um teste verifica que vinte questões são carregadas com exatamente duas chamadas JDBC.
- `npm test` verifica agrupamento, requisições sem sobreposição, recuperação após falhas, preservação de respostas locais, resultado após finalização, seleção entre páginas, respostas atrasadas e retomada fora da primeira página do histórico. São testes da lógica TypeScript, sem renderização de navegador.
- A compilação Angular valida os templates e os contratos usados pela interface. Não houve validação visual em navegador nesta sessão porque não havia navegador conectado à ferramenta.
- **Mudança de contrato:** listagens que retornavam arrays agora retornam um objeto paginado. Frontend e backend devem ser atualizados juntos; outros consumidores dessas rotas precisam ler `items` e navegar por `page`/`size`.
- Flyway aplica V4 no próximo início do backend. As migrations foram executadas somente no banco temporário de testes nesta sessão. A criação de índices pode bloquear escritas enquanto executa; bases grandes exigem planejamento da janela de atualização.
- Paginação usa `LIMIT/OFFSET`; páginas muito distantes e contagens totais ainda podem ser custosas. Dados reais e testes de carga devem orientar eventual paginação por cursor e ajustes adicionais.
