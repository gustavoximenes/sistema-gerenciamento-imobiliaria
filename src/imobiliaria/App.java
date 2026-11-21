package imobiliaria;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;

public final class App {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> TIPOS = Set.of("clientes","corretores","proprietarios","imoveis","horarios");
    private final ImobiliariaDAO dao;
    private final ImobiliariaBO bo;
    private final SupabaseAuth auth;
    private final Set<String> equipe;
    private final int porta;

    private App(ImobiliariaDAO dao, SupabaseAuth auth, Set<String> equipe, int porta) {
        this.dao=dao;
        this.bo=new ImobiliariaBO(dao);
        this.auth=auth;
        this.equipe=equipe;
        this.porta=porta;
    }

    public static void main(String[] args) throws Exception {
        var conf = new Properties();
        try (var reader=Files.newBufferedReader(Path.of("config/supabase.properties"))) { conf.load(reader); }
        var env=System.getenv();
        var equipe=new HashSet<String>();
        for (String email : env.getOrDefault("IMOB_EQUIPE_EMAILS","").split(","))
            if (!email.isBlank()) equipe.add(email.strip().toLowerCase(Locale.ROOT));
        int porta=Integer.parseInt(env.getOrDefault("IMOB_PORTA","8080"));
        Files.createDirectories(Path.of("data"));
        var dao=new ImobiliariaDAO("jdbc:h2:./data/imobiliaria;MODE=PostgreSQL");
        dao.iniciar();
        var app=new App(dao,new SupabaseAuth(env.getOrDefault("SUPABASE_URL",conf.getProperty("url")),
            env.getOrDefault("SUPABASE_PUBLISHABLE_KEY",conf.getProperty("key"))),equipe,porta);
        var servidor=HttpServer.create(new InetSocketAddress("127.0.0.1",porta),0);
        servidor.createContext("/",app::atender);
        servidor.setExecutor(Executors.newFixedThreadPool(12));
        servidor.start();
        System.out.println("Imobiliária disponível em http://localhost:"+porta);
    }

    private void atender(HttpExchange eq) throws IOException {
        var h=eq.getResponseHeaders();
        h.set("X-Content-Type-Options","nosniff");
        h.set("Referrer-Policy","same-origin");
        h.set("Content-Security-Policy","default-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
        h.set("Cache-Control","no-store");
        String csrf=cookie(eq,"imob_csrf");
        boolean post=eq.getRequestMethod().equals("POST");
        try {
            String host=eq.getRequestHeaders().getFirst("Host");
            if (!("localhost:"+porta).equalsIgnoreCase(host) && !("127.0.0.1:"+porta).equals(host))
                throw new Falha(400,"Abra o endereço localhost indicado no terminal.");
            if (!post && !eq.getRequestMethod().equals("GET")) throw new Falha(405,"Método indisponível.");
            String rota=eq.getRequestURI().getPath();
            if (!post && rota.equals("/estilo.css")) {
                enviar(eq,200,"text/css",Files.readString(Path.of("web/estilo.css")));
                return;
            }
            if (!post && csrf==null) {
                byte[] bytes=new byte[32];
                RANDOM.nextBytes(bytes);
                csrf=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                cookie(eq,"imob_csrf",csrf,86400);
            }
            if (post && (eq.getRequestHeaders().getFirst("Content-Type")==null ||
                !eq.getRequestHeaders().getFirst("Content-Type").startsWith("application/x-www-form-urlencoded")))
                throw new Falha(415,"Formato de formulário inválido.");
            byte[] corpo=post ? eq.getRequestBody().readNBytes(8193) : new byte[0];
            if (corpo.length>8192) throw new Falha(413,"Formulário muito grande.");
            var f=parametros(post ? new String(corpo,StandardCharsets.UTF_8) : eq.getRequestURI().getRawQuery());
            if (post && (csrf==null || !csrf.equals(f.get("csrf")))) throw new Falha(403,"Atualize a página e reenvie o formulário.");
            String token=cookie(eq,"imob_session");
            if (post && rota.equals("/sair")) {
                cookie(eq,"imob_session","",0);
                try { auth.sair(token); } catch (Falha indisponivel) { System.err.println("O logout remoto está indisponível."); }
                redirecionar(eq,"/");
                return;
            }
            var identidade=token==null ? null : auth.verificar(token);
            boolean staff=identidade!=null && equipe.contains(identidade.email().toLowerCase(Locale.ROOT));
            var view=new View(csrf,identidade,staff);
            if (post && rota.equals("/entrar")) {
                String novo=auth.entrar(ImobiliariaBO.email(f),senha(f));
                if (auth.verificar(novo)==null) throw new Falha(401,"Confirme o e-mail antes de entrar.");
                cookie(eq,"imob_session",novo,3600);
                redirecionar(eq,"/");
                return;
            }
            if (post && rota.equals("/criar-conta")) {
                auth.cadastrar(ImobiliariaBO.email(f),senha(f));
                redirecionar(eq,"/entrar?confirmar=1");
                return;
            }
            if (rota.startsWith("/gestao") && identidade!=null && !staff)
                throw new Falha(403,"Acesso reservado à equipe.");
            boolean restrita=rota.startsWith("/gestao") || rota.equals("/agendar") || rota.equals("/visitas") || rota.equals("/visitas/status");
            if (restrita && identidade==null) {
                redirecionar(eq,"/entrar");
                return;
            }
            Long cliente=identidade==null || staff || !restrita ? null : dao.clienteDaConta(
                new DTO.Identidade(identidade.id(),identidade.email().toLowerCase(Locale.ROOT)));
            if (post) {
                switch (rota) {
                    case "/agendar" -> { bo.agendar(f,cliente); redirecionar(eq,"/visitas?ok=1"); }
                    case "/visitas/status" -> { bo.mudarVisita(f,cliente); redirecionar(eq,"/visitas?ok=1"); }
                    case "/gestao/imovel/status" -> { bo.mudarImovel(f); redirecionar(eq,"/gestao/imoveis?ok=1"); }
                    default -> {
                        String tipo=tipo(rota);
                        if (tipo==null) throw new Falha(404,"Página não encontrada.");
                        bo.cadastrar(tipo,f);
                        redirecionar(eq,"/gestao/"+tipo+"?ok=1");
                    }
                }
                return;
            }
            String pagina=switch (rota) {
                case "/" -> view.catalogo(dao.imoveis(false));
                case "/entrar" -> view.login(false,f.containsKey("confirmar"));
                case "/criar-conta" -> view.login(true,false);
                case "/agendar" -> {
                    long id=ImobiliariaBO.id(f,"imovel");
                    var imovel=dao.imoveis(true).stream().filter(i->i.id()==id).findFirst()
                        .orElseThrow(()->new Falha(404,"Imóvel indisponível."));
                    yield view.agendar(imovel,dao.horarios(id,cliente,bo.agora()),staff ? dao.clientes() : List.of());
                }
                case "/visitas" -> {
                    Long filtro=staff && f.containsKey("cliente") && !f.get("cliente").isEmpty() ? Long.valueOf(ImobiliariaBO.id(f,"cliente")) : cliente;
                    Long corretor=staff && f.containsKey("corretor") && !f.get("corretor").isEmpty() ? ImobiliariaBO.id(f,"corretor") : null;
                    yield view.visitas(dao.visitas(filtro,corretor),staff ? dao.clientes() : List.of(),
                        staff ? dao.corretores() : List.of(),filtro,corretor,f.containsKey("ok"));
                }
                case "/gestao", "/gestao/relatorios" -> view.relatorios(bo.relatorios());
                default -> {
                    String tipo=tipo(rota);
                    if (tipo==null) throw new Falha(404,"Página não encontrada.");
                    yield view.gestao(tipo,dao,f.containsKey("ok"));
                }
            };
            enviar(eq,200,"text/html",pagina);
        } catch (Falha e) {
            erro(eq,e.status,e.getMessage(),csrf);
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            erro(eq,400,"Revise os campos do formulário.",csrf);
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) erro(eq,409,"Cadastro duplicado ou conflito de horário. Atualize a página.",csrf);
            else if ("23506".equals(e.getSQLState())) erro(eq,400,"Selecione um registro válido.",csrf);
            else {
                System.err.println("Falha SQL: "+e.getSQLState()+" "+e.getErrorCode());
                erro(eq,500,"Não foi possível acessar o banco de dados.",csrf);
            }
        } finally {
            eq.close();
        }
    }

