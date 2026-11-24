package dev.cadencia.feedback;

import dev.cadencia.dominio.ConfiabilidadeFaixa;
import dev.cadencia.dominio.FaixaHorario;
import dev.cadencia.dominio.FeedbackSemanal;
import dev.cadencia.dominio.ItemFeedback;
import dev.cadencia.dominio.Semana;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Serviço de aplicação responsável por calcular estatisticamente a confiabilidade histórica por
 * {@link FaixaHorario} (dia da semana e período do dia) sobre uma janela configurável das últimas
 * semanas anteriores à semana alvo, exigindo quantidade mínima de amostras antes de considerar uma
 * faixa como de baixa confiabilidade.
 */
public class ConfiabilidadeService {

    public static final int JANELA_SEMANAS_PADRAO = 4;
    public static final int MINIMO_AMOSTRAS_PADRAO = 3;
    public static final double LIMITE_CONFIABILIDADE_PADRAO = 0.50;

    private static final String SUBDIRETORIO_FEEDBACK = "feedback";
    private static final Comparator<FaixaHorario> ORDEM_FAIXAS = Comparator
            .comparingInt((FaixaHorario faixa) -> faixa.diaSemana().getValue())
            .thenComparingInt(faixa -> faixa.periodo().ordinal());

    private final FeedbackRepository feedbackRepository;
    private final Path diretorioDados;
    private final int janelaSemanas;
    private final int minimoAmostras;
    private final double limiteConfiabilidade;

    public ConfiabilidadeService(FeedbackRepository feedbackRepository, Path diretorioDados) {
        this(
                feedbackRepository,
                diretorioDados,
                JANELA_SEMANAS_PADRAO,
                MINIMO_AMOSTRAS_PADRAO,
                LIMITE_CONFIABILIDADE_PADRAO);
    }

    public ConfiabilidadeService(
            FeedbackRepository feedbackRepository,
            Path diretorioDados,
            int janelaSemanas,
            int minimoAmostras,
            double limiteConfiabilidade) {
        this.feedbackRepository = Objects.requireNonNull(
                feedbackRepository, "feedbackRepository não pode ser nulo");
        this.diretorioDados = validarENormalizarDiretorio(diretorioDados);
        if (janelaSemanas <= 0) {
            throw new IllegalArgumentException("janelaSemanas deve ser estritamente positivo");
        }
        ConfiabilidadeFaixa.validarMinimoAmostras(minimoAmostras);
        ConfiabilidadeFaixa.validarLimiteConfiabilidade(limiteConfiabilidade);
        this.janelaSemanas = janelaSemanas;
        this.minimoAmostras = minimoAmostras;
        this.limiteConfiabilidade = limiteConfiabilidade;
    }

    public int janelaSemanas() {
        return janelaSemanas;
    }

    public int minimoAmostras() {
        return minimoAmostras;
    }

    public double limiteConfiabilidade() {
        return limiteConfiabilidade;
    }

    /**
     * Carrega o histórico gravado em {@code <diretorioDados>/feedback} e calcula a confiabilidade
     * estatística por {@link FaixaHorario} para a {@code semanaAlvo}.
     */
    public Map<FaixaHorario, ConfiabilidadeFaixa> calcularPorFaixa(Semana semanaAlvo) {
        Objects.requireNonNull(semanaAlvo, "semanaAlvo não pode ser nula");
        List<FeedbackSemanal> historico = feedbackRepository.listarHistorico(
                diretorioDados.resolve(SUBDIRETORIO_FEEDBACK));
        return calcularPorFaixa(semanaAlvo, historico);
    }

