# DATABASE.md --- Modelo de Dados Definitivo

**Versão:** 1.0\
**Status:** Definitivo para implementação do MVP\
**Banco local de referência:** SQLite\
**Moeda padrão:** BRL\
**IDs:** UUID em `TEXT`

**Escopo MVP:** operação local/offline com um usuário `OWNER` e uma
família inicial no dispositivo. Autenticação remota e sincronização
multi-dispositivo estão fora deste MVP.

## 1. Objetivo

Este documento é o contrato oficial de persistência do aplicativo
financeiro familiar. Define tabelas, campos, tipos, PKs, FKs,
constraints, índices e relacionamentos para o MVP, preservando
compatibilidade com sincronização futura.

## 2. Decisão fundamental: obrigação x pagamento

`expenses` representa a **obrigação financeira**. `payments` representa
sua **liquidação**. Uma obrigação pode existir sem pagamento, pode
receber pagamentos parciais e continua existindo depois de paga para
preservar histórico.

## 3. Decisão definitiva sobre parcelamento

O modelo é:

``` text
InstallmentPlan
    └── Installment 1 ── Expense ── Payment(s)
    └── Installment 2 ── Expense ── Payment(s)
    └── Installment N ── Expense ── Payment(s)
```

`installment_plans` representa a compra/contrato; `installments`
representa apenas a posição da parcela; `expenses` é a fonte única de
verdade para valor, vencimento e status; `payments` registra a
liquidação. `installments` **não** duplica valor, vencimento ou status.

## 4. Tabelas oficiais do MVP

1.  `users`
2.  `families`
3.  `family_members`
4.  `categories`
5.  `accounts`
6.  `expenses`
7.  `recurrences`
8.  `installment_plans`
9.  `installments`
10. `payments`

Não criar tabelas adicionais no MVP sem justificar a mudança no domínio
e atualizar este documento.

## 5. Convenções de tipos

  Conceito     SQLite      Regra
  ------------ ----------- ------------------------------
  UUID         `TEXT`      identificador global estável
  Texto        `TEXT`      UTF-8
  Dinheiro     `INTEGER`   centavos
  Data civil   `TEXT`      `YYYY-MM-DD`
  Timestamp    `TEXT`      ISO-8601 UTC
  Boolean      `INTEGER`   `0/1`
  Enum         `TEXT`      valores fechados
  Moeda        `TEXT`      `BRL` no MVP

### Dinheiro

Nunca usar `FLOAT`, `DOUBLE` ou `REAL` para valores financeiros.
Exemplo: R\$ 10,50 = `1050`. Valores de despesas, recorrências e planos
parcelados devem ser `INTEGER > 0`; contagens e ajustes monetários seguem
as constraints específicas de cada tabela.

## 6. `users`

  Campo          Tipo     Null Regra
  -------------- ------ ------ ------------------------
  `id`           TEXT      NÃO PK, UUID
  `name`         TEXT      NÃO nome
  `email`        TEXT      SIM único quando informado
  `created_at`   TEXT      NÃO UTC
  `updated_at`   TEXT      NÃO UTC

**PK:** `users.id`\
**Índice:** `UNIQUE users_email_unique(email) WHERE email IS NOT NULL`

## 7. `families`

  Campo          Tipo     Null Regra
  -------------- ------ ------ ----------
  `id`           TEXT      NÃO PK, UUID
  `name`         TEXT      NÃO nome
  `created_at`   TEXT      NÃO UTC
  `updated_at`   TEXT      NÃO UTC

**PK:** `families.id`

## 8. `family_members`

  Campo          Tipo     Null Regra
  -------------- ------ ------ --------------------
  `id`           TEXT      NÃO PK, UUID
  `family_id`    TEXT      NÃO FK → `families.id`
  `user_id`      TEXT      NÃO FK → `users.id`
  `role`         TEXT      NÃO `OWNER` / `MEMBER`
  `created_at`   TEXT      NÃO UTC
  `updated_at`   TEXT      NÃO UTC

**Constraints:** `UNIQUE(family_id,user_id)`,
`role IN ('OWNER','MEMBER')`.\
**Índices:** `(family_id)`, `(user_id)`, unique `(family_id,user_id)`.

