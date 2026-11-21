package imobiliaria;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public final class SupabaseAuth {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Gson gson = new Gson();
    private final String url;
    private final String key;

    public SupabaseAuth(String url, String key) {
        URI base = URI.create(url);
        boolean local = "http".equals(base.getScheme()) && "127.0.0.1".equals(base.getHost());
        if (!"https".equals(base.getScheme()) && !local) throw new IllegalArgumentException("Use HTTPS para o Auth.");
        this.url = url.replaceFirst("/+$", "") + "/auth/v1";
        this.key = key;
    }

    public String entrar(String email, String senha) throws Falha {
        var res = chamar("/token?grant_type=password", gson.toJson(Map.of("email", email, "password", senha)), null);
        if (res.statusCode() != 200) throw new Falha(res.statusCode() >= 500 ? 503 : 401,
            res.statusCode() >= 500 ? "Serviço de login indisponível." : "Verifique o e-mail, a senha e a confirmação da conta.");
        return JsonParser.parseString(res.body()).getAsJsonObject().get("access_token").getAsString();
    }

    public void cadastrar(String nome, String email, String senha) throws Falha {
        var res = chamar("/signup", gson.toJson(Map.of(
            "email", email,
            "password", senha,
            "data", Map.of("nome", nome)
        )), null);
        if (res.statusCode() / 100 != 2) throw new Falha(res.statusCode() == 429 ? 429 : 400,
            "O Supabase não concluiu o cadastro. Confira o e-mail e tente novamente.");
    }

    public DTO.Identidade verificar(String token) throws Falha {
        var res = chamar("/user", null, token);
        if (res.statusCode() == 401 || res.statusCode() == 403) return null;
        if (res.statusCode() != 200) throw new Falha(503,"Serviço de login indisponível.");
        var user = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!user.has("email_confirmed_at") || user.get("email_confirmed_at").isJsonNull()) return null;
        return new DTO.Identidade(UUID.fromString(user.get("id").getAsString()), user.get("email").getAsString());
    }

    public void sair(String token) throws Falha {
        if (token != null) chamar("/logout?scope=local", "", token);
    }

    private HttpResponse<String> chamar(String caminho, String json, String token) throws Falha {
        var req = HttpRequest.newBuilder(URI.create(url+caminho)).timeout(Duration.ofSeconds(9)).header("apikey",key);
        if (token != null) req.header("Authorization","Bearer "+token);
        if (json != null) req.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json));
        try {
            return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Falha(503,"Serviço de login indisponível.");
        } catch (IOException e) {
            throw new Falha(503,"Serviço de login indisponível.");
        }
    }
}
