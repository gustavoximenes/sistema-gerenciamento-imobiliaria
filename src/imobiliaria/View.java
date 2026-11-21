package imobiliaria;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

final class View {
    private static final Locale BR=Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA=DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private final String csrf;
    private final DTO.Identidade conta;
    private final boolean staff;

    View(String csrf,DTO.Identidade conta,boolean staff) {
        this.csrf=csrf;
        this.conta=conta;
        this.staff=staff;
    }

    static String e(Object valor) {
        return valor==null ? "—" : valor.toString().replace("&","&amp;").replace("<","&lt;")
            .replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }

    private static String dinheiro(Object valor) {
        return valor==null ? "—" : NumberFormat.getCurrencyInstance(BR).format(valor);
    }

    private static String data(Object valor) {
        return valor instanceof java.sql.Timestamp t ? t.toLocalDateTime().format(DATA) : e(valor);
    }

    private static String status(Object valor) {
        String texto=switch (valor.toString()) {
            case "DISPONIVEL" -> "Disponível";
            case "RESERVADO" -> "Reservado";
            case "VENDIDO" -> "Vendido";
            case "PENDENTE" -> "Pendente";
            case "REALIZADA" -> "Realizada";
            case "CANCELADA" -> "Cancelada";
            default -> valor.toString();
        };
        return e(texto);
    }

    String pagina(String titulo,String conteudo) {
        String nav="<a href='/'>Catálogo</a>";
        if (conta==null) nav+="<a href='/entrar'>Entrar</a>";
        else {
            nav+="<a href='/visitas'>"+(staff?"Agenda":"Minhas visitas")+"</a>";
            if (staff) nav+="<a href='/gestao/relatorios'>Relatórios</a><a href='/gestao/imoveis'>Cadastros</a>";
            nav+=form("/sair")+"<button class='link'>Sair</button></form>";
        }
        return """
            <!doctype html><html lang='pt-BR'><head><meta charset='utf-8'>
            <meta name='viewport' content='width=device-width, initial-scale=1'>
            <title>%s · Imobiliária</title><link rel='stylesheet' href='/estilo.css'></head>
            <body><header><a class='brand' href='/'>Imobiliária<span>.</span></a><nav aria-label='Principal'>%s</nav></header>
            <main><h1>%s</h1>%s</main><footer>Projeto acadêmico · Gustavo de Matos Ximenes</footer></body></html>
            """.formatted(e(titulo),nav,e(titulo),conteudo);
    }

    String catalogo(List<DTO.Imovel> imoveis) {
        var b=new StringBuilder("<p class='lead'>Consulte valores e encontre um horário para conhecer a casa com um corretor.</p><div class='cards'>");
        for (var i : imoveis) {
            b.append("<article class='card'><div class='card-top'><span>").append(e(i.bairro()))
                .append("</span><span class='status'>").append(status(i.status())).append("</span></div><h2>")
                .append(e(i.titulo())).append("</h2><p>").append(e(i.endereco())).append("</p><p class='price'>")
                .append(dinheiro(i.preco())).append("</p>");
            if (i.status()==DTO.StatusImovel.DISPONIVEL)
                b.append("<a class='button' href='/agendar?imovel=").append(i.id()).append("'>Ver horários</a>");
            b.append("</article>");
        }
        return pagina("Casas para visitar",b.append("</div>").toString());
    }

    String login(boolean cadastro,boolean confirmar) {
        String titulo=cadastro ? "Criar conta" : "Entrar";
        String mensagem=confirmar ? "<p class='ok' role='status'>Confira a caixa de entrada e confirme a conta antes de entrar.</p>" : "";
        String alternativa=cadastro ? "<a href='/entrar'>Já tenho conta</a>" : "<a href='/criar-conta'>Criar conta</a>";
        String senha="<label>Senha<input name='senha' type='password' autocomplete='"+(cadastro?"new-password":"current-password")
            +"' "+(cadastro?"minlength='8' ":"")+"maxlength='256' required></label>";
        return pagina(titulo,mensagem+form(cadastro?"/criar-conta":"/entrar", "narrow")
            +"<label>E-mail<input name='email' type='email' autocomplete='email' maxlength='254' required></label>"
            +senha+"<button>"+titulo+"</button><p>"+alternativa+"</p></form>");
    }