## 9. `categories`

  Campo          Tipo        Null Regra
  -------------- --------- ------ ------------------------
  `id`           TEXT         NÃO PK, UUID
  `family_id`    TEXT         NÃO FK → `families.id`
  `name`         TEXT         NÃO nome
  `icon`         TEXT         SIM identificador do ícone
  `is_active`    INTEGER      NÃO 0/1
  `created_at`   TEXT         NÃO UTC
  `updated_at`   TEXT         NÃO UTC

**Constraints:** `UNIQUE(family_id,name)`, `is_active IN (0,1)`.\
**Índices:** `(family_id)`, `(family_id,is_active)`.

## 10. `accounts`

  Campo                Tipo        Null Regra
  -------------------- --------- ------ ----------------------------------------------
  `id`                 TEXT         NÃO PK, UUID
  `family_id`          TEXT         NÃO FK → `families.id`
  `name`               TEXT         NÃO nome
  `type`               TEXT         NÃO `BANK/CASH/DIGITAL_WALLET/CREDIT_CARD/OTHER`
  `institution_name`   TEXT         SIM instituição
  `is_active`          INTEGER      NÃO 0/1
  `created_at`         TEXT         NÃO UTC
  `updated_at`         TEXT         NÃO UTC

**Constraints:** `type` dentro do enum; `is_active IN (0,1)`.\
**Índices:** `(family_id)`, `(family_id,is_active)`.

## 11. `expenses`

É a tabela financeira central. Cada registro é uma obrigação individual.

  Campo               Tipo        Null Regra
  ------------------- --------- ------ -----------------------------------
  `id`                TEXT         NÃO PK, UUID
  `family_id`         TEXT         NÃO FK → `families.id`
  `category_id`       TEXT         SIM FK → `categories.id`
  `account_id`        TEXT         SIM FK → `accounts.id`
  `description`       TEXT         NÃO descrição
  `amount_in_cents`   INTEGER      NÃO valor da obrigação
  `currency`          TEXT         NÃO `BRL`
  `due_date`          TEXT         NÃO `YYYY-MM-DD`
  `type`              TEXT         NÃO `ONE_TIME/RECURRING/INSTALLMENT`
  `status`            TEXT         NÃO `A_VENCER/VENCIDA/PAGA/CANCELADA`
  `notes`             TEXT         SIM observações
  `recurrence_id`     TEXT         SIM FK → `recurrences.id`
  `created_by`        TEXT         NÃO FK → `users.id`
  `updated_by`        TEXT         NÃO FK → `users.id`
  `created_at`        TEXT         NÃO UTC
  `updated_at`        TEXT         NÃO UTC
  `deleted_at`        TEXT         SIM tombstone

**PK:** `expenses.id`.\
**FKs:** `family_id → families.id`; `category_id → categories.id`;
`account_id → accounts.id`; `recurrence_id → recurrences.id`;
`created_by/updated_by → users.id`.

**Constraints:**

``` text
amount_in_cents > 0
currency = 'BRL'
type IN ('ONE_TIME','RECURRING','INSTALLMENT')
status IN ('A_VENCER','VENCIDA','PAGA','CANCELADA')
```

Regras de consistência do domínio:

``` text
ONE_TIME    → recurrence_id NULL
RECURRING   → recurrence_id NOT NULL
INSTALLMENT → vinculada a exatamente uma linha de installments por expense_id
```

**Índices obrigatórios:**

``` text
(family_id)
(family_id,due_date)
(family_id,status)
(family_id,status,due_date)
(family_id,category_id)
(family_id,account_id)
(recurrence_id)
(family_id,created_at)
(family_id,status,due_date DESC)
```

## 12. `recurrences`

Define a regra que gera despesas recorrentes. A regra não substitui as
despesas históricas geradas.

  Campo               Tipo        Null Regra
  ------------------- --------- ------ ----------------------------
  `id`                TEXT         NÃO PK, UUID
  `family_id`         TEXT         NÃO FK → `families.id`
  `description`       TEXT         NÃO descrição
  `amount_in_cents`   INTEGER      NÃO valor padrão
  `currency`          TEXT         NÃO BRL
  `category_id`       TEXT         SIM FK → `categories.id`
  `account_id`        TEXT         SIM FK → `accounts.id`
  `frequency`         TEXT         NÃO `MONTHLY/QUARTERLY/YEARLY`
  `start_date`        TEXT         NÃO data inicial
  `due_day`           INTEGER      SIM 1..31
  `is_active`         INTEGER      NÃO 0/1
  `created_by`        TEXT         NÃO FK → `users.id`
  `created_at`        TEXT         NÃO UTC
  `updated_at`        TEXT         NÃO UTC

