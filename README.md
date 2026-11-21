# Sistema de Gerenciamento de Imobiliária

**Aluno: Gustavo de Matos Ximenes**

Projeto acadêmico de Programação Orientada a Objetos. A aplicação reúne o catálogo de casas, os cadastros da imobiliária, a agenda de visitas e os relatórios de procura.

## Executar em localhost

Instale um **JDK 17 ou superior**. Na primeira execução, o script baixa H2 e Gson do Maven Central. A conexão com a internet também é necessária para o Supabase Auth.

No PowerShell, dentro da pasta do projeto:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1
```

No Linux ou macOS, com `curl`:

```sh
sh scripts/iniciar.sh
```

Abra [http://localhost:8080](http://localhost:8080). O banco cria tabelas e carrega registros fictícios na primeira inicialização. As alterações persistem em `data/imobiliaria.mv.db`. Se a porta 8080 estiver ocupada, altere `IMOB_PORTA` e abra a porta indicada no terminal.

## Autenticação e perfis

- **Visitante** pode consultar o catálogo. O site exige login ao abrir a agenda de um imóvel.
- **Cliente** entra com uma conta Supabase confirmada, agenda visitas nos horários livres, consulta o próprio histórico e cancela as próprias visitas pendentes.
- **Equipe** gerencia cadastros, horários, status dos imóveis e das visitas, agenda de corretores e relatórios. O servidor usa a lista de e-mails confirmados definida em `IMOB_EQUIPE_EMAILS`. A lista vazia deixa o painel de gestão desabilitado.

Para a apresentação, crie e confirme a conta que será usada pela equipe **antes de incluí-la na lista de acesso**. Use **Criar conta** no site e confirme por e-mail. Se o serviço de e-mail do projeto restringir destinatários, crie o usuário em **Authentication → Users → Add user** no painel do Supabase, com confirmação administrativa para a conta que você controla.

No PowerShell, ajuste o e-mail abaixo e execute o script na mesma sessão:

```powershell
$env:IMOB_EQUIPE_EMAILS = 'seu-email-confirmado@exemplo.com'
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1
```

Para outro projeto Supabase, defina `SUPABASE_URL` e `SUPABASE_PUBLISHABLE_KEY`. O arquivo `config/supabase.properties` contém apenas a URL e a chave publicável do projeto da atividade. A senha e as chaves administrativas ficam fora do repositório. O Java valida cada token com o Supabase e controla o acesso no servidor. O cookie de sessão local expira em até uma hora, quando é necessário entrar novamente.

## Arquitetura e diagramas

| Elemento | Responsabilidade |
| --- | --- |
| `App` e `View` | Rotas HTTP, autorização e renderização de HTML |
| `ImobiliariaBO` | Validação de cadastros e regras de negócio |
| `ImobiliariaDAO` | Consultas, gravações, transações JDBC e agregações |
| `DTO` | Records com os dados trafegados entre camadas |
| `SupabaseAuth` | Login, cadastro, validação de sessão e logout |
| H2 | Persistência local e carga inicial |

- UML de classes em PlantUML: [fonte](docs/classes.puml) · [imagem](docs/classes.svg)
- Diagrama Entidade-Relacionamento: [fonte](docs/der.puml) · [imagem](docs/der.svg)
- Banco: [estrutura](db/schema.sql) · [carga inicial](db/seed.sql)

Uma visita começa como **pendente** e pode ser **realizada** após o início do horário ou **cancelada**. O cancelamento mantém o registro e devolve o horário à agenda. O DAO grava o preço do imóvel no agendamento e o banco bloqueia conflitos simultâneos de corretor, imóvel e cliente. A equipe precisa resolver visitas pendentes antes de tirar uma casa da disponibilidade.

## Indicadores

- **Ranking e volume por bairro** usam visitas realizadas.
- **Baixa procura** lista imóveis disponíveis cuja última visita realizada ocorreu há mais de 30 dias. Para imóveis sem visitas realizadas, o cálculo usa a data do cadastro.
- **Agenda e produtividade por corretor** mostram horários de visitas e totais por status.
- **Histórico por cliente** pode ser filtrado na agenda da equipe. O cliente vê o próprio histórico.
- **Ticket médio** considera o preço registrado em cada visita realizada.
- **Picos por dia e horário** agrupam a data e a hora da visita agendada e excluem cancelamentos.
- **Status das visitas** compara realizadas, pendentes e canceladas.

## Roteiro de demonstração (7 minutos)

1. **0:00–0:20 — Tema.** Apresente o objetivo da imobiliária.
2. **0:20–1:00 — Problema.** Explique o conflito de agendas e a necessidade de registrar a procura.
3. **1:00–2:35 — UML.** Mostre as classes, as multiplicidades e o histórico do horário.
4. **2:35–3:25 — DER.** Mostre as chaves e a restrição que evita duplicidade.
5. **3:25–4:15 — Persistência.** Acompanhe um agendamento de App a BO, DAO e H2.
6. **4:15–5:10 — Indicadores.** Abra os relatórios e interprete ranking, baixa procura e ticket médio.
7. **5:10–6:40 — Site.** Abra um imóvel, agende para um cliente fictício, confira a agenda do corretor e cancele para liberar a vaga. Mostre o filtro por cliente.
8. **6:40–7:00 — Considerações finais.** Retome o controle de agenda, o histórico e a análise de procura.

A carga inicial cria horários próximos à primeira execução. Para incluir horários novos depois disso, use **Cadastros → Horários**.
