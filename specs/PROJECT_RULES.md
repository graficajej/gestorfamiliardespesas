# PROJECT_RULES.md

## 1. Objetivo

Este arquivo é o contrato principal de desenvolvimento do aplicativo de
gestão financeira familiar.

O agente de IA DEVE ler este arquivo antes de criar, modificar, remover
ou refatorar qualquer código.

Quando houver conflito entre uma solicitação pontual e estas regras,
preservar a arquitetura, o domínio e as regras financeiras. Se a
solicitação exigir mudança arquitetural, explicar o impacto antes de
implementá-la.

## 2. Produto

Aplicativo mobile para Android e iOS. O primeiro MVP será local e usado
por um perfil OWNER em uma família configurada no próprio dispositivo.
O compartilhamento entre dois usuários e dispositivos fica para uma
etapa futura com autenticação e sincronização definidas.

Objetivo principal:

-   controlar despesas a vencer;
-   registrar pagamentos;
-   controlar despesas recorrentes;
-   controlar compras parceladas;
-   visualizar despesas futuras;
-   acompanhar histórico financeiro.

O produto deverá evoluir futuramente para um gestor financeiro familiar
completo.

O MVP inclui configuração local da família, despesas avulsas,
pagamentos, categorias, contas, parcelamentos e recorrências. Dashboard,
projeção, histórico dedicado, backend e sincronização ficam fora do MVP.

## 3. Stack-base

Stack inicial aprovada:

-   Kotlin;
-   Kotlin Multiplatform;
-   Compose Multiplatform;
-   Coroutines;
-   Flow;
-   arquitetura Clean Architecture;
-   MVVM/MVI conforme a necessidade da feature;
-   persistência local multiplataforma;
-   Git/GitHub;
-   Android Studio + Gemini.

SQLite é o banco local de referência definido em DATABASE.md. A escolha
do driver KMP e da estratégia de migrations deverá ser validada antes da
implementação da persistência. Backend, autenticação remota e
sincronização não fazem parte do MVP.

## 4. Princípios obrigatórios

1.  Domínio financeiro independente da UI.
2.  UI nunca acessa banco ou API diretamente.
3.  Regras de negócio ficam em Use Cases/domínio.
4.  Repository abstrai fontes de dados.
5.  Código compartilhado deve ser priorizado.
6.  Código específico de plataforma somente quando realmente necessário.
7.  Funcionalidades devem funcionar offline sempre que tecnicamente
    possível.
8.  Valores monetários nunca devem usar Float/Double para representar
    dinheiro.
9.  Datas financeiras devem ser tratadas como datas de calendário, sem
    deslocamentos acidentais de timezone.
10. Entidades sincronizáveis devem possuir IDs únicos estáveis,
    preferencialmente UUID.
11. Não apagar histórico financeiro silenciosamente.
12. Alterações de recorrências não devem alterar o histórico passado.
13. Não introduzir dependências sem justificar necessidade.
14. Não criar abstrações prematuras.
15. Toda feature relevante deve possuir testes de domínio.

## 5. Regras para o agente de IA

Antes de implementar:

1.  identificar a feature;
2.  identificar entidades afetadas;
3.  consultar ARCHITECTURE.md;
4.  consultar DATABASE.md quando houver persistência;
5.  verificar regras existentes;
6.  propor alterações necessárias;
7.  implementar somente o escopo solicitado;
8.  criar ou atualizar testes;
9.  revisar impactos em Android e iOS.

O agente NÃO deve:

-   reestruturar o projeto inteiro para resolver uma pequena tarefa;
-   mover arquivos sem necessidade;
-   criar novas camadas sem justificativa;
-   duplicar modelos de domínio desnecessariamente;
-   colocar regra de negócio dentro de Composable;
-   acessar banco diretamente a partir da UI;
-   criar chamadas HTTP dentro de ViewModel;
-   usar valores monetários como Double;
-   usar strings mágicas para estados;
-   apagar dados históricos sem uma regra explícita.

## 6. Arquitetura de feature

Cada feature deverá seguir, quando aplicável:

UI -\> ViewModel -\> UseCase -\> Repository -\> DataSource -\>
Local/Remote

Exemplo:

CreateExpenseScreen -\> CreateExpenseViewModel -\> CreateExpenseUseCase
-\> ExpenseRepository -\> ExpenseLocalDataSource

## 7. Estado da UI

A UI deverá representar estados explícitos:

-   Loading;
-   Success;
-   Empty;
-   Error.

Evitar múltiplos Booleanos independentes para representar estado
complexo.

Preferir um UiState imutável.

## 8. Tratamento de erros

Erros técnicos não devem aparecer diretamente para o usuário.

Criar uma camada de erro/domínio capaz de diferenciar:

-   erro de validação;
-   erro de persistência;
-   erro de rede;
-   erro de autenticação;
-   erro de sincronização;
-   erro inesperado.

Mensagens apresentadas ao usuário devem ser amigáveis e localizáveis.

## 9. Regras financeiras fundamentais

### Obrigação financeira != pagamento

`Expense` representa a obrigação financeira. `Installment` identifica a
posição de uma parcela no plano e referencia a `Expense`; não duplica
valor, vencimento ou status.

`Payment` representa a liquidação parcial ou total dessa obrigação.

Nunca substituir o valor original da obrigação apenas porque houve
pagamento.

### Valor previsto

Representa quanto deveria ser pago.

### Valor pago

Representa quanto efetivamente foi pago.

A diferença pode representar:

-   desconto;
-   juros;
-   multa;
-   ajuste.

O saldo é calculado no domínio por:

`valor da obrigação + juros + multa - descontos - pagamentos`.