**Constraints:** valor `> 0`; `currency='BRL'`; frequência válida;
`due_day` nulo ou 1..31; `is_active IN (0,1)`.\
**Índices:** `(family_id)`, `(family_id,is_active)`,
`(family_id,start_date)`.

Alterar uma recorrência afeta somente ocorrências futuras ainda não
geradas/definidas; nunca altera despesas históricas.

No MVP, recorrências não possuem data final e permanecem ativas até
cancelamento. A geração calcula cada data a partir da data/dia original;
quando o dia não existir no mês, usa o último dia daquele mês e retoma o
dia original no mês seguinte.

## 13. `installment_plans`

Representa a compra ou contrato parcelado como um todo.

  Campo                     Tipo        Null Regra
  ------------------------- --------- ------ ----------------------
  `id`                      TEXT         NÃO PK, UUID
  `family_id`               TEXT         NÃO FK → `families.id`
  `description`             TEXT         NÃO compra/contrato
  `total_amount_in_cents`   INTEGER      NÃO total
  `currency`                TEXT         NÃO BRL
  `installment_count`       INTEGER      NÃO \> 0
  `first_due_date`          TEXT         NÃO primeiro vencimento
  `frequency`               TEXT         NÃO `MONTHLY` no MVP
  `category_id`             TEXT         SIM FK → `categories.id`
  `account_id`              TEXT         SIM FK → `accounts.id`
  `created_by`              TEXT         NÃO FK → `users.id`
  `created_at`              TEXT         NÃO UTC
  `updated_at`              TEXT         NÃO UTC

**Constraints:** total `> 0`; `currency='BRL'`;
`0 < installment_count <= total_amount_in_cents`;
`frequency='MONTHLY'`. A relação garante ao menos um centavo por parcela.
**Índices:** `(family_id)`, `(family_id,first_due_date)`.

Criação de um plano, suas `expenses` e respectivas posições em
`installments` é atômica. O total em centavos é dividido igualmente e o
resto é distribuído, um centavo por vez, nas primeiras parcelas. Cada
vencimento mensal é calculado a partir do dia da primeira data; em meses
sem esse dia, usa-se o último dia do mês sem deslocar o dia-base das
parcelas seguintes.

## 14. `installments`

Representa apenas a posição da parcela dentro de um plano. Não armazena
valor, vencimento ou status financeiro.

  Campo                   Tipo        Null Regra
  ----------------------- --------- ------ -----------------------------
  `id`                    TEXT         NÃO PK, UUID
  `installment_plan_id`   TEXT         NÃO FK → `installment_plans.id`
  `expense_id`            TEXT         NÃO FK → `expenses.id`
  `number`                INTEGER      NÃO número da parcela
  `total_installments`    INTEGER      NÃO total de parcelas
  `created_at`            TEXT         NÃO UTC
  `updated_at`            TEXT         NÃO UTC

**Constraints:** `number > 0`; `total_installments > 0`;
`number <= total_installments`; `UNIQUE(installment_plan_id,number)`;
`UNIQUE(expense_id)`.\
**Índices:** `(installment_plan_id)`, unique
`(installment_plan_id,number)`, unique `(expense_id)`.

## 15. `payments`

