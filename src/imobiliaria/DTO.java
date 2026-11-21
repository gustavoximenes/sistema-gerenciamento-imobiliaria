package imobiliaria;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class DTO {
    public enum StatusImovel { DISPONIVEL, RESERVADO, VENDIDO }
    public enum StatusVisita { PENDENTE, REALIZADA, CANCELADA }
    public record Cliente(long id, String nome, String email, UUID authId) {}
    public record Corretor(long id, String nome, String email, String creci) {}
    public record Proprietario(long id, String nome, String email) {}
    public record Imovel(long id, long proprietarioId, String titulo, String bairro,
                        String endereco, BigDecimal preco, StatusImovel status) {}
    public record Horario(long id, long corretorId, String corretor, LocalDateTime inicio) {}
    public record Visita(long id, long clienteId, long imovelId, long horarioId,
                         LocalDateTime inicio, BigDecimal precoRegistrado, StatusVisita status) {}
    public record Relatorios(Object ticketMedio, List<List<Object>> ranking,
                            List<List<Object>> baixaProcura,
                            List<List<Object>> produtividade,
                            List<List<Object>> bairros,
                            List<List<Object>> statusDasVisitas,
                            List<List<Object>> picosPorDia,
                            List<List<Object>> picosPorHora) {}
    public record Identidade(UUID id, String email) {}
    private DTO() {}
}
