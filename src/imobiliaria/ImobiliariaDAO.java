package imobiliaria;

import org.h2.tools.RunScript;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.time.LocalDateTime;

public final class ImobiliariaDAO {
    private final String url;

    public ImobiliariaDAO(String url) { this.url = url; }

    private Connection abrir() throws SQLException {
        var c = DriverManager.getConnection(url, "sa", "");
        try (var s = c.createStatement()) { s.execute("SET TIME ZONE 'America/Sao_Paulo'"); }
        return c;
    }

    public void iniciar() throws Exception {
        try (var c = abrir(); var schema = Files.newBufferedReader(Path.of("db/schema.sql"))) {
            RunScript.execute(c, schema);
            c.setAutoCommit(false);
            if (ler(c, "SELECT versao FROM carga WHERE versao=1").isEmpty()) {
                try (var seed = Files.newBufferedReader(Path.of("db/seed.sql"))) {
                    RunScript.execute(c, seed);
                    alterar(c, "INSERT INTO carga VALUES (1)");
                }
            }
            c.commit();
        }
    }

    private PreparedStatement preparar(Connection c, String sql, Object... args) throws SQLException {
        var s = c.prepareStatement(sql);
        for (int i=0; i<args.length; i++) s.setObject(i+1,args[i]);
        return s;
    }

    private List<List<Object>> ler(Connection c, String sql, Object... args) throws SQLException {
        try (var s = preparar(c,sql,args); var r = s.executeQuery()) {
            List<List<Object>> linhas = new ArrayList<>();
            while (r.next()) {
                List<Object> linha = new ArrayList<>();
                for (int i=1; i<=r.getMetaData().getColumnCount(); i++) linha.add(r.getObject(i));
                linhas.add(linha);
            }
            return linhas;
        }
    }

    private int alterar(Connection c, String sql, Object... args) throws SQLException {
        try (var s = preparar(c,sql,args)) { return s.executeUpdate(); }
    }

    private List<List<Object>> ler(String sql, Object... args) throws SQLException {
        try (var c = abrir()) { return ler(c,sql,args); }
    }

    private int alterar(String sql, Object... args) throws SQLException {
        try (var c = abrir()) { return alterar(c,sql,args); }
    }

    public long clienteDaConta(DTO.Identidade conta) throws SQLException, Falha {
        try (var c = abrir()) {
            c.setAutoCommit(false);
            var porId = ler(c,"SELECT id FROM cliente WHERE auth_id=?",conta.id());
            if (!porId.isEmpty()) return (long)porId.get(0).get(0);
            var existente = ler(c,"SELECT id,auth_id FROM cliente WHERE email=? FOR UPDATE",conta.email());
            if (existente.isEmpty()) {
                alterar(c,"INSERT INTO cliente(nome,email,auth_id) VALUES (?,?,?)",conta.email().split("@")[0],conta.email(),conta.id());
            } else {
                if (existente.get(0).get(1) != null) throw new Falha(403,"E-mail já vinculado a outra conta.");
                alterar(c,"UPDATE cliente SET auth_id=? WHERE id=?",conta.id(),existente.get(0).get(0));
            }
            long id = (long)ler(c,"SELECT id FROM cliente WHERE auth_id=?",conta.id()).get(0).get(0);
            c.commit();
            return id;
        }
    }

    public void salvar(DTO.Cliente c) throws SQLException {
        alterar("INSERT INTO cliente(nome,email) VALUES (?,?)",c.nome(),c.email());
    }

    public void salvar(DTO.Corretor c) throws SQLException {
        alterar("INSERT INTO corretor(nome,email,creci) VALUES (?,?,?)",c.nome(),c.email(),c.creci());
    }

    public void salvar(DTO.Proprietario p) throws SQLException {
        alterar("INSERT INTO proprietario(nome,email) VALUES (?,?)",p.nome(),p.email());
    }

    public void salvar(DTO.Imovel i) throws SQLException {
        alterar("INSERT INTO imovel(proprietario_id,titulo,bairro,endereco,preco,status) VALUES (?,?,?,?,?,?)",
            i.proprietarioId(),i.titulo(),i.bairro(),i.endereco(),i.preco(),i.status().name());
    }