Representa a liquidação de uma obrigação. O modelo permite pagamentos
parciais.

  Campo                        Tipo        Null Regra
  ---------------------------- --------- ------ --------------------
  `id`                         TEXT         NÃO PK, UUID
  `family_id`                  TEXT         NÃO FK → `families.id`
  `expense_id`                 TEXT         NÃO FK → `expenses.id`
  `paid_amount_in_cents`       INTEGER      NÃO \> 0
  `currency`                   TEXT         NÃO BRL
  `paid_date`                  TEXT         NÃO data civil
  `payment_method`             TEXT         NÃO enum
  `account_id`                 TEXT         SIM FK → `accounts.id`
  `interest_amount_in_cents`   INTEGER      NÃO \>= 0
  `fine_amount_in_cents`       INTEGER      NÃO \>= 0
  `discount_amount_in_cents`   INTEGER      NÃO \>= 0
  `notes`                      TEXT         SIM observações
  `created_by`                 TEXT         NÃO FK → `users.id`
  `created_at`                 TEXT         NÃO UTC
  `updated_at`                 TEXT         NÃO UTC

**FKs:** `family_id → families.id`; `expense_id → expenses.id`;
`account_id → accounts.id`; `created_by → users.id`.

**PaymentMethod:** `PIX`, `CASH`, `DEBIT_CARD`, `CREDIT_CARD`,
`BANK_TRANSFER`, `BOLETO`, `OTHER`.

**Índices:** `(family_id)`, `(expense_id)`, `(family_id,paid_date)`,
`(family_id,account_id)`.

O registro do pagamento, a verificação do saldo remanescente e a
atualização de `expenses.status` devem ocorrer na mesma transação. O valor
original de `expenses.amount_in_cents` não é alterado por pagamentos.
Obrigações não terminais são recalculadas na abertura/retomada e antes de
consultas relevantes: saldo zero resulta em `PAGA`; saldo positivo com
vencimento anterior à data atual resulta em `VENCIDA`; nos demais casos,
`A_VENCER`. `CANCELADA` é terminal e não pode receber pagamentos.

## 16. Relacionamentos

``` text
User 1 ── N FamilyMember
Family 1 ── N FamilyMember
Family 1 ── N Category
Family 1 ── N Account
Family 1 ── N Expense
Family 1 ── N Recurrence
Family 1 ── N InstallmentPlan
Family 1 ── N Payment
Category 1 ── N Expense
Account 1 ── N Expense
Recurrence 1 ── N Expense
InstallmentPlan 1 ── N Installment
Installment 1 ── 1 Expense
Expense 1 ── N Payment
```

## 17. Integridade entre famílias

Toda entidade financeira deve pertencer a uma família. O domínio deve
impedir, por exemplo, `Expense` da família A com `Category`, `Account`,
`Recurrence`, `InstallmentPlan` ou `Payment` da família B. Antes de
persistir, o UseCase deve verificar que o usuário atual é `FamilyMember`
da família e que todas as referências pertencem à mesma família.

## 18. Status e pagamentos parciais

`expenses.status` é o estado persistido da obrigação: `A_VENCER`,
`VENCIDA`, `PAGA`, `CANCELADA`. O domínio é a fonte oficial da regra de
transição. Exemplo: se o vencimento passou e a obrigação não está
totalmente liquidada, seu estado é `VENCIDA`. Pagamentos parciais são
permitidos:

``` text
Expense
 ├── Payment 1
 ├── Payment 2
 └── Payment 3
```

O total pago e os ajustes são agregados pelo domínio:

``` text
saldo = amount_in_cents
  + SUM(interest_amount_in_cents)
  + SUM(fine_amount_in_cents)
  - SUM(discount_amount_in_cents)
  - SUM(paid_amount_in_cents)
```

Rejeitar um pagamento que exceda o saldo imediatamente anterior à
operação. Uma obrigação com pagamentos não pode ser cancelada no MVP.
Uma obrigação cancelada não pode receber pagamentos. `PAGA` significa
saldo igual a zero; `VENCIDA` significa saldo positivo e vencimento
anterior à data civil atual; caso contrário, permanece `A_VENCER`. O
status é recalculado ao abrir/retomar o app e antes de consultas que
dependam dele.

## 19. Parcelamento e arredondamento

O total das parcelas deve ser exatamente igual ao total do plano.
Exemplo: R\$ 100,00 em 3 parcelas → R\$ 33,34 + R\$ 33,33 + R\$ 33,33. A
regra de arredondamento deve ser determinística e testada.

## 20. Recorrência

A geração de ocorrências deve ser por janela futura controlada e
idempotente. Executar a geração duas vezes não pode duplicar despesas. A
combinação `recurrence_id + due_date` identifica uma ocorrência e possui
índice único parcial para impedir duplicatas. O repositório deve tratar
conflito como ocorrência já existente.

