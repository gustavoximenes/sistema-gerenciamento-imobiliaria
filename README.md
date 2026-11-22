# Sistema de Gerenciamento de Imobiliária

**Aluno: Gustavo de Matos Ximenes**

Aplicação acadêmica em Java 17, HTML e CSS para administrar uma imobiliária. O sistema usa o **Supabase Auth** para registro e login e o **PostgreSQL do Supabase** para todos os dados da aplicação.

## Versão web sem localhost

A pasta [`web-publico`](web-publico) contém uma SPA em HTML, CSS e JavaScript que conversa diretamente com o mesmo Supabase. Ela não precisa do servidor Java nem de nenhum programa aberto no computador.

Link público pelo GitHub Pages:

**https://gustavoximenes.github.io/sistema-gerenciamento-imobiliaria/**

Essa versão oferece catálogo com filtros, detalhes dos imóveis, favoritos, simulador educacional, formulário de contato, autenticação, perfil, histórico, visitas e painel administrativo. O workflow [`.github/workflows/pages.yml`](.github/workflows/pages.yml) publica automaticamente alterações da pasta `web-publico/dist`.

A aplicação Java continua no repositório para atender aos requisitos de Java, BO, DAO e DTO da disciplina.

## Funcionalidades

- Homepage institucional, catálogo, detalhes do imóvel, sobre e contato.
- Registro e login com e-mail e senha pelo Supabase Auth.
- Perfil com edição de nome e telefone.
- Histórico privado dos últimos imóveis consultados, salvo no Supabase.
- Cadastro de clientes, corretores, proprietários, imóveis e horários.
- Informações completas do imóvel, incluindo tipo, descrição, cidade, quartos, banheiros, vagas, área, preço, destaque e status.
- Solicitação simbólica de visita, registrada como pendente com a data desejada.
- Administração com edição de imóveis, clientes, corretores e proprietários.
- Administração de contas com redefinição de senha e remoção das sessões persistidas.
- Agenda, histórico por cliente e produtividade por corretor.
- Ranking, baixa procura, ticket médio e gráficos por bairro, dia, horário e status.
- Portal público responsivo que funciona diretamente pelo navegador.
- Busca por nome, bairro, tipo, quartos, preço e status.
- Favoritos sincronizados por usuário e formulário de contato para a equipe.
- Simulador educacional de parcelas e painel administrativo web.

## Executar em localhost

Instale um **JDK 17 ou superior**. O script baixa somente o Gson 2.13.2 do Maven Central na primeira execução.

No PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1
```

No Linux ou macOS:

```sh
sh scripts/iniciar.sh
```

Abra [http://localhost:8080](http://localhost:8080).

### Passo a passo no Windows

1. Instale o JDK 17 ou uma versão mais recente.
2. Abra a pasta do projeto no Explorador de Arquivos.
3. Clique na barra de endereço da pasta, digite `powershell` e pressione Enter.
4. Confira se o Java está instalado:

```powershell
java -version
javac -version
```

5. Inicie o sistema:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1
```

6. Aguarde a mensagem `Imobiliária disponível em http://localhost:8080`.
7. Abra o navegador e acesse [http://localhost:8080](http://localhost:8080).
8. Mantenha a janela do PowerShell aberta enquanto usar o sistema.
9. Para desligar o localhost, volte ao PowerShell e pressione `Ctrl+C`.

Se aparecer a mensagem de que `java` ou `javac` não foi reconhecido, feche e abra o PowerShell depois de instalar o JDK. Se a porta 8080 estiver ocupada, execute antes do passo 5:

```powershell
$env:IMOB_PORTA = '8081'
```

Nesse caso, abra `http://localhost:8081`.

A URL e a chave publicável do projeto acadêmico estão em `config/supabase.properties`. Para usar outro projeto, defina `SUPABASE_URL` e `SUPABASE_PUBLISHABLE_KEY` antes de iniciar. A chave administrativa `service_role` não é usada pela aplicação.

## Preparar o Supabase

O arquivo [`db/supabase.sql`](db/supabase.sql) cria as tabelas, relacionamentos, índices, views, carga inicial, trigger de perfil e políticas de segurança. Ele já foi aplicado ao projeto **Imobiliaria POO**.

Para preparar outro projeto, execute o arquivo no SQL Editor do Supabase. O banco será preenchido com proprietários, corretores, clientes fictícios, imóveis, horários e visitas históricas.

Todas as tabelas públicas têm Row Level Security. Um visitante acessa apenas o catálogo. Um cliente autenticado acessa o próprio perfil, histórico e visitas. A equipe acessa os cadastros e relatórios.

## Autorizar a primeira conta da equipe

1. Crie uma conta pelo site e confirme o e-mail.
2. No SQL Editor do Supabase, execute o comando abaixo com o e-mail confirmado:

```sql
insert into public.equipe(auth_id, nome)
select id, 'Gustavo de Matos Ximenes'
from auth.users
where email = 'SEU_EMAIL_CONFIRMADO'
on conflict (auth_id) do update set ativo = true;
```

Na próxima requisição, a conta verá **Gestão**, **Agenda** e **Relatórios**. A autorização é consultada no banco em cada acesso protegido.

## Arquitetura BO, DAO e DTO

| Camada | Responsabilidade |
| --- | --- |
| `App` e `View` | Rotas HTTP, cookies, autorização e HTML |
| `ImobiliariaBO` | Validações e regras de negócio |
| `ImobiliariaDAO` | API REST do Supabase, consultas, cadastros e relatórios |
| `DTO` | Records Java usados entre as camadas |
| `SupabaseAuth` | Cadastro, login, validação de token e logout |
| PostgreSQL | Persistência, relacionamentos, índices, views e RLS |

O DAO envia a chave publicável e o token do usuário autenticado. O PostgreSQL aplica as políticas RLS mesmo se alguém tentar chamar a API de dados fora do site.

## Páginas

| Rota | Conteúdo |
| --- | --- |
| `/` | Homepage e imóveis em destaque |
| `/imoveis` | Catálogo completo |
| `/imovel?id=1` | Informações detalhadas e acesso ao agendamento |
| `/sobre` | Apresentação do projeto |
| `/contato` | Canais de contato demonstrativos |
| `/perfil` | Dados pessoais e últimas consultas |
| `/visitas` | Histórico do cliente ou agenda da equipe |
| `/gestao/relatorios` | Indicadores e gráficos |
| `/gestao/imoveis` | Cadastro completo de imóveis no Supabase |
| `/gestao/usuarios` | Contas cadastradas e redefinição administrativa de senha |

## Diagramas

- UML de classes em PlantUML: [`docs/classes.puml`](docs/classes.puml)
- Diagrama Entidade-Relacionamento: [`docs/der.puml`](docs/der.puml)

## Roteiro de demonstração

1. Abra a homepage e o catálogo.
2. Entre em um imóvel e mostre suas informações completas.
3. Crie uma conta, entre e abra **Meu perfil**.
4. Volte ao perfil depois de consultar imóveis e mostre o histórico.
5. Edite nome e telefone.
6. Com uma conta da equipe, cadastre um imóvel em **Gestão → Imóveis**.
7. Agende uma visita e mostre a agenda.
8. Abra os relatórios e interprete ranking, baixa procura e ticket médio.