    String agendar(DTO.Imovel i,List<DTO.Horario> horarios,List<List<Object>> clientes) {
        String intro="<p class='lead'>"+e(i.bairro())+" · "+e(i.endereco())+"</p>";
        if (horarios.isEmpty()) return pagina(i.titulo(),intro+"<p>Não há horários livres nos próximos 21 dias.</p>");
        var b=new StringBuilder(intro).append(form("/agendar","narrow")).append(hidden("imovel",i.id()));
        if (staff) b.append(selecao("Cliente","cliente",clientes,null,true));
        b.append("<label>Horário com corretor<select name='horario' required><option value=''>Selecione</option>");
        for (var h : horarios) b.append(option(h.id(),h.inicio().format(DATA)+" · "+h.corretor(),null));
        b.append("</select></label><p>Visitas de 1 hora. Horário de Brasília.</p><button>Confirmar agendamento</button></form>");
        return pagina(i.titulo(),b.toString());
    }

    String visitas(List<List<Object>> linhas,List<List<Object>> clientes,List<List<Object>> corretores,
                   Long cliente,Long corretor,boolean ok) {
        var b=new StringBuilder(ok(ok));
        if (staff) {
            b.append("<form class='filters' action='/visitas'>").append(selecao("Histórico por cliente","cliente",clientes,cliente,false))
                .append(selecao("Agenda por corretor","corretor",corretores,corretor,false)).append("<button>Filtrar</button></form>");
        }
        var registros=new ArrayList<List<String>>();
        for (var r : linhas) {
            String acoes="—";
            if ("PENDENTE".equals(r.get(5))) {
                acoes=form("/visitas/status","inline")+hidden("id",r.get(0))
                    +(staff ? "<button name='status' value='REALIZADA'>Realizar</button>" : "")
                    +"<button class='secondary' name='status' value='CANCELADA'>Cancelar</button></form>";
            }
            registros.add(List.of(e(r.get(1)),e(r.get(2)),e(r.get(3)),data(r.get(4)),status(r.get(5)),dinheiro(r.get(6)),acoes));
        }
        return pagina(staff?"Agenda de visitas":"Minhas visitas",
            b+grade(List.of("Cliente","Imóvel","Corretor","Horário","Status","Preço na visita","Ações"),registros));
    }

    String gestao(String tipo,ImobiliariaDAO dao,boolean ok) throws SQLException {
        var abas="<div class='tabs'><a href='/gestao/imoveis'>Imóveis</a><a href='/gestao/clientes'>Clientes</a>"
            +"<a href='/gestao/corretores'>Corretores</a><a href='/gestao/proprietarios'>Proprietários</a>"
            +"<a href='/gestao/horarios'>Horários</a></div>";
        String titulo;
        String campos;
        String dados;
        switch (tipo) {
            case "imoveis" -> {
                titulo="Imóveis";
                campos=input("Título","titulo","text",100)+input("Bairro","bairro","text",70)
                    +input("Endereço","endereco","text",180)+input("Preço em R$","preco","text",15)
                    +selecao("Proprietário","proprietario",dao.proprietarios(),null,true);
                var linhas=new ArrayList<List<String>>();
                for (var i : dao.imoveis(false)) {
                    String caixa=form("/gestao/imovel/status","inline")+hidden("id",i.id())+"<select aria-label='Status do imóvel' name='status'>";
                    for (var s : DTO.StatusImovel.values()) caixa+=option(s,switch(s) {
                        case DISPONIVEL -> "Disponível";
                        case RESERVADO -> "Reservado";
                        case VENDIDO -> "Vendido";
                    },i.status());
                    caixa+="</select><button>Atualizar</button></form>";
                    linhas.add(List.of(e(i.titulo()),e(i.bairro()),dinheiro(i.preco()),caixa));
                }
                dados=grade(List.of("Imóvel","Bairro","Preço","Status"),linhas);
            }
            case "horarios" -> {
                titulo="Horários dos corretores";
                campos=selecao("Corretor","corretor",dao.corretores(),null,true)
                    +"<label>Hora cheia (Brasília)<input name='inicio' type='datetime-local' required step='60'></label>";
                dados="<p>Os horários livres ficam disponíveis no catálogo na janela dos próximos 21 dias.</p>";
            }
            default -> {
                titulo=switch(tipo) {case "clientes" -> "Clientes"; case "corretores" -> "Corretores"; default -> "Proprietários";};
                campos=input("Nome","nome","text",100)+input("E-mail","email","email",254);
                if (tipo.equals("corretores")) {
                    campos+=input("CRECI","creci","text",30);
                    dados=tabela(List.of("ID","Nome","E-mail","CRECI"),dao.corretores());
                } else dados=tabela(List.of("ID","Nome","E-mail"),tipo.equals("clientes")?dao.clientes():dao.proprietarios());
            }
        }
        return pagina(titulo,abas+ok(ok)+"<h2>Novo cadastro</h2>"+form("/gestao/"+tipo,"filters")
            +campos+"<button>Salvar</button></form>"+dados);
    }

