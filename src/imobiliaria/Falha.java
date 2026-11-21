package imobiliaria;

public final class Falha extends Exception {
    public final int status;

    public Falha(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }
}