    public void salvar(DTO.Horario h) throws SQLException {
        alterar("INSERT INTO horario(corretor_id,inicio) VALUES (?,?)",h.corretorId(),h.inicio());
    }

    public void mudarImovel(long id, DTO.StatusImovel status) throws SQLException, Falha {
        try (var c = abrir()) {
            c.setAutoCommit(false);
            if (ler(c,"SELECT id FROM imovel WHERE id=? FOR UPDATE",id).isEmpty())
                throw new Falha(404,"Imóvel não encontrado.");
            if (status != DTO.StatusImovel.DISPONIVEL &&
                !ler(c,"SELECT id FROM visita WHERE imovel_id=? AND status='PENDENTE'",id).isEmpty())
                throw new Falha(409,"Resolva as visitas pendentes antes de mudar o status deste imóvel.");
            alterar(c,"UPDATE imovel SET status=? WHERE id=?",status.name(),id);
            c.commit();
        }
    }

    public void reservar(long cliente, long imovel, long horario, LocalDateTime agora) throws SQLException, Falha {
        try (var c = abrir()) {
            c.setAutoCommit(false);
            var casas = ler(c,"SELECT preco,status FROM imovel WHERE id=? FOR UPDATE",imovel);
            if (casas.isEmpty() || !"DISPONIVEL".equals(casas.get(0).get(1)))
                throw new Falha(409,"O imóvel está indisponível.");
            int inseridos = alterar(c,"""
                INSERT INTO visita(cliente_id,imovel_id,horario_id,inicio,preco_registrado)
                SELECT ?,?,h.id,h.inicio,? FROM horario h WHERE h.id=? AND h.inicio>?
                """,cliente,imovel,casas.get(0).get(0),horario,agora);
            if (inseridos == 0) throw new Falha(409,"O horário já passou ou está indisponível.");
            c.commit();
        }
    }

    public void mudarVisita(long id, Long cliente, DTO.StatusVisita status, LocalDateTime agora) throws SQLException, Falha {
        int n = alterar("""
            UPDATE visita SET status=?, ativo=?
            WHERE id=? AND status='PENDENTE'
                AND (CAST(? AS BIGINT) IS NULL OR cliente_id=?)
                AND (?='CANCELADA' OR inicio<=?)
            """, status.name(), status==DTO.StatusVisita.CANCELADA ? null : 1, id, cliente, cliente, status.name(), agora);
        if (n == 0) throw new Falha(409,"A visita não permite essa alteração.");
    }

    public List<List<Object>> clientes() throws SQLException {
        return ler("SELECT id,nome,email FROM cliente ORDER BY nome");
    }

    public List<List<Object>> corretores() throws SQLException {
        return ler("SELECT id,nome,email,creci FROM corretor ORDER BY nome");
    }

    public List<List<Object>> proprietarios() throws SQLException {
        return ler("SELECT id,nome,email FROM proprietario ORDER BY nome");
    }

    public List<DTO.Imovel> imoveis(boolean disponiveis) throws SQLException {
        var linhas = ler("""
            SELECT id,proprietario_id,titulo,bairro,endereco,preco,status FROM imovel
            WHERE (?=FALSE OR status='DISPONIVEL') ORDER BY titulo
            """,disponiveis);
        return linhas.stream().map(r -> new DTO.Imovel((long)r.get(0),(long)r.get(1),(String)r.get(2),
            (String)r.get(3),(String)r.get(4),(java.math.BigDecimal)r.get(5),DTO.StatusImovel.valueOf((String)r.get(6)))).toList();
    }