`end_date` não faz parte do schema MVP; uma recorrência termina por
cancelamento (`is_active = 0`).

`start_date` é o limite inferior da regra. A geração começa no mês-base da
recorrência e só materializa vencimentos iguais ou posteriores a essa data.
`due_day` define o dia do mês; quando nulo, usa o dia de `start_date`.
Meses sem esse dia usam o último dia do mês, sem alterar o dia-base dos
meses seguintes. A interface materializa a janela dos próximos 12 meses;
gerar novamente a mesma janela é seguro e não duplica despesas. Editar a
regra não altera ocorrências já materializadas e cancelar impede novas
gerações, preservando as despesas existentes.

## 21. Datas e timestamps

Datas financeiras (`due_date`, `paid_date`, `start_date`,
`first_due_date`) são datas civis e devem ser modeladas no Kotlin como
`LocalDate`, sem conversão de timezone. Auditoria (`created_at`,
`updated_at`, `deleted_at`) usa `Instant`/UTC.

## 22. Soft delete e histórico

Não apagar fisicamente obrigações pagas ou dados necessários ao
histórico. `deleted_at` é o tombstone inicial de `expenses`. Com
sincronização, outras entidades poderão adotar tombstones. Consultas
normais devem filtrar registros removidos.

## 23. Foreign keys

SQLite deve executar:

``` sql
PRAGMA foreign_keys = ON;
```

A política padrão é preservar histórico e evitar cascatas destrutivas em
entidades financeiras. Desativação/soft delete deve ser preferida à
exclusão física quando houver histórico.

## 24. SQL estrutural de referência