    /**
     * Calcula deterministicamente a confiabilidade por {@link FaixaHorario} sem mutar
     * {@code historico}, considerando exclusivamente semanas estritamente anteriores à
     * {@code semanaAlvo}, ordenando por {@code (ano, numeroIso)} e recortando a janela das últimas
     * {@link #janelaSemanas()} semanas disponíveis.
     */
    public Map<FaixaHorario, ConfiabilidadeFaixa> calcularPorFaixa(
            Semana semanaAlvo, List<FeedbackSemanal> historico) {
        Objects.requireNonNull(semanaAlvo, "semanaAlvo não pode ser nula");
        Objects.requireNonNull(historico, "historico não pode ser nulo");

        List<FeedbackSemanal> copiaHistorico = List.copyOf(historico);
        List<FeedbackSemanal> semanasAnterioresOrdenadas = copiaHistorico.stream()
                .filter(feedback -> feedback.ehAnteriorA(semanaAlvo))
                .sorted(FeedbackSemanal.ORDEM_CRONOLOGICA)
                .toList();

        if (semanasAnterioresOrdenadas.isEmpty()) {
            return Map.of();
        }

        int inicioJanela = Math.max(0, semanasAnterioresOrdenadas.size() - janelaSemanas);
        List<FeedbackSemanal> semanasJanela = semanasAnterioresOrdenadas.subList(
                inicioJanela, semanasAnterioresOrdenadas.size());

        Map<FaixaHorario, AcumuladorFaixa> acumuladores = new HashMap<>();
        for (FeedbackSemanal semana : semanasJanela) {
            for (ItemFeedback item : semana.itens()) {
                acumuladores
                        .computeIfAbsent(item.faixaHorario(), ignored -> new AcumuladorFaixa())
                        .registrar(item.status().peso());
            }
        }

        Map<FaixaHorario, ConfiabilidadeFaixa> resultado = new LinkedHashMap<>();
        acumuladores.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(ORDEM_FAIXAS))
                .forEach(entry -> resultado.put(
                        entry.getKey(),
                        entry.getValue().consolidar(
                                entry.getKey(), minimoAmostras, limiteConfiabilidade)));

        return Collections.unmodifiableMap(resultado);
    }

    /**
     * Delegador de compatibilidade para {@link #calcularPorFaixa(Semana)}.
     */
    public Map<FaixaHorario, ConfiabilidadeFaixa> calcularConfiabilidades(Semana semanaAlvo) {
        return calcularPorFaixa(semanaAlvo);
    }

    /**
     * Delegador de compatibilidade para {@link #calcularPorFaixa(Semana, List)}.
     */
    public Map<FaixaHorario, ConfiabilidadeFaixa> calcularConfiabilidades(
            Semana semanaAlvo, List<FeedbackSemanal> historico) {
        return calcularPorFaixa(semanaAlvo, historico);
    }

    /**
     * Indica se a faixa analisada já atingiu o mínimo de amostras e ficou abaixo do limite
     * configurado.
     */
    public boolean ehBaixaConfiabilidade(ConfiabilidadeFaixa confiabilidade) {
        return confiabilidade != null && confiabilidade.baixaConfiabilidade();
    }

    private static Path validarENormalizarDiretorio(Path diretorioDados) {
        Objects.requireNonNull(diretorioDados, "diretorioDados não pode ser nulo");
        if (diretorioDados.toString().isBlank()) {
            throw new IllegalArgumentException("diretorioDados não pode ser vazio");
        }
        return diretorioDados.normalize();
    }

    private static final class AcumuladorFaixa {

        private int amostras;
        private double somaPesos;

        private void registrar(double peso) {
            this.amostras++;
            this.somaPesos += peso;
        }

        private ConfiabilidadeFaixa consolidar(
                FaixaHorario faixa, int minimoAmostras, double limiteConfiabilidade) {
            double media = Math.min(1.0, Math.max(0.0, somaPesos / amostras));
            boolean amostrasSuficientes = amostras >= minimoAmostras;
            boolean baixaConfiabilidade = amostrasSuficientes && media < limiteConfiabilidade;
            return new ConfiabilidadeFaixa(
                    faixa, amostras, media, amostrasSuficientes, baixaConfiabilidade);
        }
    }
}
