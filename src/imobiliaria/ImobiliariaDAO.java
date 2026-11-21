package imobiliaria;

import com.google.gson.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

public final class ImobiliariaDAO {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final Gson gson = new Gson();
    private final String restUrl;
    private final String key;
    private final ThreadLocal<String> tokenAtual = new ThreadLocal<>();

    public ImobiliariaDAO(String supabaseUrl, String key) {
        URI base = URI.create(supabaseUrl);
        if (!"https".equals(base.getScheme()) &&
            !("http".equals(base.getScheme()) && "127.0.0.1".equals(base.getHost())))
            throw new IllegalArgumentException("Use HTTPS para o Supabase.");
        this.restUrl = supabaseUrl.replaceFirst("/+$", "") + "/rest/v1/";
        this.key = Objects.requireNonNull(key);
    }

    public void usarToken(String token) { tokenAtual.set(token); }
    public void limparToken() { tokenAtual.remove(); }

    public boolean equipeAtual() throws SQLException {
        JsonElement json = requisitar("POST", "rpc/equipe_atual", new JsonObject(), null);
        return json.getAsBoolean();
    }

    public long clienteDaConta(DTO.Identidade conta) throws SQLException, Falha {
        JsonArray linhas = get("cliente?select=id&auth_id=eq." + conta.id());
        if (!linhas.isEmpty()) return numero(linhas.get(0).getAsJsonObject(), "id");
        JsonObject novo = new JsonObject();
        novo.addProperty("auth_id", conta.id().toString());
        novo.addProperty("nome", conta.email().split("@", 2)[0]);
        novo.addProperty("email", conta.email().toLowerCase(Locale.ROOT));
        requisitar("POST", "cliente", novo, "return=representation");
        linhas = get("cliente?select=id&auth_id=eq." + conta.id());
        if (linhas.isEmpty()) throw new Falha(409, "Não foi possível criar o perfil da conta.");
        return numero(linhas.get(0).getAsJsonObject(), "id");
    }

    public DTO.Cliente perfil(UUID authId) throws SQLException, Falha {
        JsonArray linhas = get("cliente?select=id,nome,email,telefone,auth_id&auth_id=eq."+authId+"&limit=1");
        if (linhas.isEmpty()) throw new Falha(404, "Perfil não encontrado.");
        JsonObject o = linhas.get(0).getAsJsonObject();
        return new DTO.Cliente(numero(o,"id"), texto(o,"nome"), texto(o,"email"),
            textoNulo(o,"telefone"), UUID.fromString(texto(o,"auth_id")));
    }

    public void atualizarPerfil(String nome, String telefone, UUID authId) throws SQLException {
        JsonObject body = new JsonObject();
        body.addProperty("nome", nome);
        if (telefone == null || telefone.isBlank()) body.add("telefone", JsonNull.INSTANCE);
        else body.addProperty("telefone", telefone);
        requisitar("PATCH", "cliente?auth_id=eq." + authId, body, "return=minimal");
    }

    public void registrarConsulta(UUID authId, long imovelId) throws SQLException {
        JsonObject body = new JsonObject();
        body.addProperty("auth_id", authId.toString());
        body.addProperty("imovel_id", imovelId);
        requisitar("POST", "historico_navegacao", body, "return=minimal");
    }

    public List<DTO.Historico> historico() throws SQLException {
        JsonArray linhas = get("vw_historico_navegacao?select=id,imovel_id,titulo,bairro,preco,status,consultado_em"
            + "&order=consultado_em.desc&limit=20");
        List<DTO.Historico> resultado = new ArrayList<>();
        for (JsonElement linha : linhas) {
            JsonObject o = linha.getAsJsonObject();
            resultado.add(new DTO.Historico(numero(o,"id"),numero(o,"imovel_id"),texto(o,"titulo"),
                texto(o,"bairro"),decimal(o,"preco"),DTO.StatusImovel.valueOf(texto(o,"status")),
                dataHora(texto(o,"consultado_em"))));
        }
        return resultado;
    }

    public void limparHistorico() throws SQLException {
        requisitar("DELETE", "historico_navegacao?id=gt.0", null, "return=minimal");
    }

    public void salvar(DTO.Cliente c) throws SQLException {
        JsonObject o = new JsonObject();
        o.addProperty("nome",c.nome()); o.addProperty("email",c.email());
        if (c.telefone()!=null && !c.telefone().isBlank()) o.addProperty("telefone",c.telefone());
        requisitar("POST","cliente",o,"return=minimal");
    }

