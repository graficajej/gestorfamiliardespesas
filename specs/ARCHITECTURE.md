# ARCHITECTURE.md

## 1. Objetivo

Definir a arquitetura técnica do aplicativo de gestão financeira
familiar.

Este documento complementa PROJECT_RULES.md e deve ser consultado antes
de criar módulos, camadas, dependências ou estruturas de dados.

## 2. Arquitetura geral

O projeto utiliza Kotlin Multiplatform com compartilhamento prioritário
de:

-   domínio;
-   casos de uso;
-   modelos;
-   repositories;
-   fontes de dados;
-   persistência;
-   regras financeiras;
-   validações;
-   estado de apresentação quando viável.

Compose Multiplatform será utilizado para compartilhar a UI sempre que
isso não prejudicar requisitos específicos da plataforma.

## 3. Camadas

### Presentation

Responsável por:

-   telas;
-   componentes;
-   navegação;
-   UiState;
-   interação do usuário.

Não pode conter regra financeira.

### Domain

Responsável por:

-   entidades;
-   value objects;
-   regras de negócio;
-   casos de uso;
-   contratos de repositories;
-   validações de domínio.

Não deve depender de Android UI.

### Data

Responsável por:

-   implementação de repositories;
-   banco local;
-   API;
-   mapeamento DTO \<-\> domínio;
-   sincronização.

### Core

Responsável por recursos compartilhados:

-   utilitários;
-   resultados;
-   erros;
-   logging;
-   design system;
-   navegação;
-   recursos comuns.

## 4. Fluxo de dependência

Presentation -\> Domain -\> Data

Presentation NÃO deve depender diretamente de detalhes de Data.

Domain NÃO deve depender de frameworks de UI.

Data implementa contratos definidos no Domain.

O composition root liga implementações concretas aos contratos. No MVP,
`composition.ManagementViewModelFactory` cria os repositories SQLDelight;
as entradas de plataforma criam o driver e fornecem o ViewModel à UI.
ViewModels dependem somente dos contratos de repository e casos de uso do
Domain, não de `AppDatabase` nem de implementações SQLDelight.

## 5. Estrutura sugerida

``` text
project-root/
├── androidApp/
├── iosApp/
├── composeApp/
├── shared/
│   ├── core/
│   │   ├── common/
│   │   ├── domain/
│   │   ├── database/
│   │   ├── network/
│   │   ├── navigation/
│   │   └── design-system/
│   │
│   ├── feature/
│   │   ├── dashboard/
│   │   ├── expenses/
│   │   ├── payments/
│   │   ├── installments/
│   │   ├── recurring/
│   │   ├── categories/
│   │   ├── accounts/
│   │   └── settings/
│   │
│   └── testing/
│
├── docs/
│   ├── PROJECT_RULES.md
│   ├── ARCHITECTURE.md
│   └── DATABASE.md
│
└── README.md
```

A estrutura física poderá ser ajustada conforme a versão das ferramentas
KMP, mas a separação de responsabilidades deverá permanecer.

## 6. Organização de uma feature

Exemplo:

``` text
feature/expenses/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
│
├── data/
│   ├── local/
│   ├── remote/
│   ├── mapper/
│   └── repository/
│
└── presentation/
    ├── list/
    ├── create/
    ├── edit/
    └── details/
```

Nem toda feature precisa possuir todas essas pastas desde o primeiro
dia.

Criar apenas o que for necessário.

## 7. Presentation

Cada tela deverá possuir:

-   Screen;
-   ViewModel;
-   UiState;
-   UiEvent, quando necessário;
-   UiEffect, quando necessário.

Exemplo:

``` text
ExpenseListScreen
ExpenseListViewModel
ExpenseListUiState
ExpenseListEvent
ExpenseListEffect
```

A Screen renderiza o estado.

O ViewModel coordena ações de apresentação.

UseCases executam regras.

## 8. MVI/MVVM

Adotar um modelo unidirecional:

User Action -\> Event -\> ViewModel -\> UseCase -\> State update -\> UI

Evitar mutação direta da UI.

## 9. Domain

O domínio deverá ser independente de detalhes de armazenamento.

Exemplo:

``` kotlin
interface ExpenseRepository {
    suspend fun getExpense(id: ExpenseId): Result<Expense>
    fun observeExpenses(filter: ExpenseFilter): Flow<List<Expense>>
    suspend fun save(expense: Expense): Result<Expense>
    suspend fun delete(id: ExpenseId): Result<Unit>
}
```

Os tipos reais deverão seguir a convenção final do projeto.

## 10. Use Cases

Um UseCase representa uma ação de negócio.

Exemplos:

-   CreateExpenseUseCase;
-   UpdateExpenseUseCase;
-   MarkExpenseAsPaidUseCase;
-   RegisterPaymentUseCase;
-   GenerateInstallmentsUseCase;
-   CreateRecurrenceUseCase;
-   CancelRecurrenceUseCase;
-   GetMonthlySummaryUseCase;
-   GetFutureExpensesUseCase.

Evitar UseCases genéricos como:

