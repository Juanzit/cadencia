package dev.cadencia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.cadencia.agenda.AgendaRepository;
import dev.cadencia.agenda.AgendaService;
import dev.cadencia.agenda.YamlAgendaRepository;
import dev.cadencia.exportacao.MarkdownPlanoSemanalRepository;
import dev.cadencia.exportacao.PlanoSemanalRepository;
import dev.cadencia.exportacao.PlanoSemanalService;
import dev.cadencia.exportacao.strategies.ExportadorStrategy;
import dev.cadencia.exportacao.strategies.MarkdownSemanalExportador;
import dev.cadencia.feedback.FeedbackRepository;
import dev.cadencia.feedback.FeedbackService;
import dev.cadencia.feedback.MarkdownFeedbackRepository;
import dev.cadencia.planejamento.BacklogRepository;
import dev.cadencia.planejamento.PlanejamentoService;
import dev.cadencia.planejamento.YamlBacklogRepository;
import dev.cadencia.planejamento.strategies.AlocadorStrategy;
import dev.cadencia.planejamento.strategies.PrioridadeAlocadorStrategy;
import jakarta.validation.Validator;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração Spring que conecta portas, adapters, serviços de aplicação e propriedades do
 * sistema sem acoplar as camadas puras ao framework.
 */
@Configuration
public class CadenciaConfig {

    @Bean
    Clock clock(@Value("${cadencia.fuso:America/Sao_Paulo}") String fuso) {
        return Clock.system(ZoneId.of(fuso));
    }

    @Bean
    AgendaRepository agendaRepository(Validator validator) {
        return new YamlAgendaRepository(new ObjectMapper(new YAMLFactory()), validator);
    }

    @Bean
    AgendaService agendaService(AgendaRepository agendaRepository, Clock clock) {
        return new AgendaService(agendaRepository, clock);
    }

    @Bean
    BacklogRepository backlogRepository(
            Validator validator,
            @Value("${cadencia.pedaco-padrao-min:25}") long pedacoPadraoMin) {
        return new YamlBacklogRepository(
                new ObjectMapper(new YAMLFactory()),
                validator,
                Duration.ofMinutes(pedacoPadraoMin));
    }

    @Bean
    AlocadorStrategy alocadorStrategy() {
        return new PrioridadeAlocadorStrategy();
    }

    @Bean
    PlanejamentoService planejamentoService(
            AlocadorStrategy alocadorStrategy,
            @Value("${cadencia.folga-minima-min:10}") long folgaMinimaMin) {
        return new PlanejamentoService(alocadorStrategy, Duration.ofMinutes(folgaMinimaMin));
    }

    @Bean
    ExportadorStrategy exportadorStrategy() {
        return new MarkdownSemanalExportador();
    }

    @Bean
    PlanoSemanalRepository planoSemanalRepository() {
        return new MarkdownPlanoSemanalRepository();
    }

    @Bean
    PlanoSemanalService planoSemanalService(
            AgendaService agendaService,
            BacklogRepository backlogRepository,
            PlanejamentoService planejamentoService,
            ExportadorStrategy exportadorStrategy,
            PlanoSemanalRepository planoSemanalRepository,
            @Value("${cadencia.diretorio-dados:./dados}") Path diretorioDados) {
        return new PlanoSemanalService(
                agendaService,
                backlogRepository,
                planejamentoService,
                exportadorStrategy,
                planoSemanalRepository,
                diretorioDados);
    }

    @Bean
    FeedbackRepository feedbackRepository() {
        return new MarkdownFeedbackRepository();
    }

    @Bean
    FeedbackService feedbackService(
            AgendaService agendaService,
            BacklogRepository backlogRepository,
            PlanejamentoService planejamentoService,
            FeedbackRepository feedbackRepository,
            @Value("${cadencia.diretorio-dados:./dados}") Path diretorioDados) {
        return new FeedbackService(
                agendaService,
                backlogRepository,
                planejamentoService,
                feedbackRepository,
                diretorioDados);
    }
}