``` sql
CREATE TABLE users (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    email TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE UNIQUE INDEX users_email_unique ON users(email) WHERE email IS NOT NULL;

CREATE TABLE families (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE family_members (
    id TEXT PRIMARY KEY NOT NULL,
    family_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    role TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (family_id) REFERENCES families(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    CHECK (role IN ('OWNER','MEMBER')),
    UNIQUE (family_id,user_id)
);

CREATE TABLE categories (
    id TEXT PRIMARY KEY NOT NULL,
    family_id TEXT NOT NULL,
    name TEXT NOT NULL,
    icon TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (family_id) REFERENCES families(id),
    CHECK (is_active IN (0,1)),
    UNIQUE (family_id,name)
);

CREATE TABLE accounts (
    id TEXT PRIMARY KEY NOT NULL,
    family_id TEXT NOT NULL,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    institution_name TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (family_id) REFERENCES families(id),
    CHECK (type IN ('BANK','CASH','DIGITAL_WALLET','CREDIT_CARD','OTHER')),
    CHECK (is_active IN (0,1))
);

CREATE TABLE recurrences (
    id TEXT PRIMARY KEY NOT NULL,
    family_id TEXT NOT NULL,
    description TEXT NOT NULL,
    amount_in_cents INTEGER NOT NULL,
    currency TEXT NOT NULL DEFAULT 'BRL',
    category_id TEXT,
    account_id TEXT,
    frequency TEXT NOT NULL,
    start_date TEXT NOT NULL,
    due_day INTEGER,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (family_id) REFERENCES families(id),
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    CHECK (amount_in_cents > 0),
    CHECK (currency = 'BRL'),
    CHECK (frequency IN ('MONTHLY','QUARTERLY','YEARLY')),
    CHECK (due_day IS NULL OR due_day BETWEEN 1 AND 31),
    CHECK (is_active IN (0,1))
);

CREATE TABLE installment_plans (
    id TEXT PRIMARY KEY NOT NULL,
    family_id TEXT NOT NULL,
    description TEXT NOT NULL,
    total_amount_in_cents INTEGER NOT NULL,
    currency TEXT NOT NULL DEFAULT 'BRL',
    installment_count INTEGER NOT NULL,
    first_due_date TEXT NOT NULL,
    frequency TEXT NOT NULL DEFAULT 'MONTHLY',
    category_id TEXT,
    account_id TEXT,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (family_id) REFERENCES families(id),
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    CHECK (total_amount_in_cents > 0),
    CHECK (currency = 'BRL'),
    CHECK (installment_count > 0 AND installment_count <= total_amount_in_cents),
    CHECK (frequency = 'MONTHLY')
);

CREATE TABLE expenses (
    id TEXT PRIMARY KEY NOT NULL,
    family_id TEXT NOT NULL,
    category_id TEXT,
    account_id TEXT,
    description TEXT NOT NULL,
    amount_in_cents INTEGER NOT NULL,
    currency TEXT NOT NULL DEFAULT 'BRL',
    due_date TEXT NOT NULL,
    type TEXT NOT NULL,
    status TEXT NOT NULL,
    notes TEXT,
    recurrence_id TEXT,
    created_by TEXT NOT NULL,
    updated_by TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT,
    FOREIGN KEY (family_id) REFERENCES families(id),
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (recurrence_id) REFERENCES recurrences(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (updated_by) REFERENCES users(id),
    CHECK (amount_in_cents > 0),
    CHECK (currency = 'BRL'),
    CHECK (type IN ('ONE_TIME','RECURRING','INSTALLMENT')),
    CHECK (status IN ('A_VENCER','VENCIDA','PAGA','CANCELADA')),
    CHECK (
      (type = 'ONE_TIME' AND recurrence_id IS NULL) OR
      (type = 'RECURRING' AND recurrence_id IS NOT NULL) OR
      (type = 'INSTALLMENT' AND recurrence_id IS NULL)
    )
);

CREATE TABLE installments (
    id TEXT PRIMARY KEY NOT NULL,
    installment_plan_id TEXT NOT NULL,
    expense_id TEXT NOT NULL,
    number INTEGER NOT NULL,
    total_installments INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (installment_plan_id) REFERENCES installment_plans(id),
    FOREIGN KEY (expense_id) REFERENCES expenses(id),
    CHECK (number > 0),
    CHECK (total_installments > 0),
    CHECK (number <= total_installments),
    UNIQUE (installment_plan_id,number),
    UNIQUE (expense_id)
);

CREATE TABLE payments (
    id TEXT PRIMARY KEY NOT NULL,
    family_id TEXT NOT NULL,
    expense_id TEXT NOT NULL,
    paid_amount_in_cents INTEGER NOT NULL,
    currency TEXT NOT NULL DEFAULT 'BRL',
    paid_date TEXT NOT NULL,
    payment_method TEXT NOT NULL,
    account_id TEXT,
    interest_amount_in_cents INTEGER NOT NULL DEFAULT 0,
    fine_amount_in_cents INTEGER NOT NULL DEFAULT 0,
    discount_amount_in_cents INTEGER NOT NULL DEFAULT 0,
    notes TEXT,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (family_id) REFERENCES families(id),
    FOREIGN KEY (expense_id) REFERENCES expenses(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    CHECK (paid_amount_in_cents > 0),
    CHECK (currency = 'BRL'),
    CHECK (interest_amount_in_cents >= 0),
    CHECK (fine_amount_in_cents >= 0),
    CHECK (discount_amount_in_cents >= 0),
    CHECK (payment_method IN ('PIX','CASH','DEBIT_CARD','CREDIT_CARD','BANK_TRANSFER','BOLETO','OTHER'))
);
```

## 25. Índices SQL de referência