``` text
SaveEverythingUseCase
ManageFinanceUseCase
```

Preferir ações específicas.

## 11. Data

Repositories fazem a ponte entre domínio e fontes de dados.

Exemplo:

``` text
ExpenseRepositoryImpl
├── LocalDataSource
└── RemoteDataSource
```

A implementação deverá decidir quando consultar:

-   local;
-   remoto;
-   ambos.

A UI não deve saber.

## 12. Offline-first

Prioridade:

1.  ler/escrever localmente;
2.  atualizar UI;
3.  sincronizar remotamente;
4.  tratar falhas de sincronização.

A camada de sincronização não deverá bloquear operações locais básicas.

No primeiro MVP, a fonte de dados é somente local: um perfil OWNER e
uma família configurados no dispositivo. Backend, autenticação remota e
sincronização entre dispositivos ficam adiados. Não criar módulos ou
dependências de rede até essa decisão ser retomada.

O estado `VENCIDA` deve ser atualizado ao abrir/retomar o aplicativo e
antes das consultas que dependam dele; não exigir processamento em
segundo plano no MVP.

## 13. Banco local

O banco local deverá representar o estado necessário para operação
offline.

Não criar tabelas somente para refletir telas.

Modelar entidades de domínio e relações financeiras.

Detalhes ficam em DATABASE.md.

SQLite é a referência aprovada. Driver KMP e estratégia de migrations
devem ser escolhidos após comparar compatibilidade Android/iOS e
manutenção. Gravações compostas de plano, despesas e parcelas devem ser
atômicas.

## 14. Network

A camada de rede deverá conter:

-   client;
-   DTOs;
-   serializers;
-   interceptors;
-   tratamento técnico de erros;
-   mappers.

DTO não deve vazar para Domain ou Presentation.

## 15. Erros

Criar tipos de erro de domínio/técnicos separados.

Exemplo conceitual:

``` text
AppError
├── Validation
├── NotFound
├── Unauthorized
├── Network
├── Database
├── Sync
└── Unknown
```

A camada de apresentação converte erros em mensagens amigáveis.

Falhas de validação e ausência de dados podem apresentar suas mensagens
de domínio. Falhas inesperadas usam uma mensagem contextual genérica; a
mensagem técnica não deve ser exposta na UI. O MVP não mantém logging de
dados financeiros nem introduz um framework de logging sem requisito
operacional.

## 16. Design System

Componentes compartilhados devem ficar em:

``` text
core/design-system
```

Exemplos:

-   AppButton;
-   AppTextField;
-   MoneyField;
-   DateField;
-   AppCard;
-   StatusBadge;
-   ExpenseItem;
-   EmptyState;
-   ErrorState;
-   LoadingState.

Features não devem duplicar componentes básicos sem necessidade.

## 17. Navegação

Rotas devem ser centralizadas.

Evitar strings de rota espalhadas.

Rotas devem possuir parâmetros tipados ou uma abstração equivalente.

## 18. Datas

No domínio financeiro, distinguir:

-   data de calendário;
-   timestamp de criação/alteração.

Vencimento de conta é data de calendário.

createdAt/updatedAt são timestamps.

Nunca converter automaticamente uma data financeira para UTC de forma
que altere o dia exibido.

## 19. Dinheiro

Não utilizar:

``` text
Double
Float
```

para valores financeiros.

Preferir:

``` text
Long em menor unidade monetária
```

ou uma classe Money.

Exemplo:

R\$ 125,90

armazenamento:

12590 centavos

A formatação é responsabilidade da camada de apresentação.

## 20. IDs

Entidades sincronizáveis devem utilizar UUID ou identificador global
equivalente.

Não utilizar auto-increment local como ID de negócio.

## 21. Auditoria

Entidades relevantes deverão possuir:

-   id;
-   createdAt;
-   updatedAt;
-   createdBy;
-   updatedBy.

Quando sincronização estiver implementada:

-   deletedAt/isDeleted;
-   sync metadata.

## 22. Concorrência

Nenhuma operação de negócio deverá depender de estado mutável global.

Coroutines devem ser utilizadas para operações assíncronas.

Streams devem utilizar Flow quando houver observação contínua.

## 23. Testabilidade

UseCases devem ser testáveis sem:

-   UI;
-   banco real;
-   rede;
-   Android framework;
-   iOS framework.

Repositories devem ser mockáveis/fakeáveis.

## 24. Código específico de plataforma

Somente criar código Android/iOS específico quando:

-   API não existe em KMP;
-   comportamento realmente difere;
-   integração com sistema operacional exige.

Isolar esse código atrás de interfaces.

## 25. Estratégia de evolução

Não antecipar:

-   cartão completo;
-   Open Finance;
-   investimentos;
-   contas a receber;
-   SaaS;
-   multi-family.

Entretanto, não criar decisões que impeçam essas funcionalidades.

## 26. Regra de arquitetura

Dependências apontam para dentro.

UI conhece Domain.

Data implementa Domain.

Domain não conhece UI.

Detalhes tecnológicos não definem regras financeiras.