    public void salvar(DTO.Corretor c) throws SQLException {
        JsonObject o = new JsonObject();
        o.addProperty("nome",c.nome()); o.addProperty("email",c.email()); o.addProperty("creci",c.creci());
        requisitar("POST","corretor",o,"return=minimal");
    }

    public void salvar(DTO.Proprietario p) throws SQLException {
        JsonObject o = new JsonObject();
        o.addProperty("nome",p.nome()); o.addProperty("email",p.email());
        if (p.telefone()!=null && !p.telefone().isBlank()) o.addProperty("telefone",p.telefone());
        requisitar("POST","proprietario",o,"return=minimal");
    }

    public void salvar(DTO.Imovel i) throws SQLException {
        JsonObject o = imovelJson(i);
        requisitar("POST","imovel",o,"return=minimal");
    }

    public void salvar(DTO.Horario h) throws SQLException {
        JsonObject o = new JsonObject();
        o.addProperty("corretor_id",h.corretorId());
        o.addProperty("inicio",h.inicio().atZone(FUSO).toOffsetDateTime().toString());
        requisitar("POST","horario",o,"return=minimal");
    }

    public void mudarImovel(long id, DTO.StatusImovel status) throws SQLException, Falha {
        if (status != DTO.StatusImovel.DISPONIVEL) {
            JsonArray pendentes = get("visita?select=id&imovel_id=eq."+id+"&status=eq.PENDENTE&limit=1");
            if (!pendentes.isEmpty()) throw new Falha(409,"Resolva as visitas pendentes antes de mudar o status deste imóvel.");
        }
        JsonObject o = new JsonObject(); o.addProperty("status",status.name());
        requisitar("PATCH","imovel?id=eq."+id,o,"return=minimal");
    }

    public void reservar(long cliente, long imovel, long horario, LocalDateTime agora) throws SQLException, Falha {
        JsonArray casas = get("imovel?select=preco,status&id=eq."+imovel+"&limit=1");
        if (casas.isEmpty() || !"DISPONIVEL".equals(texto(casas.get(0).getAsJsonObject(),"status")))
            throw new Falha(409,"O imóvel está indisponível.");
        JsonArray horas = get("horario?select=inicio&id=eq."+horario+"&limit=1");
        if (horas.isEmpty()) throw new Falha(409,"O horário está indisponível.");
        String inicio = texto(horas.get(0).getAsJsonObject(),"inicio");
        if (!dataHora(inicio).isAfter(agora)) throw new Falha(409,"O horário já passou.");
        JsonObject o = new JsonObject();
        o.addProperty("cliente_id",cliente); o.addProperty("imovel_id",imovel);
        o.addProperty("horario_id",horario); o.addProperty("inicio",inicio);
        o.addProperty("preco_registrado",decimal(casas.get(0).getAsJsonObject(),"preco"));
        o.addProperty("status","PENDENTE");
        requisitar("POST","visita",o,"return=minimal");
    }

    public void mudarVisita(long id, Long cliente, DTO.StatusVisita status, LocalDateTime agora)
            throws SQLException, Falha {
        JsonArray linhas = get("visita?select=id,cliente_id,inicio,status&id=eq."+id+"&limit=1");
        if (linhas.isEmpty()) throw new Falha(404,"Visita não encontrada.");
        JsonObject atual = linhas.get(0).getAsJsonObject();
        if (!"PENDENTE".equals(texto(atual,"status")) ||
            (cliente != null && numero(atual,"cliente_id") != cliente) ||
            (status == DTO.StatusVisita.REALIZADA && dataHora(texto(atual,"inicio")).isAfter(agora)))
            throw new Falha(409,"A visita não permite essa alteração.");
        JsonObject o = new JsonObject(); o.addProperty("status",status.name());
        requisitar("PATCH","visita?id=eq."+id,o,"return=minimal");
    }

    public List<List<Object>> clientes() throws SQLException {
        return linhas(get("cliente?select=id,nome,email&order=nome"),"id","nome","email");
    }

    public List<List<Object>> corretores() throws SQLException {
        return linhas(get("corretor?select=id,nome,email,creci&order=nome"),"id","nome","email","creci");
    }

    public List<List<Object>> proprietarios() throws SQLException {
        return linhas(get("proprietario?select=id,nome,email&order=nome"),"id","nome","email");
    }