Os ajustes e pagamentos são somados sobre todos os pagamentos da
obrigação. Rejeitar qualquer novo pagamento cujo valor informado exceda
o saldo atualizado. No MVP, não cancelar uma obrigação que já possua
pagamentos registrados.

## 10. Parcelamentos

Um parcelamento deve possuir um plano e suas posições de parcela. Cada
posição referencia uma `Expense`, que é a fonte única de valor,
vencimento e status.

Exemplo:

Compra total: R\$ 1.200 6 parcelas

Gerar:

1/6 2/6 3/6 4/6 5/6 6/6

Cada parcela possui uma obrigação (`Expense`) com:

-   valor;
-   vencimento;
-   status;
-   pagamento opcional.

Nunca armazenar apenas "6x" como texto e tentar reconstruir as parcelas
depois.

O total das obrigações geradas deve ser igual ao total do plano. A
distribuição de centavos é determinística; o restante é distribuído
começando pelas primeiras parcelas. Cada valor de parcela deve ser
maior que zero.

## 11. Recorrências

Recorrência representa uma regra para geração de ocorrências futuras.

Alterar uma recorrência não deve modificar automaticamente despesas já
geradas e históricas.

Cancelamento de recorrência impede novas ocorrências, mas preserva o
histórico.

No MVP, uma recorrência não possui data final; ela permanece ativa até
ser cancelada. A geração é idempotente por recorrência e data de
vencimento. Datas mensais são calculadas a partir do dia original: se o
mês não tiver esse dia, usar seu último dia e voltar ao dia original no
mês seguinte, sem deriva permanente.

## 12. Status

Status de obrigação:

-   A_VENCER;
-   VENCIDA;
-   PAGA;
-   CANCELADA.

Não usar strings livres para estados de domínio.

Uma obrigação com saldo maior que zero e vencimento anterior à data
civil atual é `VENCIDA`; com saldo maior que zero e vencimento hoje ou no
futuro é `A_VENCER`; com saldo igual a zero é `PAGA`. `CANCELADA` é um
estado terminal e não pode receber pagamentos. O status persistido deve
ser recalculado ao abrir/retomar o app e antes de consultas que dependam
dele. Regras de transição ficam no domínio.

## 13. Exclusão

Dados financeiros sincronizáveis não devem ser fisicamente apagados de
maneira que outro dispositivo não consiga receber a exclusão.

Preferir soft delete/tombstone quando a sincronização estiver
implementada.

Exclusões que impactem histórico devem exigir regras explícitas.

## 14. Sincronização futura

A sincronização é evolução futura, não requisito do MVP. Quando for
implementada, deverá ser preparada para:

-   dois dispositivos;
-   alterações concorrentes;
-   IDs estáveis;
-   createdAt;
-   updatedAt;
-   deletedAt/isDeleted;
-   identificação do usuário responsável pela alteração.

Não implementar sincronização improvisada diretamente dentro de telas.

## 15. UI/UX

Prioridades:

1.  clareza;
2.  rapidez;
3.  legibilidade;
4.  baixa carga cognitiva;
5.  consistência.

A ação principal de adicionar despesa deverá ser facilmente acessível.

Toda tela relevante deve possuir:

-   loading;
-   empty state;
-   error state;
-   feedback de sucesso quando necessário.

## 16. Navegação inicial

Áreas principais:

-   Dashboard;
-   Contas;
-   Parcelamentos;
-   Histórico;
-   Configurações.

Ação global:

-   Nova despesa.

## 17. Testes

Testar principalmente regras de domínio.

Obrigatório testar:

-   cálculo de parcelas;
-   geração de recorrências;
-   status de vencimento;
-   pagamento;
-   desconto;
-   juros;
-   multa;
-   cancelamento;
-   projeção futura.

Para o MVP, também cobrir saldo agregado, rejeição de pagamento acima do
saldo, bloqueio de cancelamento após pagamento, valores positivos,
ajuste de datas mensais sem deriva e idempotência de recorrências.
Projeção e dashboard serão testados quando entrarem no escopo.

Quando uma regra for alterada, atualizar testes correspondentes.

## 18. Compatibilidade

Toda funcionalidade compartilhada deve ser validada conceitualmente
para:

-   Android;
-   iOS.

Não assumir que uma API Android existe no iOS.

Código específico deve ficar isolado em expect/actual, interfaces ou
adaptadores apropriados.

## 19. Dependências

Antes de adicionar uma biblioteca:

-   verificar se ela é necessária;
-   verificar compatibilidade KMP;
-   verificar manutenção;
-   verificar impacto no Android;
-   verificar impacto no iOS;
-   preferir dependências maduras e pequenas.

## 20. Git

Commits devem ser pequenos e relacionados a uma única mudança.

Exemplos:

feat(expenses): add create expense use case feat(payments): add payment
registration test(installments): add installment calculation tests
fix(expenses): correct overdue status

Não misturar refatoração extensa com implementação de feature.

## 21. Definition of Done

Uma feature só está concluída quando:

-   código implementado;
-   arquitetura respeitada;
-   regras de negócio testadas;
-   estados de UI tratados;
-   Android considerado;
-   iOS considerado;
-   nenhuma regra financeira importante está na UI;
-   código compilável;
-   testes passando;
-   documentação atualizada quando houver mudança arquitetural.

## 22. Regra final

Quando houver dúvida entre uma solução rápida e uma solução que preserve
o domínio e a arquitetura, preservar o domínio e a arquitetura.

Não inventar regras de negócio. Se uma regra não estiver documentada,
sinalizar a dúvida antes de tomar uma decisão que altere dados
financeiros.