    private static String tipo(String rota) {
        if (!rota.startsWith("/gestao/")) return null;
        String tipo=rota.substring("/gestao/".length());
        return TIPOS.contains(tipo) ? tipo : null;
    }

    private static String senha(Map<String,String> f) throws Falha {
        String senha=f.getOrDefault("senha","");
        if (senha.isEmpty() || senha.length()>256) throw new Falha(400,"Informe uma senha válida.");
        return senha;
    }

    private static Map<String,String> parametros(String texto) {
        Map<String,String> f=new HashMap<>();
        if (texto==null || texto.isEmpty()) return f;
        for (String parte : texto.split("&")) {
            String[] par=parte.split("=",2);
            if (par.length==2) f.put(URLDecoder.decode(par[0],StandardCharsets.UTF_8),URLDecoder.decode(par[1],StandardCharsets.UTF_8));
        }
        return f;
    }

    private static String cookie(HttpExchange eq,String chave) {
        for (String cab : eq.getRequestHeaders().getOrDefault("Cookie",List.of()))
            for (String parte : cab.split(";"))
                if (parte.strip().startsWith(chave+"=")) return parte.strip().substring(chave.length()+1);
        return null;
    }

    private static void cookie(HttpExchange eq,String chave,String valor,int segundos) {
        eq.getResponseHeaders().add("Set-Cookie",chave+"="+valor+"; Path=/; HttpOnly; SameSite=Strict; Max-Age="+segundos);
    }

    private static void redirecionar(HttpExchange eq,String rota) throws IOException {
        eq.getResponseHeaders().set("Location",rota);
        eq.sendResponseHeaders(303,-1);
    }

    private static void erro(HttpExchange eq,int status,String mensagem,String csrf) throws IOException {
        enviar(eq,status,"text/html",new View(csrf,null,false).pagina("Revise a operação",
            "<p role='alert'>"+View.e(mensagem)+"</p><p><a href='/'>Voltar ao catálogo</a></p>"));
    }

    private static void enviar(HttpExchange eq,int status,String tipo,String conteudo) throws IOException {
        byte[] bytes=conteudo.getBytes(StandardCharsets.UTF_8);
        eq.getResponseHeaders().set("Content-Type",tipo+"; charset=UTF-8");
        eq.sendResponseHeaders(status,bytes.length);
        eq.getResponseBody().write(bytes);
    }
}
