# Plano de Implementação do MVP

## 1. Objetivo

Entregar um aplicativo financeiro familiar para Android e iOS usando
Kotlin Multiplatform e Compose Multiplatform. O primeiro MVP é local e
offline, com uma família e um usuário `OWNER` configurados no dispositivo.

Este plano divide o trabalho em tarefas pequenas, delegáveis e
verificáveis. Ele complementa [PROJECT_RULES.md](PROJECT_RULES.md),
[ARCHITECTURE.md](ARCHITECTURE.md) e [DATABASE.md](DATABASE.md).

## 2. Escopo do MVP

### Incluído

- Configuração inicial local do usuário OWNER e da família.
- Categorias; o gerenciamento de contas fica fora da primeira versão.
- Despesas avulsas e pagamentos integrais ou parciais.
- Parcelamentos com `InstallmentPlan → Installment → Expense → Payment`.
- Recorrências sem data final, encerradas por cancelamento.
- Persistência SQLite local e uso offline.
- Navegação e estados de UI necessários aos fluxos acima.

### Fora do MVP

- Autenticação remota, backend e sincronização entre dispositivos.
- Dashboard, projeção futura e área de histórico dedicada.
- Faturas/cartão de crédito completos, Open Finance, investimentos,
  recebíveis, orçamento, metas e multi-família SaaS.

## 3. Regras aprovadas

- `Expense` é a fonte única de valor, vencimento e status. `Installment`
  guarda apenas a posição no plano e referencia uma `Expense`.
- Despesas, recorrências e planos parcelados têm valores maiores que
  zero. A quantidade de parcelas não pode exceder o total em centavos,
  garantindo pelo menos um centavo por parcela.
- Saldo = obrigação + juros + multa - descontos - pagamentos,
  agregados sobre os pagamentos da obrigação.
- Rejeitar pagamento acima do saldo atualizado. No MVP, não cancelar uma
  obrigação que já tenha pagamentos; uma obrigação cancelada não pode
  receber pagamentos.
- Saldo zero implica `PAGA`; saldo positivo com vencimento anterior à
  data atual implica `VENCIDA`; nos demais casos, `A_VENCER`. `CANCELADA`
  é terminal. Recalcular status ao abrir/retomar o app e antes de
  consultas que dependam dele.
- Datas mensais são calculadas a partir da referência original. Se o dia
  não existir no mês, usar o último dia daquele mês e retornar ao dia
  original no seguinte, sem deriva.
- Recorrências não têm `end_date` no MVP e terminam por cancelamento.
  `(recurrence_id, due_date)` identifica uma ocorrência e impede
  duplicidade.
- O domínio valida que todas as referências financeiras pertencem à
  família ativa.

## 4. Trabalho com agentes

- **Coordenador:** mantém o contexto, prepara tarefas, controla
  dependências, integra alterações e executa as validações.
- **Executor:** implementa uma tarefa delimitada, sem alterar contratos
  de domínio ou schema por decisão própria.
- **Revisor:** analisa o diff e os critérios de aceite, verificando
  arquitetura, regras financeiras, testes e impacto Android/iOS.
- Tarefas dependentes são sequenciais. Paralelizar somente trabalho
  independente, com contratos aprovados e áreas de escrita separadas.
  Não delegar a dois agentes a mesma migration, módulo compartilhado ou
  documento ao mesmo tempo.

### Conteúdo obrigatório de cada tarefa

1. Objetivo e contexto nos documentos de referência.
2. Pré-requisitos e dependências concluídas.
3. Arquivos/pacotes permitidos e limites do escopo.
4. Comportamento esperado e casos-limite.
5. Testes e comandos de validação.
6. Critérios de aceite e Definition of Done.
7. Relato do executor: resumo, diff, testes executados, limitações e
   decisões ainda pendentes.

## 5. Fases e tarefas

### Fase 0: Contratos e decisões técnicas

**0.1 Alinhar documentos do projeto (concluída)**
- PROJECT_RULES, ARCHITECTURE e DATABASE foram alinhados ao escopo,
  domínio e schema aprovados.
- Aceite verificado: sem divergências sobre parcela, saldo, status,
  valores, recorrência, usuário inicial ou sincronização.

**0.2 Escolher driver SQLite KMP e migrations**
- Comparar alternativas mantidas e compatíveis com Android/iOS; verificar
  migrations, foreign keys, testes compartilhados e integração com Kotlin
  e Gradle.
