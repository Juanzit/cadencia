package dev.cadencia.exportacao.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.cadencia.agenda.AgendaRepository;
import dev.cadencia.agenda.AgendaService;
import dev.cadencia.agenda.IntervaloOcupado;
import dev.cadencia.agenda.SemanaPlanejada;
import dev.cadencia.agenda.TipoIntervaloOcupado;
import dev.cadencia.agenda.YamlAgendaRepository;
import dev.cadencia.dominio.AgendaConfiguracao;
import dev.cadencia.dominio.Bloco;
import dev.cadencia.dominio.Classificacao;
import dev.cadencia.dominio.Compromisso;
import dev.cadencia.dominio.Jornada;
import dev.cadencia.dominio.MargemEmpresa;
import dev.cadencia.dominio.Margens;
import dev.cadencia.dominio.Semana;
import dev.cadencia.dominio.TipoBloco;
import dev.cadencia.dominio.TipoCompromisso;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownSemanalExportadorTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Clock RELOGIO_SEGUNDA_W48 = Clock.fixed(
            LocalDate.of(2026, 11, 23).atTime(9, 0).atZone(SAO_PAULO).toInstant(),
            SAO_PAULO);

    private final ExportadorStrategy exportador = new MarkdownSemanalExportador();

    @TempDir
    Path tempDir;

    @Test
    void deveExportarAgendaExemploNoFormatoDesignComIdempotenciaEQuebraLf() throws IOException {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            AgendaRepository yamlRepo = new YamlAgendaRepository(
                    new ObjectMapper(new YAMLFactory()),
                    factory.getValidator());
            AgendaService service = new AgendaService(yamlRepo, RELOGIO_SEGUNDA_W48);

            SemanaPlanejada semanaPlanejada = service.gerarSemana(Path.of("exemplos/agenda.exemplo.yaml"));

            String primeiraExportacao = exportador.exportar(semanaPlanejada);
            String segundaExportacao = exportador.exportar(semanaPlanejada);

            assertThat(primeiraExportacao).isEqualTo(segundaExportacao);
            assertThat(primeiraExportacao).doesNotContain("\r");
            assertThat(primeiraExportacao).doesNotContain("| Plantão |");
            assertThat(primeiraExportacao).endsWith("\n").doesNotEndWith("\n\n");

            Path arquivoSaida = tempDir.resolve("2026-W48.md");
            Files.writeString(arquivoSaida, primeiraExportacao, StandardCharsets.UTF_8);
            byte[] bytesGravados = Files.readAllBytes(arquivoSaida);
            assertThat(new String(bytesGravados, StandardCharsets.UTF_8)).isEqualTo(primeiraExportacao);

            assertThat(primeiraExportacao).isEqualTo("""
                    # Semana 2026-W48 (23/11 a 29/11)

                    ## Resumo
                    - Blocos livres: 24 (profundo: 18 · interrompível: 5 · vigilância: 1)
                    - Tempo livre total: 85h15
                    - Turmas em transição: nenhuma

                    ## Segunda-feira, 23/11
                    | Horário | Tipo | Detalhe |
                    | :--- | :--- | :--- |
                    | 07:00–08:30 | **Bloco profundo** | Disponível (1h30) |
                    | 08:30–09:00 | Margem | Margem antes (EmpresaA) |
                    | 09:00–11:00 | Aula | EmpresaA · UNIDADE_CENTRO · Turma T01 |
                    | 11:00–11:30 | Deslocamento | TRANSITO · Retorno para casa |
                    | 11:30–12:00 | Bloco interrompível | Disponível (0h30) |
                    | 12:00–13:00 | Almoço | CASA · Almoço |
                    | 13:00–15:30 | **Bloco profundo** | Disponível (2h30) |
                    | 15:30–16:00 | Margem | Margem antes (EmpresaB) |
                    | 16:00–17:30 | Aula | EmpresaB · CASA · Turma R02 |
                    | 17:30–17:45 | Margem | Margem depois (EmpresaB) |
                    | 18:00–18:30 | Deslocamento | Deslocamento automático (CASA -> ACADEMIA) |
                    | 18:30–19:30 | Treino | ACADEMIA · Treino de força |
                    | 19:30–23:00 | **Bloco profundo** | Disponível (3h30) |

                    ## Terça-feira, 24/11
                    | Horário | Tipo | Detalhe |
                    | :--- | :--- | :--- |
                    | 07:00–12:30 | **Bloco profundo** | Disponível (5h30) |
                    | 12:30–13:30 | Almoço | CASA · Almoço |
                    | 13:30–18:30 | **Bloco profundo** | Disponível (5h00) |
                    | 18:30–19:00 | Margem | Margem antes (EmpresaB) |
                    | 19:00–20:30 | Aula | EmpresaB · CASA · Turma R04 |
                    | 20:30–20:45 | Margem | Margem depois (EmpresaB) |
                    | 20:45–23:00 | **Bloco profundo** | Disponível (2h15) |

                    ## Quarta-feira, 25/11
                    | Horário | Tipo | Detalhe |
                    | :--- | :--- | :--- |
                    | 07:00–08:00 | Bloco interrompível | Disponível (1h00) |
                    | 08:00–09:15 | Treino | ACADEMIA · Treino matinal |
                    | 09:15–10:30 | Bloco interrompível | Disponível (1h15) |
                    | 10:30–11:00 | Deslocamento | Deslocamento automático (ACADEMIA -> REMOTO) |
                    | 11:00–12:00 | Reunião | EmpresaA · REMOTO · Reunião pedagógica semanal |
                    | 12:00–12:30 | Deslocamento | Deslocamento automático (REMOTO -> CASA) |
                    | 12:30–13:30 | Almoço | CASA · Almoço |
                    | 13:30–15:00 | **Bloco profundo** | Disponível (1h30) |
                    | 15:00–17:00 | Bloco de vigilância | Janela de plantão compatível apenas com tarefas leves |
                    | 17:00–23:00 | **Bloco profundo** | Disponível (6h00) |

                    ## Quinta-feira, 26/11
                    | Horário | Tipo | Detalhe |
                    | :--- | :--- | :--- |
                    | 07:00–12:00 | **Bloco profundo** | Disponível (5h00) |
                    | 12:00–13:00 | Almoço | CASA · Almoço |
                    | 13:00–13:30 | Bloco interrompível | Disponível (0h30) |
                    | 13:30–14:00 | Deslocamento | Deslocamento automático (CASA -> UNIDADE_CENTRO) |
                    | 13:30–14:00 | Margem | Margem antes (EmpresaA) |
                    | 14:00–16:00 | Aula | EmpresaA · UNIDADE_CENTRO · Turma T03 |
                    | 16:00–16:30 | Deslocamento | TRANSITO · Retorno para casa |
                    | 16:30–19:00 | **Bloco profundo** | Disponível (2h30) |
                    | 19:00–20:00 | Treino | ACADEMIA · Treino de força |
                    | 20:00–23:00 | **Bloco profundo** | Disponível (3h00) |

                    ## Sexta-feira, 27/11
                    | Horário | Tipo | Detalhe |
                    | :--- | :--- | :--- |
                    | 07:00–10:00 | **Bloco profundo** | Disponível (3h00) |
                    | 10:00–11:30 | **Bloco profundo** | Reserva manual para estudo prático |
                    | 11:30–12:00 | Bloco interrompível | Disponível (0h30) |
                    | 12:00–13:00 | Almoço | CASA · Almoço |
                    | 13:00–14:30 | **Bloco profundo** | Disponível (1h30) |
                    | 14:30–15:00 | Margem | Margem antes (EmpresaB) |
                    | 15:00–16:30 | Aula | EmpresaB · CASA · Turma R05 |
                    | 16:30–16:45 | Margem | Margem depois (EmpresaB) |
                    | 16:45–23:00 | **Bloco profundo** | Disponível (6h15) |

                    ## Sábado, 28/11
                    | Horário | Tipo | Detalhe |
                    | :--- | :--- | :--- |
                    | 07:00–08:30 | **Bloco profundo** | Disponível (1h30) |
                    | 08:30–09:00 | Margem | Margem antes (EmpresaA) |
                    | 09:00–11:30 | Aula | EmpresaA · UNIDADE_CENTRO · Turma T06 |
                    | 11:30–23:00 | **Bloco profundo** | Disponível (11h30) |

                    ## Domingo, 29/11
                    | Horário | Tipo | Detalhe |
                    | :--- | :--- | :--- |
                    | 07:00–23:00 | **Bloco profundo** | Disponível (16h00) |

                    ## Checklist de estudos
                    - [ ] seg 07:00–08:30 · Bloco profundo (1h30)
                    - [ ] seg 11:30–12:00 · Bloco interrompível (0h30)
                    - [ ] seg 13:00–15:30 · Bloco profundo (2h30)
                    - [ ] seg 19:30–23:00 · Bloco profundo (3h30)
                    - [ ] ter 07:00–12:30 · Bloco profundo (5h30)
                    - [ ] ter 13:30–18:30 · Bloco profundo (5h00)
                    - [ ] ter 20:45–23:00 · Bloco profundo (2h15)
                    - [ ] qua 07:00–08:00 · Bloco interrompível (1h00)
                    - [ ] qua 09:15–10:30 · Bloco interrompível (1h15)
                    - [ ] qua 13:30–15:00 · Bloco profundo (1h30)
                    - [ ] qua 15:00–17:00 · Janela de plantão compatível apenas com tarefas leves
                    - [ ] qua 17:00–23:00 · Bloco profundo (6h00)
                    - [ ] qui 07:00–12:00 · Bloco profundo (5h00)
                    - [ ] qui 13:00–13:30 · Bloco interrompível (0h30)
                    - [ ] qui 16:30–19:00 · Bloco profundo (2h30)
                    - [ ] qui 20:00–23:00 · Bloco profundo (3h00)
                    - [ ] sex 07:00–10:00 · Bloco profundo (3h00)
                    - [ ] sex 10:00–11:30 · Reserva manual para estudo prático
                    - [ ] sex 11:30–12:00 · Bloco interrompível (0h30)
                    - [ ] sex 13:00–14:30 · Bloco profundo (1h30)
                    - [ ] sex 16:45–23:00 · Bloco profundo (6h15)
                    - [ ] sáb 07:00–08:30 · Bloco profundo (1h30)
                    - [ ] sáb 11:30–23:00 · Bloco profundo (11h30)
                    - [ ] dom 07:00–23:00 · Bloco profundo (16h00)

                    ## Avisos
                    - Nenhum aviso.
                    """);
        }
    }

    @Test
    void deveFormatarEOrdenarCorretamenteBlocosEOcupacoesAteMeiaNoite() {
        List<Compromisso> compromissos = List.of(
                // Segunda 22:00–00:00 PLANTAO: excluído de intervalosOcupados e convertido em vigilância 22:00–23:30
                new Compromisso(DayOfWeek.MONDAY, LocalTime.of(22, 0), Duration.ofHours(2),
                        TipoCompromisso.PLANTAO, null, null, null),
                // Terça 22:30–00:00 TREINO sem detalhe: intervalo ocupado termina exatamente às 00:00
                new Compromisso(DayOfWeek.TUESDAY, LocalTime.of(22, 30), Duration.ofMinutes(90),
                        TipoCompromisso.TREINO, null, null, null),
                // Quarta 22:50–23:50 AULA EmpresaB: margem depois 23:50–00:00 e 00:00–00:05 na quinta
                new Compromisso(DayOfWeek.WEDNESDAY, LocalTime.of(22, 50), Duration.ofHours(1),
                        TipoCompromisso.AULA, "EmpresaB", null, "Aula noturna"),
                // Domingo 23:00–01:00 PLANTAO: vigilância 23:00–23:30 no domingo e aviso da segunda seguinte
                new Compromisso(DayOfWeek.SUNDAY, LocalTime.of(23, 0), Duration.ofHours(2),
                        TipoCompromisso.PLANTAO, null, null, "Plantão domingo"));

        AgendaConfiguracao config = new AgendaConfiguracao(
                new Jornada(LocalTime.of(7, 0), LocalTime.of(23, 30)),
                new Margens(0, Map.of("EmpresaB", new MargemEmpresa(30, 15))),
                new Classificacao(90, 25),
                compromissos,
                List.of());
        AgendaService service = new AgendaService(caminho -> config, RELOGIO_SEGUNDA_W48);
        SemanaPlanejada planejada = service.gerarSemana(Path.of("in-memory/meia-noite.yaml"));

        String markdown = exportador.exportar(planejada);

        assertThat(markdown)
                .doesNotContain("\r")
                .doesNotContain("| Plantão |");
        assertThat(markdown).contains("""
                ## Segunda-feira, 23/11
                | Horário | Tipo | Detalhe |
                | :--- | :--- | :--- |
                | 07:00–22:00 | **Bloco profundo** | Disponível (15h00) |
                | 22:00–23:30 | Bloco de vigilância | PLANTAO |
                """);
        assertThat(markdown).contains("""
                ## Terça-feira, 24/11
                | Horário | Tipo | Detalhe |
                | :--- | :--- | :--- |
                | 07:00–22:30 | **Bloco profundo** | Disponível (15h30) |
                | 22:30–00:00 | Treino | — |
                """);
        assertThat(markdown).contains(
                "| 23:50–00:00 | Margem | Margem depois (EmpresaB) |",
                "| 00:00–00:05 | Margem | Margem depois (EmpresaB) |",
                "| 23:00–23:30 | Bloco de vigilância | PLANTAO · Plantão domingo |",
                "- [ ] seg 22:00–23:30 · PLANTAO",
                "- [ ] dom 23:00–23:30 · PLANTAO · Plantão domingo",
                "- Compromisso \"PLANTAO · Plantão domingo\" de domingo ultrapassa a semana; a fração de segunda-feira (00:00–01:00) foi descartada.");
    }

    @Test
    void deveLidarComSemanaVaziaSanitizacaoDeCelulasViradaIsoEOrdenacaoDeBlocos() {
        assertThatNullPointerException().isThrownBy(() -> exportador.exportar(null));

        LocalDate segundaVirada = LocalDate.of(2025, 12, 29);
        Semana semanaVazia = new Semana(2026, 1, List.of(), List.of());
        SemanaPlanejada vazia = new SemanaPlanejada(segundaVirada, semanaVazia, List.of());

        String mdVazia = exportador.exportar(vazia);
        assertThat(mdVazia)
                .doesNotContain("\r")
                .contains("# Semana 2026-W01 (29/12 a 04/01)")
                .contains("- Blocos livres: 0 (profundo: 0 · interrompível: 0 · vigilância: 0)")
                .contains("- Tempo livre total: 0h00")
                .contains("## Segunda-feira, 29/12\n- Sem compromissos ou blocos livres registrados.")
                .contains("## Checklist de estudos\n- Nenhum bloco livre disponível na semana.")
                .contains("## Avisos\n- Nenhum aviso.");

        // Blocos fora de ordem e texto com pipe / quebras de linha são sanitizados e ordenados
        Bloco tarde = new Bloco(segundaVirada, LocalTime.of(14, 0), LocalTime.of(15, 0),
                TipoBloco.INTERROMPIVEL, null);
        Bloco manha = new Bloco(segundaVirada, LocalTime.of(8, 0), LocalTime.of(10, 0),
                TipoBloco.VIGILANCIA, "Nota | linha 1\nlinha 2");
        IntervaloOcupado custom = new IntervaloOcupado(
                segundaVirada, LocalTime.of(10, 0), Duration.ofHours(1),
                TipoIntervaloOcupado.COMPROMISSO, "OUTRO · evento | detalhe");
        IntervaloOcupado plantaoSintetico = new IntervaloOcupado(
                segundaVirada, LocalTime.of(12, 0), Duration.ofHours(1),
                TipoIntervaloOcupado.COMPROMISSO, "PLANTAO · Turno manual");

        SemanaPlanejada desordenada = new SemanaPlanejada(
                segundaVirada,
                new Semana(2026, 1, List.of(), List.of(tarde, manha)),
                List.of(custom, plantaoSintetico),
                List.of("Aviso linha 1\r\nlinha 2"));

        String mdSanitizada = exportador.exportar(desordenada);
        assertThat(mdSanitizada)
                .doesNotContain("\r")
                .contains("| 08:00–10:00 | Bloco de vigilância | Nota \\| linha 1 linha 2 |")
                .contains("| 10:00–11:00 | Compromisso | evento \\| detalhe |")
                .contains("| 12:00–13:00 | Plantão | Turno manual |")
                .contains("- [ ] seg 08:00–10:00 · Nota | linha 1 linha 2")
                .contains("- [ ] seg 14:00–15:00 · Bloco interrompível (1h00)")
                .contains("- Aviso linha 1 linha 2");
    }
}