    public List<DTO.Imovel> imoveis(boolean disponiveis) throws SQLException {
        String filtro = disponiveis ? "&status=eq.DISPONIVEL" : "";
        JsonArray dados = get("imovel?select=id,proprietario_id,titulo,descricao,tipo,bairro,cidade,endereco,quartos,banheiros,vagas,area_m2,preco,destaque,status"
            + filtro + "&order=destaque.desc,titulo");
        List<DTO.Imovel> resultado = new ArrayList<>();
        for (JsonElement e : dados) resultado.add(imovel(e.getAsJsonObject()));
        return resultado;
    }

    public DTO.Imovel imovel(long id) throws SQLException, Falha {
        JsonArray dados = get("imovel?select=id,proprietario_id,titulo,descricao,tipo,bairro,cidade,endereco,quartos,banheiros,vagas,area_m2,preco,destaque,status&id=eq."+id+"&limit=1");
        if (dados.isEmpty()) throw new Falha(404,"Imóvel não encontrado.");
        return imovel(dados.get(0).getAsJsonObject());
    }

    public List<DTO.Horario> horarios(long imovel, Long cliente, LocalDateTime agora) throws SQLException {
        JsonObject body = new JsonObject(); body.addProperty("p_imovel_id",imovel);
        JsonElement resposta = requisitar("POST","rpc/listar_horarios_livres",body,null);
        List<DTO.Horario> resultado = new ArrayList<>();
        for (JsonElement e : resposta.getAsJsonArray()) {
            JsonObject o=e.getAsJsonObject();
            resultado.add(new DTO.Horario(numero(o,"id"),numero(o,"corretor_id"),texto(o,"corretor"),dataHora(texto(o,"inicio"))));
        }
        return resultado;
    }

    public List<List<Object>> visitas(Long cliente, Long corretor) throws SQLException {
        String filtro = cliente == null ? "" : "&cliente_id=eq."+cliente;
        filtro += corretor == null ? "" : "&corretor_id=eq."+corretor;
        JsonArray dados = get("vw_visitas?select=id,cliente,imovel,corretor,inicio,status,preco_registrado"
            + filtro + "&order=inicio.desc,id.desc");
        return linhasConvertidas(dados,"id","cliente","imovel","corretor","inicio","status","preco_registrado");
    }

    public List<List<Object>> ranking() throws SQLException {
        return linhasConvertidas(get("vw_ranking?select=titulo,total&order=total.desc,titulo"),"titulo","total");
    }

    public List<List<Object>> baixaProcura() throws SQLException {
        return linhasConvertidas(get("vw_baixa_procura?select=titulo,bairro,ultima_visita&order=titulo"),"titulo","bairro","ultima_visita");
    }

    public List<List<Object>> produtividade() throws SQLException {
        return linhasConvertidas(get("vw_produtividade?select=nome,realizadas,pendentes,canceladas&order=realizadas.desc,nome"),
            "nome","realizadas","pendentes","canceladas");
    }

    public Object ticketMedio() throws SQLException {
        JsonArray dados = get("visita?select=preco_registrado&status=eq.REALIZADA");
        if (dados.isEmpty()) return null;
        BigDecimal total=BigDecimal.ZERO;
        for (JsonElement e:dados) total=total.add(decimal(e.getAsJsonObject(),"preco_registrado"));
        return total.divide(BigDecimal.valueOf(dados.size()),2,java.math.RoundingMode.HALF_UP);
    }

    public List<List<Object>> bairros() throws SQLException {
        return linhasConvertidas(get("vw_bairros?select=bairro,total&order=total.desc,bairro"),"bairro","total");
    }
    public List<List<Object>> picosPorDia() throws SQLException {
        return linhasConvertidas(get("vw_picos_dia?select=dia,total&order=dia"),"dia","total");
    }
    public List<List<Object>> picosPorHora() throws SQLException {
        return linhasConvertidas(get("vw_picos_hora?select=hora,total&order=hora"),"hora","total");
    }
    public List<List<Object>> statusDasVisitas() throws SQLException {
        return linhasConvertidas(get("vw_status_visitas?select=status,total&order=status"),"status","total");
    }

    private JsonObject imovelJson(DTO.Imovel i) {
        JsonObject o = new JsonObject();
        o.addProperty("proprietario_id",i.proprietarioId()); o.addProperty("titulo",i.titulo());
        o.addProperty("descricao",i.descricao()); o.addProperty("tipo",i.tipo());
        o.addProperty("bairro",i.bairro()); o.addProperty("cidade",i.cidade());
        o.addProperty("endereco",i.endereco()); o.addProperty("quartos",i.quartos());
        o.addProperty("banheiros",i.banheiros()); o.addProperty("vagas",i.vagas());
        o.addProperty("area_m2",i.areaM2()); o.addProperty("preco",i.preco());
        o.addProperty("destaque",i.destaque()); o.addProperty("status",i.status().name());
        return o;
    }