- Aceite: decisão e justificativa registradas antes de adicionar a
  dependência.
- Decisão: SQLDelight 2.1.0, com runtime e queries no `commonMain`,
  `android-driver` no `androidMain` e `native-driver` no `iosMain`.
  A definição inicial fica em `.sq`; alterações futuras serão versionadas
  em arquivos `.sqm` e verificadas pelo Gradle plugin.
- Justificativa: mantém SQL e mapeamento tipados no código compartilhado,
  enquanto usa os drivers SQLite nativos de cada plataforma; o driver
  JDBC é usado somente para testes Android-host no Windows.
- A compilação/testes Android-host passaram no Windows. A validação do
  alvo iOS permanece no gate macOS da tarefa 9.3.

**0.3 Definir matriz de build**
- Registrar requisitos de JDK, Android SDK, Gradle, macOS e Xcode.
- Aceite: comandos locais e CI documentados; validação iOS atribuída a
  runner macOS.

### Fase 1: Bootstrap multiplataforma

**1.1 Criar estrutura KMP/Compose mínima**
- Configurar alvos Android e iOS e um app inicial compartilhado.
- Evitar módulos vazios para funcionalidades futuras.
- Aceite: build Android passa e o projeto iOS compila em macOS.

**1.2 Configurar testes compartilhados e CI**
- Configurar execução de testes shared, build Android e validação do alvo
  iOS em macOS.
- Aceite: pipeline roda os gates definidos sem depender de serviços
  externos.

### Fase 2: Tipos e regras de domínio

**2.1 Tipos comuns**
- Definir dinheiro em centavos, data civil, timestamps UTC, UUID,
  resultado e erros sem dependência de UI/Android.
- Aceite: validações e conversões testadas; nenhum `Float`/`Double` para
  dinheiro.

**2.2 Saldo, pagamento e status**
- Implementar agregação de pagamentos/ajustes e transições de status.
- Aceite: testar saldo sem pagamento, parcial, quitado, vencido,
  cancelado, ajustes, excedente e tentativa de pagamento cancelado.

**2.3 Datas mensais**
- Implementar datas ancoradas na referência original para parcelas e
  recorrências mensal, trimestral e anual.
- Aceite: testar dias 28–31, fevereiro bissexto e retorno ao dia original
  após mês curto.

**2.4 Distribuição de parcelas**
- Distribuir centavos de forma determinística, com soma exata do plano.
- Aceite: testar total/contagem positivos, limite de uma parcela por
  centavo, arredondamento e casos-limite.

**2.5 Geração idempotente de recorrências**
- Gerar despesas em janela controlada sem alterar ocorrências existentes.
- Aceite: segunda execução não duplica; regra cancelada não gera novas
  ocorrências.

### Fase 3: Persistência local

**3.1 Criar schema e migrations**
- Implementar as dez tabelas oficiais com constraints, índices, UUID em
  TEXT, datas civis em `YYYY-MM-DD` e valores monetários em INTEGER.
- Aceite: migration desde banco vazio, foreign keys habilitadas, sem
  `expenses.installment_id` nem `recurrences.end_date`.

**3.2 Testar integridade e atomicidade**
- Cobrir constraints, referências, pertença à família, idempotência e
  transações de planos/parcelas.
- Aceite: operação inválida falha; erro durante geração do parcelamento
  não deixa dados parciais; duplicata recurrence/date é tratada como
  ocorrência já existente.

**3.3 Implementar repositories e mappers locais**
- Implementar contratos do domínio e conversões de/para o banco.
- Aceite: round-trip testado; detalhes de persistência não vazam para
  Domain ou Presentation.

### Fase 4: Configuração local e cadastros base

**4.1 Criar OWNER, família e vínculo inicial**
- Implementar caso de uso transacional para gravar usuário, família e
  vínculo `OWNER`.
- Aceite: configuração cria um conjunto válido; falha não deixa registros
  órfãos.

**4.2 Implementar configuração inicial na UI**
- Solicitar nome da pessoa e da família e apresentar loading, erro e
  sucesso.
- Aceite: UI usa ViewModel/UseCase e funciona no Android/iOS.
- Implementada em UI compartilhada; a tela coleta os nomes, grava o
  OWNER/família com transação e restaura a família salva na próxima
  abertura. Build e teste Android-host passaram no Windows; validação
  visual/compilação iOS ainda aguarda macOS/Xcode.