``` sql
CREATE INDEX family_members_family_idx ON family_members(family_id);
CREATE INDEX family_members_user_idx ON family_members(user_id);
CREATE INDEX categories_family_idx ON categories(family_id);
CREATE INDEX categories_active_idx ON categories(family_id,is_active);
CREATE INDEX accounts_family_idx ON accounts(family_id);
CREATE INDEX accounts_active_idx ON accounts(family_id,is_active);
CREATE INDEX expenses_family_idx ON expenses(family_id);
CREATE INDEX expenses_due_date_idx ON expenses(family_id,due_date);
CREATE INDEX expenses_status_idx ON expenses(family_id,status);
CREATE INDEX expenses_category_idx ON expenses(family_id,category_id);
CREATE INDEX expenses_account_idx ON expenses(family_id,account_id);
CREATE INDEX expenses_recurrence_idx ON expenses(recurrence_id);
CREATE INDEX expenses_upcoming_idx ON expenses(family_id,status,due_date);
CREATE INDEX expenses_history_idx ON expenses(family_id,status,due_date DESC);
CREATE UNIQUE INDEX expenses_recurrence_due_date_unique
  ON expenses(recurrence_id,due_date)
  WHERE recurrence_id IS NOT NULL;
CREATE INDEX recurrences_family_idx ON recurrences(family_id);
CREATE INDEX recurrences_active_idx ON recurrences(family_id,is_active);
CREATE INDEX installment_plans_family_idx ON installment_plans(family_id);
CREATE INDEX installment_plans_first_due_idx ON installment_plans(family_id,first_due_date);
CREATE INDEX installments_plan_idx ON installments(installment_plan_id);
CREATE INDEX payments_family_idx ON payments(family_id);
CREATE INDEX payments_expense_idx ON payments(expense_id);
CREATE INDEX payments_date_idx ON payments(family_id,paid_date);
CREATE INDEX payments_account_idx ON payments(family_id,account_id);
```

## 26. Consultas principais suportadas

-   mês atual: `family_id + due_date`
-   próximas: `family_id + status + due_date`
-   vencidas: `family_id + status=VENCIDA`
-   histórico: `family_id + status=PAGA + due_date DESC`
-   parcelamento:
    `installment_plan → installments → expenses → payments`
-   projeção: `expenses` por janela futura, excluindo `CANCELADA`
-   totais: agregações de `expenses` e `payments` conforme as regras do
    domínio.

## 27. Auditoria e sincronização futura

`created_at`, `updated_at`, `created_by` e `updated_by` existem para
auditoria e futura sincronização. IDs são UUID para não depender de
auto-increment local. Campos como `sync_status`, `last_synced_at`,
`server_updated_at` e `version` **não fazem parte do MVP** e só devem
entrar quando a estratégia de sincronização for definida.

## 28. Entidades futuras

Reservadas para evolução: `CreditCard`, `CreditCardInvoice`,
`Financing`, `Income`, `Budget`, `Goal`, `Attachment`, `Notification`,
`AuditLog`. Não criar essas tabelas no MVP apenas para antecipar
funcionalidades.

## 29. Regras que o agente não pode quebrar

1.  Nunca usar `Float`/`Double`/`REAL` para dinheiro.
2.  Nunca usar auto-increment como ID de negócio.
3.  Nunca apagar fisicamente obrigações que precisam de histórico.
4.  Nunca representar parcelamento apenas como texto (`"6x"`).
5.  Nunca duplicar `amount`, `due_date` ou `status` em `installments`.
6.  Nunca permitir referências entre famílias diferentes.
7.  Nunca alterar despesas históricas ao editar recorrências.
8.  Nunca gerar recorrências duplicadas.
9.  Nunca colocar regra financeira na UI.
10. Toda alteração de schema exige migration.
11. Toda nova regra financeira exige testes.
12. Toda mudança estrutural exige atualização deste `DATABASE.md`.

## 30. Fonte única da verdade

  Informação             Fonte
  ---------------------- ---------------------------------
  obrigação financeira   `expenses`
  valor da obrigação     `expenses.amount_in_cents`
  vencimento             `expenses.due_date`
  status                 `expenses.status`
  pagamentos             `payments`
  valor pago             `payments.paid_amount_in_cents`
  compra parcelada       `installment_plans`
  número da parcela      `installments.number`
  regra recorrente       `recurrences`
  categoria              `categories`
  conta                  `accounts`
  família                `families`
  usuário                `users`
  vínculo familiar       `family_members`

## 31. Critério de aprovação

O modelo está pronto para implementação quando as 10 tabelas, PKs, FKs,
constraints e índices acima estiverem implementados por migration; o
banco operar com foreign keys habilitadas; valores usarem centavos;
datas financeiras usarem `LocalDate`; IDs forem UUID; parcelamento
seguir `Plan → Installment → Expense → Payment`; recorrência gerar
`Expense`; histórico for preservado; integridade familiar for validada;
e testes cobrirem persistência, parcelamento, recorrência, pagamentos
parciais e status.

**Este documento é a referência oficial do modelo de dados do
aplicativo.**
