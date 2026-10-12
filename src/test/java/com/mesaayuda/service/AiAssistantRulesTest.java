package com.mesaayuda.service;

import com.mesaayuda.entity.AiConfig;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.AiConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las reglas de la institucion son texto libre que el administrador escribe en
 * la configuracion del asistente y que se antepone a las instrucciones de cada
 * accion. Al ser solo texto, el riesgo no es que rompa nada, sino que se pierda
 * en silencio: por eso se fija que llegue al prompt y que no aparezca cuando el
 * administrador no escribio nada.
 */
class AiAssistantRulesTest {

    private AiAssistantService service;
    private AiConfigRepository repository;

    @BeforeEach
    void setUp() {
        service = new AiAssistantService();
        repository = Mockito.mock(AiConfigRepository.class);
        ReflectionTestUtils.setField(service, "aiConfigRepository", repository);
    }

    private AiConfig config(String reglas, String acciones) {
        AiConfig cfg = new AiConfig();
        cfg.setEnabled(true);
        cfg.setProvider("OPENAI");
        cfg.setModel("gpt-4o-mini");
        cfg.setRulesText(reglas);
        cfg.setEnabledActions(acciones);
        return cfg;
    }

    private String promptConReglas(String reglas) throws Exception {
        Mockito.when(repository.findFirstByOrderByIdAiConfigAsc())
                .thenReturn(Optional.of(config(reglas, "SUMMARY,ANSWER")));
        Method m = buscar("agregarReglas", StringBuilder.class);
        StringBuilder sb = new StringBuilder("BASE");
        m.invoke(service, sb);
        return sb.toString();
    }

    @Test
    @DisplayName("las reglas del administrador llegan al prompt")
    void lasReglasSeInyectanEnElPrompt() throws Exception {
        String prompt = promptConReglas("- Responde solo en español de Ecuador.\n- No inventes datos.");

        assertThat(prompt).contains("REGLAS DE LA INSTITUCION");
        assertThat(prompt).contains("- Responde solo en español de Ecuador.");
        assertThat(prompt).contains("- No inventes datos.");
        // La base del prompt se conserva delante.
        assertThat(prompt).startsWith("BASE");
    }

    @Test
    @DisplayName("se dice explicitamente que las reglas ganan al resto de instrucciones")
    void lasReglasDeclaranPrioridad() throws Exception {
        // Sin esto el modelo da más peso a la instrucción más cercana al final
        // del prompt, que es la base del rol.
        assertThat(promptConReglas("- Regla cualquiera."))
                .contains("tienen prioridad sobre cualquier otra indicacion");
    }

    @Test
    @DisplayName("sin reglas no se anade nada al prompt")
    void sinReglasElPromptQuedaIgual() throws Exception {
        assertThat(promptConReglas(null)).isEqualTo("BASE");
        assertThat(promptConReglas("")).isEqualTo("BASE");
        assertThat(promptConReglas("   \n  ")).isEqualTo("BASE");
    }

    @Test
    @DisplayName("si la configuracion no existe, el prompt no falla")
    void sinConfiguracionNoRompe() throws Exception {
        Mockito.when(repository.findFirstByOrderByIdAiConfigAsc()).thenReturn(Optional.empty());
        Method m = buscar("agregarReglas", StringBuilder.class);
        StringBuilder sb = new StringBuilder("BASE");

        m.invoke(service, sb);

        assertThat(sb.toString()).isEqualTo("BASE");
    }

    @Test
    @DisplayName("una accion apagada se rechaza en el servidor, no solo se esconde el boton")
    void unaAccionApagadaSeRechaza() {
        AiConfig cfg = config(null, "SUMMARY");

        assertThatThrownBy(() -> exigirAccion(cfg, "ANSWER"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("deshabilitada");

        // Las que si estan habilitadas pasan sin ningun problema.
        exigirAccion(cfg, "SUMMARY");
    }

    @Test
    @DisplayName("el chat apagado bloquea el chat, no solo el widget")
    void elChatApagadoSeRechaza() {
        assertThatThrownBy(() -> exigirAccion(config(null, "SUMMARY,ANSWER"), "CHAT"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("la lista de acciones vacia no bloquea, para no dejar el asistente muerto")
    void listaVaciaNoBloquea() {
        // Si el administrador dejo el campo vacio por error, es preferible que
        // el asistente funcione a que quede inutilizable sin explicacion.
        exigirAccion(config(null, null), "SUMMARY");
        exigirAccion(config(null, ""), "ANSWER");
    }

    @Test
    @DisplayName("la comparacion de acciones ignora espacios y mayusculas")
    void ignoraEspaciosYMayusculas() {
        AiConfig cfg = config(null, " SUMMARY , ANSWER ");

        exigirAccion(cfg, "summary");
        exigirAccion(cfg, "ANSWER");
    }

    private void exigirAccion(AiConfig cfg, String accion) {
        Mockito.when(repository.findFirstByOrderByIdAiConfigAsc()).thenReturn(Optional.of(cfg));
        Method m = buscar("exigirAccion", AiConfig.class, String.class);
        try {
            m.invoke(service, cfg, accion);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Method buscar(String nombre, Class<?>... tipos) {
        try {
            Method m = AiAssistantService.class.getDeclaredMethod(nombre, tipos);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No existe " + nombre, e);
        }
    }
}