    private DTO.Imovel imovel(JsonObject o) {
        return new DTO.Imovel(numero(o,"id"),numero(o,"proprietario_id"),texto(o,"titulo"),texto(o,"descricao"),
            texto(o,"tipo"),texto(o,"bairro"),texto(o,"cidade"),texto(o,"endereco"),inteiro(o,"quartos"),
            inteiro(o,"banheiros"),inteiro(o,"vagas"),decimal(o,"area_m2"),decimal(o,"preco"),
            o.get("destaque").getAsBoolean(),DTO.StatusImovel.valueOf(texto(o,"status")));
    }

    private JsonArray get(String caminho) throws SQLException {
        return requisitar("GET",caminho,null,null).getAsJsonArray();
    }

    private JsonElement requisitar(String metodo, String caminho, JsonElement body, String prefer) throws SQLException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(restUrl+caminho)).timeout(Duration.ofSeconds(15))
            .header("apikey",key).header("Accept","application/json");
        String token=tokenAtual.get();
        if (token!=null) req.header("Authorization","Bearer "+token);
        if (prefer!=null) req.header("Prefer",prefer);
        if (body!=null) req.header("Content-Type","application/json");
        req.method(metodo,body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(gson.toJson(body)));
        HttpResponse<String> res;
        try { res=http.send(req.build(),HttpResponse.BodyHandlers.ofString()); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SQLException("Consulta interrompida",e); }
        catch (IOException e) { throw new SQLException("Supabase indisponível",e); }
        if (res.statusCode()/100!=2) {
            String state=res.statusCode()==409 ? "23505" : res.statusCode()==403 ? "42501" : "58000";
            throw new SQLException("Supabase retornou HTTP "+res.statusCode(),state,res.statusCode());
        }
        if (res.body()==null || res.body().isBlank()) return new JsonArray();
        return JsonParser.parseString(res.body());
    }

    private static String codificar(String valor) { return URLEncoder.encode(valor,StandardCharsets.UTF_8); }
    private static long numero(JsonObject o,String k) { return o.get(k).getAsLong(); }
    private static int inteiro(JsonObject o,String k) { return o.get(k).getAsInt(); }
    private static String texto(JsonObject o,String k) { return o.get(k).getAsString(); }
    private static String textoNulo(JsonObject o,String k) { return !o.has(k)||o.get(k).isJsonNull()?null:o.get(k).getAsString(); }
    private static BigDecimal decimal(JsonObject o,String k) { return o.get(k).getAsBigDecimal(); }
    private static LocalDateTime dataHora(String valor) {
        return OffsetDateTime.parse(valor).atZoneSameInstant(FUSO).toLocalDateTime();
    }

    private static List<List<Object>> linhas(JsonArray dados,String...campos) {
        List<List<Object>> resultado=new ArrayList<>();
        for(JsonElement e:dados){
            JsonObject o=e.getAsJsonObject(); List<Object> linha=new ArrayList<>();
            for(String campo:campos) linha.add(o.get(campo).isJsonNull()?null:o.get(campo).isJsonPrimitive()&&o.get(campo).getAsJsonPrimitive().isNumber()?o.get(campo).getAsLong():o.get(campo).getAsString());
            resultado.add(linha);
        }
        return resultado;
    }

    private static List<List<Object>> linhasConvertidas(JsonArray dados,String...campos) {
        List<List<Object>> resultado=new ArrayList<>();
        for(JsonElement e:dados){
            JsonObject o=e.getAsJsonObject(); List<Object> linha=new ArrayList<>();
            for(String campo:campos){
                JsonElement v=o.get(campo);
                if(v==null||v.isJsonNull()) linha.add(null);
                else if(campo.equals("preco_registrado")) linha.add(v.getAsBigDecimal());
                else if(campo.equals("inicio")||campo.equals("ultima_visita")) linha.add(dataHora(v.getAsString()));
                else if(v.getAsJsonPrimitive().isNumber()) linha.add(v.getAsLong());
                else linha.add(v.getAsString());
            }
            resultado.add(linha);
        }
        return resultado;
    }
}