    public List<DTO.Horario> horarios(long imovel, Long cliente, LocalDateTime agora) throws SQLException {
        var linhas = ler("""
            SELECT h.id,h.corretor_id,c.nome,h.inicio FROM horario h JOIN corretor c ON c.id=h.corretor_id
            WHERE h.inicio>? AND h.inicio<=?
              AND NOT EXISTS (SELECT 1 FROM visita v WHERE v.ativo=1 AND
                (v.horario_id=h.id OR (v.inicio=h.inicio AND
                 (v.imovel_id=? OR v.cliente_id=CAST(? AS BIGINT)))))
            ORDER BY h.inicio,c.nome
            """,agora,agora.plusDays(21),imovel,cliente);
        return linhas.stream().map(r -> new DTO.Horario((long)r.get(0),(long)r.get(1),
            (String)r.get(2),((Timestamp)r.get(3)).toLocalDateTime())).toList();
    }

    public List<List<Object>> visitas(Long cliente, Long corretor) throws SQLException {
        return ler("""
            SELECT v.id,cl.nome,i.titulo,c.nome,h.inicio,v.status,v.preco_registrado
            FROM visita v JOIN horario h ON h.id=v.horario_id
            JOIN corretor c ON c.id=h.corretor_id JOIN cliente cl ON cl.id=v.cliente_id
            JOIN imovel i ON i.id=v.imovel_id
            WHERE (CAST(? AS BIGINT) IS NULL OR v.cliente_id=?)
              AND (CAST(? AS BIGINT) IS NULL OR h.corretor_id=?)
            ORDER BY h.inicio DESC,v.id DESC
            """,cliente,cliente,corretor,corretor);
    }

    public List<List<Object>> ranking() throws SQLException {
        return ler("""
            SELECT i.titulo,COUNT(*) total FROM visita v JOIN imovel i ON i.id=v.imovel_id
            WHERE v.status='REALIZADA' GROUP BY i.id,i.titulo ORDER BY total DESC,i.titulo
            """);
    }

    public List<List<Object>> baixaProcura() throws SQLException {
        return ler("""
            SELECT i.titulo,i.bairro,MAX(v.inicio) FROM imovel i LEFT JOIN visita v
            ON v.imovel_id=i.id AND v.status='REALIZADA'
            WHERE i.status='DISPONIVEL' GROUP BY i.id,i.titulo,i.bairro,i.criado_em
            HAVING COALESCE(CAST(MAX(v.inicio) AS DATE),i.criado_em)<CURRENT_DATE-30 ORDER BY i.titulo
            """);
    }

    public List<List<Object>> produtividade() throws SQLException {
        return ler("""
            SELECT c.nome,COUNT(CASE WHEN v.status='REALIZADA' THEN 1 END) feitas,
                COUNT(CASE WHEN v.status='PENDENTE' THEN 1 END),
                COUNT(CASE WHEN v.status='CANCELADA' THEN 1 END)
            FROM corretor c LEFT JOIN horario h ON h.corretor_id=c.id
            LEFT JOIN visita v ON v.horario_id=h.id GROUP BY c.id,c.nome ORDER BY feitas DESC,c.nome
            """);
    }

    public Object ticketMedio() throws SQLException {
        return ler("SELECT AVG(preco_registrado) FROM visita WHERE status='REALIZADA'").get(0).get(0);
    }

    public List<List<Object>> bairros() throws SQLException {
        return ler("""
            SELECT i.bairro,COUNT(*) total FROM visita v JOIN imovel i ON i.id=v.imovel_id
            WHERE v.status='REALIZADA' GROUP BY i.bairro ORDER BY total DESC,i.bairro
            """);
    }

    public List<List<Object>> picosPorDia() throws SQLException {
        return ler("""
            SELECT ISO_DAY_OF_WEEK(inicio) dia,COUNT(*) FROM visita
            WHERE status<>'CANCELADA' GROUP BY ISO_DAY_OF_WEEK(inicio) ORDER BY dia
            """);
    }

    public List<List<Object>> picosPorHora() throws SQLException {
        return ler("""
            SELECT EXTRACT(HOUR FROM inicio) hora,COUNT(*) FROM visita
            WHERE status<>'CANCELADA' GROUP BY EXTRACT(HOUR FROM inicio) ORDER BY hora
            """);
    }

    public List<List<Object>> statusDasVisitas() throws SQLException {
        return ler("SELECT status,COUNT(*) FROM visita GROUP BY status ORDER BY status");
    }
}