**4.3 Categorias e contas**
- Implementar criar, listar e desativar com validação da família.
- Aceite: categoria única por família; testes de domínio/repository e
  estados de UI cobertos.
- Implementados os fluxos compartilhados de criação, listagem e
  desativação, com validação da família e preservação de registros
  inativos. Testes de domínio, integração SQLite e ViewModel passaram no
  Android-host; validação visual/compilação iOS ainda aguarda macOS/Xcode.
  Decisão para a primeira versão: o gerenciamento e a seleção de contas
  ficam ocultos na UI; a entidade, persistência e referências opcionais
  permanecem para preservar compatibilidade e dados existentes.

### Fase 5: Despesas avulsas

**5.1 Casos de uso e repository**
- Implementar criar, listar/observar, editar e cancelar despesa avulsa.
- Aceite: valor positivo, UUID, autoria, data civil e referências da
  família validados; histórico preservado.
- Implementados os casos de uso e o repository SQLDelight, com observação
  reativa, validação de família/referências e bloqueio de edição ou
  cancelamento após pagamentos. Testes de domínio e integração SQLite
  passaram no Android-host.

**5.2 Fluxo de apresentação**
- Implementar ViewModel, UiState e telas de cadastro/lista.
- Aceite: estados loading/empty/error/success; regras financeiras fora
  dos Composables.
- Implementados cadastro, listagem, edição e cancelamento no fluxo
  compartilhado, com validação de entrada no ViewModel e atualização
  reativa da lista. Testes do ViewModel/parser e build Android passaram;
  validação de iOS continua pendente de macOS/Xcode.

### Fase 6: Pagamentos

**6.1 Registrar pagamento parcial ou integral**
- Persistir pagamento e atualizar o status de forma atômica.
- Aceite: valor original da obrigação preservado; excedente e pagamento
  em despesa cancelada rejeitados; cancelamento após pagamento bloqueado.
- Implementados entidade/contratos/caso de uso e repository SQLDelight.
  Registro, validação de saldo, pertença à família e atualização do status
  ocorrem na mesma transação; valores excedentes e despesas canceladas são
  rejeitados. Testes de domínio e integração SQLite passaram.

**6.2 Atualizar status por vencimento**
- Recalcular obrigações não terminais ao abrir/retomar e antes das
  consultas relevantes.
- Aceite: testes com relógio controlado para antes, no dia e após o
  vencimento, incluindo estados pago/cancelado.
- Status recalculado no início/retorno ao app, ao consultar despesas/saldo
  e antes/depois da gravação. Testes com datas fixas cobrem antes, no dia
  e após o vencimento, além dos estados pago e cancelado.

**6.3 Interface de pagamento**
- Exibir saldo e permitir registrar pagamento pelo fluxo de apresentação.
- Aceite: feedback loading/erro/sucesso; formatação monetária somente na
  Presentation.
- Implementado diálogo compartilhado para valor, data, forma de pagamento,
  conta e observações, com saldo, histórico e feedback de carregamento,
  erro e sucesso. Teste de fluxo ViewModel cobriu pagamentos parcial e
  integral; build Android passou. Validação de iOS segue pendente de
  macOS/Xcode.

### Fase 7: Parcelamentos

**7.1 Criar plano, despesas e posições**
- Criar plano, Expenses e Installments em uma transação.
- Aceite: uma Expense por posição; Installment referencia Expense; soma
  das obrigações igual ao total do plano.
- Implementados caso de uso, repository SQLDelight e gravação transacional
  de plano, despesas do tipo `INSTALLMENT` e posições. O total é dividido
  em centavos, distribuindo o resto nas primeiras parcelas; datas mensais
  são calculadas a partir do dia original e ajustadas ao último dia do mês
  quando necessário. Testes de domínio e rollback SQLite passaram.

**7.2 Consultar plano e parcelas**
- Exibir posição e dados financeiros derivados das Expenses associadas.
- Aceite: valor, vencimento e status vêm exclusivamente de Expense.
- Implementada consulta reativa por família e tela compartilhada que exibe
  descrição/total do plano e número, valor, vencimento e status de cada
  despesa associada. Valores, vencimentos e status das parcelas são lidos
  da `Expense`; teste ViewModel e build Android passaram. Validação iOS
  permanece pendente de macOS/Xcode.

