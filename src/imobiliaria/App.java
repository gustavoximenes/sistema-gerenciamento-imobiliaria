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
    private static final Set<String> TIPOS = Set.of("clientes","corretores","proprietarios","imoveis","horarios","usuarios");
    private final ImobiliariaDAO dao;
    private final ImobiliariaBO bo;
    private final SupabaseAuth auth;
    private final int porta;

    private App(ImobiliariaDAO dao, SupabaseAuth auth, int porta) {
        this.dao=dao;
        this.bo=new ImobiliariaBO(dao);
        this.auth=auth;
        this.porta=porta;
    }

    public static void main(String[] args) throws Exception {
        var conf = new Properties();
        try (var reader=Files.newBufferedReader(Path.of("config/supabase.properties"))) { conf.load(reader); }
        var env=System.getenv();
        String url=env.getOrDefault("SUPABASE_URL",conf.getProperty("url"));
        String key=env.getOrDefault("SUPABASE_PUBLISHABLE_KEY",conf.getProperty("key"));
        int porta=Integer.parseInt(env.getOrDefault("IMOB_PORTA","8080"));
        var app=new App(new ImobiliariaDAO(url,key),new SupabaseAuth(url,key),porta);
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
                byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes);
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
                try { auth.sair(token); } catch (Falha indisponivel) { System.err.println("Logout remoto indisponível."); }
                redirecionar(eq,"/"); return;
            }
            var identidade=token==null ? null : auth.verificar(token);
            if (identidade!=null) dao.usarToken(token);
            boolean staff=identidade!=null && dao.equipeAtual();
            var view=new View(csrf,identidade,staff);

            if (post && rota.equals("/entrar")) {
                String novo=auth.entrar(ImobiliariaBO.email(f),senhaLogin(f));
                if (auth.verificar(novo)==null) throw new Falha(401,"Confirme o e-mail antes de entrar.");
                cookie(eq,"imob_session",novo,3600);
                redirecionar(eq,"/perfil"); return;
            }
            if (post && rota.equals("/criar-conta")) {
                auth.cadastrar(ImobiliariaBO.nome(f),ImobiliariaBO.email(f),senha(f));
                redirecionar(eq,"/entrar?confirmar=1"); return;
            }

            boolean perfil=rota.equals("/perfil")||rota.equals("/perfil/historico/limpar");
            boolean restrita=rota.startsWith("/gestao")||rota.equals("/agendar")||rota.equals("/visitas")||
                rota.equals("/visitas/status")||perfil;
            if(restrita&&identidade==null){redirecionar(eq,"/entrar");return;}
            if(rota.startsWith("/gestao")&&!staff) throw new Falha(403,"Acesso reservado à equipe.");
            Long cliente=identidade==null||staff?null:dao.clienteDaConta(identidade);

            if (post) {
                switch (rota) {
                    case "/agendar" -> { bo.agendar(f,cliente); redirecionar(eq,"/visitas?ok=1"); }
                    case "/visitas/status" -> { bo.mudarVisita(f,cliente); redirecionar(eq,"/visitas?ok=1"); }
                    case "/perfil" -> { bo.atualizarPerfil(f,identidade); redirecionar(eq,"/perfil?ok=1"); }
                    case "/perfil/historico/limpar" -> { dao.limparHistorico(); redirecionar(eq,"/perfil?historico=limpo"); }
                    case "/gestao/imovel/status" -> { bo.mudarImovel(f); redirecionar(eq,"/gestao/imoveis?ok=1"); }
                    case "/gestao/imoveis/editar" -> { bo.editar("imoveis",f); redirecionar(eq,"/gestao/imoveis?ok=1"); }
                    case "/gestao/clientes/editar" -> { bo.editar("clientes",f); redirecionar(eq,"/gestao/clientes?ok=1"); }
                    case "/gestao/corretores/editar" -> { bo.editar("corretores",f); redirecionar(eq,"/gestao/corretores?ok=1"); }
                    case "/gestao/proprietarios/editar" -> { bo.editar("proprietarios",f); redirecionar(eq,"/gestao/proprietarios?ok=1"); }
                    case "/gestao/usuarios/senha" -> { bo.alterarSenha(f); redirecionar(eq,"/gestao/usuarios?ok=1"); }
                    default -> {
                        String tipo=tipo(rota);
                        if(tipo==null) throw new Falha(404,"Página não encontrada.");
                        bo.cadastrar(tipo,f); redirecionar(eq,"/gestao/"+tipo+"?ok=1");
                    }
                }
                return;
            }

            String pagina=switch(rota){
                case "/" -> view.home(dao.imoveis(true));
                case "/imoveis" -> view.catalogo(dao.imoveis(false));
                case "/imovel" -> {
                    var imovel=dao.imovel(ImobiliariaBO.id(f,"id"));
                    if(identidade!=null) dao.registrarConsulta(identidade.id(),imovel.id());
                    yield view.detalhe(imovel);
                }
                case "/sobre" -> view.sobre();
                case "/contato" -> view.contato();
                case "/entrar" -> view.login(false,f.containsKey("confirmar"));
                case "/criar-conta" -> view.login(true,false);
                case "/perfil" -> view.perfil(dao.perfil(identidade.id()),dao.historico(),f.containsKey("ok"),f.containsKey("historico"));
                case "/agendar" -> {
                    long id=ImobiliariaBO.id(f,"imovel");
                    var imovel=dao.imovel(id);
                    if(imovel.status()!=DTO.StatusImovel.DISPONIVEL) throw new Falha(404,"Imóvel indisponível.");
                    yield view.agendar(imovel,staff?dao.clientes():List.of());
                }
                case "/visitas" -> {
                    Long filtro=cliente;
                    if(staff&&f.containsKey("cliente")&&!f.get("cliente").isEmpty()) filtro=ImobiliariaBO.id(f,"cliente");
                    Long corretor=staff&&f.containsKey("corretor")&&!f.get("corretor").isEmpty()?ImobiliariaBO.id(f,"corretor"):null;
                    yield view.visitas(dao.visitas(filtro,corretor),staff?dao.clientes():List.of(),
                        staff?dao.corretores():List.of(),filtro,corretor,f.containsKey("ok"));
                }
                case "/gestao", "/gestao/relatorios" -> view.relatorios(bo.relatorios());
                case "/gestao/imoveis/editar" -> view.editarImovel(dao.imovel(ImobiliariaBO.id(f,"id")),dao.proprietarios());
                case "/gestao/clientes/editar" -> view.editarCliente(dao.cliente(ImobiliariaBO.id(f,"id")));
                case "/gestao/corretores/editar" -> view.editarCorretor(dao.corretor(ImobiliariaBO.id(f,"id")));
                case "/gestao/proprietarios/editar" -> view.editarProprietario(dao.proprietario(ImobiliariaBO.id(f,"id")));
                default -> {
                    String tipo=tipo(rota);
                    if(tipo==null) throw new Falha(404,"Página não encontrada.");
                    yield view.gestao(tipo,dao,f.containsKey("ok"));
                }
            };
            enviar(eq,200,"text/html",pagina);
        } catch(Falha e){ erro(eq,e.status,e.getMessage(),csrf); }
        catch(IllegalArgumentException|java.time.DateTimeException e){ erro(eq,400,"Revise os campos do formulário.",csrf); }
        catch(SQLException e){
            if("23505".equals(e.getSQLState())) erro(eq,409,"Cadastro duplicado ou conflito de horário. Atualize a página.",csrf);
            else if("42501".equals(e.getSQLState())) erro(eq,403,"Sua conta não tem permissão para essa operação.",csrf);
            else { System.err.println("Falha Supabase: "+e.getMessage()); erro(eq,503,"Não foi possível acessar o banco do Supabase.",csrf); }
        } catch(RuntimeException e){
            e.printStackTrace();
            erro(eq,500,"Ocorreu uma falha inesperada ao montar a página.",csrf);
        } finally { dao.limparToken(); eq.close(); }
    }

    private static String tipo(String rota){if(!rota.startsWith("/gestao/"))return null;String t=rota.substring("/gestao/".length());return TIPOS.contains(t)?t:null;}
    private static String senha(Map<String,String> f)throws Falha{String s=f.getOrDefault("senha","");if(s.length()<8||s.length()>256)throw new Falha(400,"A senha deve ter pelo menos 8 caracteres.");return s;}
    private static String senhaLogin(Map<String,String> f)throws Falha{String s=f.getOrDefault("senha","");if(s.isBlank()||s.length()>256)throw new Falha(400,"Informe a senha.");return s;}
    private static Map<String,String> parametros(String texto){Map<String,String> f=new HashMap<>();if(texto==null||texto.isEmpty())return f;for(String parte:texto.split("&")){String[] p=parte.split("=",2);if(p.length==2)f.put(URLDecoder.decode(p[0],StandardCharsets.UTF_8),URLDecoder.decode(p[1],StandardCharsets.UTF_8));}return f;}
    private static String cookie(HttpExchange eq,String chave){for(String cab:eq.getRequestHeaders().getOrDefault("Cookie",List.of()))for(String p:cab.split(";"))if(p.strip().startsWith(chave+"="))return p.strip().substring(chave.length()+1);return null;}
    private static void cookie(HttpExchange eq,String chave,String valor,int segundos){eq.getResponseHeaders().add("Set-Cookie",chave+"="+valor+"; Path=/; HttpOnly; SameSite=Strict; Max-Age="+segundos);}
    private static void redirecionar(HttpExchange eq,String rota)throws IOException{eq.getResponseHeaders().set("Location",rota);eq.sendResponseHeaders(303,-1);}
    private static void erro(HttpExchange eq,int status,String mensagem,String csrf)throws IOException{enviar(eq,status,"text/html",new View(csrf,null,false).pagina("Revise a operação","<div class='notice error' role='alert'>"+View.e(mensagem)+"</div><p><a href='/'>Voltar à página inicial</a></p>"));}
    private static void enviar(HttpExchange eq,int status,String tipo,String conteudo)throws IOException{byte[] b=conteudo.getBytes(StandardCharsets.UTF_8);eq.getResponseHeaders().set("Content-Type",tipo+"; charset=UTF-8");eq.sendResponseHeaders(status,b.length);eq.getResponseBody().write(b);}
}
