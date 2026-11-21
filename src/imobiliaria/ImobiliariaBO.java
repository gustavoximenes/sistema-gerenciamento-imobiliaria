package imobiliaria;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.util.Locale;
import java.util.Map;

public final class ImobiliariaBO {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private final ImobiliariaDAO dao;

    public ImobiliariaBO(ImobiliariaDAO dao) { this.dao = dao; }

    public LocalDateTime agora() { return LocalDateTime.now(FUSO); }

    public void cadastrar(String tipo, Map<String,String> f) throws SQLException, Falha {
        switch (tipo) {
            case "clientes" -> dao.salvar(new DTO.Cliente(0,campo(f,"nome",100),email(f),null));
            case "corretores" -> dao.salvar(new DTO.Corretor(0,campo(f,"nome",100),email(f),campo(f,"creci",30)));
            case "proprietarios" -> dao.salvar(new DTO.Proprietario(0,campo(f,"nome",100),email(f)));
            case "imoveis" -> {
                var preco = new BigDecimal(campo(f,"preco",15).replace(',','.'));
                if (preco.signum() <= 0 || preco.scale() > 2 || preco.precision()-preco.scale()>12)
                    throw new Falha(400,"Informe um preço positivo com até duas casas decimais.");
                dao.salvar(new DTO.Imovel(0,id(f,"proprietario"),campo(f,"titulo",100),campo(f,"bairro",70),
                    campo(f,"endereco",180),preco,DTO.StatusImovel.DISPONIVEL));
            }
            case "horarios" -> {
                var inicio = LocalDateTime.parse(campo(f,"inicio",25));
                if (!inicio.isAfter(agora()) || inicio.getMinute()!=0 || inicio.getSecond()!=0 || inicio.getNano()!=0)
                    throw new Falha(400,"Selecione uma hora cheia no futuro.");
                dao.salvar(new DTO.Horario(0,id(f,"corretor"),null,inicio));
            }
            default -> throw new Falha(404,"Cadastro desconhecido.");
        }
    }

    public void agendar(Map<String,String> f, Long cliente) throws SQLException, Falha {
        dao.reservar(cliente!=null ? cliente : id(f,"cliente"),id(f,"imovel"),id(f,"horario"),agora());
    }

    public void mudarVisita(Map<String,String> f, Long cliente) throws SQLException, Falha {
        var status = DTO.StatusVisita.valueOf(campo(f,"status",12));
        if (status==DTO.StatusVisita.PENDENTE || (cliente!=null && status!=DTO.StatusVisita.CANCELADA))
            throw new Falha(403,"Você não pode aplicar esse status.");
        dao.mudarVisita(id(f,"id"),cliente,status,agora());
    }

    public DTO.Relatorios relatorios() throws SQLException {
        return new DTO.Relatorios(dao.ticketMedio(),dao.ranking(),dao.baixaProcura(),dao.produtividade(),
            dao.bairros(),dao.statusDasVisitas(),dao.picosPorDia(),dao.picosPorHora());
    }

    public void mudarImovel(Map<String,String> f) throws SQLException, Falha {
        dao.mudarImovel(id(f,"id"),DTO.StatusImovel.valueOf(campo(f,"status",12)));
    }

    public static String email(Map<String,String> f) throws Falha {
        String email = campo(f,"email",254).toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new Falha(400,"Informe um e-mail válido.");
        return email;
    }

    public static long id(Map<String,String> f, String chave) throws Falha {
        long id = Long.parseLong(campo(f,chave,19));
        if (id<=0) throw new Falha(400,"Selecione um registro válido.");
        return id;
    }

    private static String campo(Map<String,String> f, String chave, int max) throws Falha {
        String v = f.getOrDefault(chave,"").strip();
        if (v.isEmpty() || v.length()>max) throw new Falha(400,"Revise o campo "+chave+".");
        return v;
    }
}