### Fase 8: Recorrências

**8.1 Criar, editar e cancelar regra**
- Implementar regra ativa sem data final; editar não altera despesas
  existentes.
- Aceite: cancelamento impede novas ocorrências e preserva o histórico.
- Concluído: cadastro, edição, cancelamento, validação de família e
  preservação das ocorrências materializadas; fluxo integrado verificado
  por teste Android-host.

**8.2 Gerar janela futura**
- Gerar Expenses por janela e chave recurrence/date única.
- Aceite: idempotência no repository e regras de calendário cobertas por
  testes.
- Concluído: geração idempotente por índice único `(recurrence_id,
  due_date)`, com janela automática dos próximos 12 meses. Testes cobrem
  mensal/trimestral/anual, fim do mês, ano bissexto, limites da janela,
  data inicial e repetição; `:shared:testAndroidHostTest` e
  `:androidApp:assembleDebug` passaram. A validação iOS permanece pendente
  de macOS/Xcode.

### Fase 9: Integração e validação do MVP

**9.1 Integrar navegação e estados**
- Adicionar apenas rotas e componentes necessários aos fluxos incluídos.
- Aceite: rotas centralizadas; estados vazios, erro e loading nas telas
  relevantes.
- Concluído: seções são centralizadas em `ManagementSection`, com títulos
  associados à rota; os painéis apresentam estados de loading, vazio, erro
  e salvamento nos fluxos aplicáveis.

**9.2 Revisar arquitetura e preservação de dados**
- Revisar dependências, integridade de família, soft delete, logs e erros.
- Aceite: nenhuma regra financeira na UI; nenhum histórico removido
  silenciosamente; documentação consistente.
- Concluído: repositórios SQLDelight são conectados em
  `ManagementViewModelFactory`; o ViewModel depende de contratos do
  domínio. Erros técnicos inesperados são convertidos em mensagens
  contextuais, e validações mantêm mensagens de domínio. Os testes de
  repositório cobrem integridade de família, transações e preservação de
  histórico. Não há logger no MVP; dados financeiros não são registrados
  em logs.

**9.3 Validar Android e iOS**
- Executar testes shared/Android; compilar/testar iOS em macOS; percorrer
  configuração, cadastros, despesas, pagamentos, planos e recorrências.
- Aceite: gates passam e limitações conhecidas ficam registradas.
- Android validado: `:shared:testAndroidHostTest`,
  `:androidApp:lintDebug` e `:androidApp:assembleDebug` passaram. O lint
  reportou 0 erros e 16 avisos (atualizações de dependências sugeridas,
  qualificadores de recursos redundantes e ícone monocromático ausente).
  Os testes Android-host executaram 53 testes, sem falhas. Emulador API 35:
  instalação e inicialização verificadas; percurso manual cobriu
  configuração, categorias, despesa, pagamento integral, parcelamento,
  recorrência e persistência após reiniciar o app. A navegação de quatro
  abas sem gerenciamento/seleção de contas foi validada após o ajuste da
  primeira versão. Não foram encontrados crashes do aplicativo. iOS
  permanece pendente de macOS/Xcode; a fase 9 como um todo aguarda esse
  gate.

## 6. Definition of Done por tarefa

- Mudança dentro do escopo e sem refatoração não relacionada.
- Arquitetura e regras em PROJECT_RULES, ARCHITECTURE e DATABASE
  obedecidas.
- Toda regra financeira alterada tem testes de domínio.
- Testes e comandos definidos na tarefa foram executados, com resultado
  relatado.
- Compatibilidade Android/iOS considerada; APIs específicas isoladas.
- Documentação atualizada quando contrato, schema ou comportamento
  mudar.

## 7. Dependências e riscos

- Driver SQLite e migrations precisam ser escolhidos antes da Fase 3.
- Persistência depende do schema alinhado e das regras de domínio
  testadas.
- Parcelamento e recorrência dependem das funções de datas e distribuição
  de valores.
- Build iOS requer macOS/Xcode; no Windows, validar shared/Android e usar
  CI macOS para o gate iOS.
- Sincronização futura exige decisões próprias sobre identidade,
  concorrência, tombstones e conflitos; não improvisar dentro do MVP.