    String relatorios(DTO.Relatorios relatorios) {
        var b=new StringBuilder("<div class='metric'><span>Ticket médio por visita realizada</span><strong>")
            .append(dinheiro(relatorios.ticketMedio())).append("</strong><p>Média do preço registrado no agendamento.</p></div>");
        b.append("<div class='panels'><section><h2>Ranking de imóveis</h2>")
            .append(tabela(List.of("Imóvel","Realizadas"),relatorios.ranking()))
            .append("</section><section><h2>Baixa procura</h2><p>Casas disponíveis há mais de 30 dias sem visita realizada recente.</p>");
        b.append(grade(List.of("Imóvel","Bairro","Última visita"),relatorios.baixaProcura().stream()
            .map(r->List.of(e(r.get(0)),e(r.get(1)),r.get(2)==null?"Nenhuma":data(r.get(2)))).toList()));
        b.append("</section></div><section><h2>Produtividade por corretor</h2>")
            .append(tabela(List.of("Corretor","Realizadas","Pendentes","Canceladas"),relatorios.produtividade()))
            .append("<p><a href='/visitas'>Abrir agenda e histórico por cliente</a></p></section>");
        b.append("<div class='panels'><section><h2>Visitas realizadas por bairro</h2>").append(barras(relatorios.bairros(),View::e))
            .append("</section><section><h2>Status das visitas</h2>").append(barras(relatorios.statusDasVisitas(),View::status))
            .append("</section><section><h2>Agendamentos por dia da visita</h2>")
            .append(barras(relatorios.picosPorDia(),v->List.of("Seg","Ter","Qua","Qui","Sex","Sáb","Dom").get(((Number)v).intValue()-1)))
            .append("</section><section><h2>Agendamentos por horário</h2>")
            .append(barras(relatorios.picosPorHora(),v->e(v)+"h")).append("</section></div>");
        return pagina("Relatórios de visitas",b.toString());
    }

    private static String barras(List<List<Object>> linhas, Function<Object,String> rotulo) {
        if (linhas.isEmpty()) return "<p>Sem dados para exibir.</p>";
        long max=linhas.stream().mapToLong(r->((Number)r.get(1)).longValue()).max().orElse(1);
        var b=new StringBuilder("<ul class='chart'>");
        for (var r:linhas) {
            long n=((Number)r.get(1)).longValue();
            b.append("<li><span>").append(rotulo.apply(r.get(0))).append("</span><meter aria-label='")
                .append(rotulo.apply(r.get(0))).append("' max='").append(Math.max(1,max)).append("' value='")
                .append(n).append("'></meter><strong>").append(n).append("</strong></li>");
        }
        return b.append("</ul>").toString();
    }

    private static String tabela(List<String> cab,List<List<Object>> linhas) {
        return grade(cab,linhas.stream().map(r->r.stream().map(View::e).toList()).toList());
    }

    private static String grade(List<String> cab,List<List<String>> linhas) {
        if (linhas.isEmpty()) return "<p>Nenhum registro encontrado.</p>";
        var b=new StringBuilder("<div class='table-wrap'><table><thead><tr>");
        for (String col:cab) b.append("<th scope='col'>").append(e(col)).append("</th>");
        b.append("</tr></thead><tbody>");
        for (var r:linhas) {
            b.append("<tr>");
            for (String col:r) b.append("<td>").append(col).append("</td>");
            b.append("</tr>");
        }
        return b.append("</tbody></table></div>").toString();
    }

    private String form(String rota) { return form(rota,""); }

    private String form(String rota,String classe) {
        return "<form method='post' action='"+rota+"' class='"+classe+"'>"+hidden("csrf",csrf);
    }

    private static String hidden(String nome,Object valor) {
        return "<input type='hidden' name='"+nome+"' value='"+e(valor)+"'>";
    }

    private static String input(String rotulo,String nome,String tipo,int max) {
        return "<label>"+rotulo+"<input name='"+nome+"' type='"+tipo+"' maxlength='"+max+"' required></label>";
    }

    private static String selecao(String rotulo,String nome,List<List<Object>> lista,Long atual,boolean obrigatorio) {
        var b=new StringBuilder("<label>").append(rotulo).append("<select name='").append(nome).append("'")
            .append(obrigatorio?" required":"").append("><option value=''>")
            .append(obrigatorio?"Selecione":"Todos").append("</option>");
        for (var r:lista) b.append(option(r.get(0),r.get(1),atual));
        return b.append("</select></label>").toString();
    }

    private static String option(Object valor,Object rotulo,Object atual) {
        return "<option value='"+e(valor)+"'"+(Objects.equals(valor,atual)?" selected":"")+">"+e(rotulo)+"</option>";
    }

    private static String ok(boolean ok) {
        return ok ? "<p class='ok' role='status'>Alteração salva.</p>" : "";
    }
}
