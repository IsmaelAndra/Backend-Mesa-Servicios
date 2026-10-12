package com.mesaayuda.service;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.ChatConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La visibilidad de los acuses de lectura es una combinacion de roles, no un
 * booleano, asi que lo que se comprueba aqui es que cada codigo deja pasar
 * exactamente a quien debe. El caso que mas importa es que ningun ajuste pueda
 * dejar fuera al super administrador: es quien administra el resto.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatVisibilityPolicyTest {

    @Mock private ChatConfigRepository repository;

    private ChatVisibilityPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ChatVisibilityPolicy(repository);
    }

    private static User usuario(String rol) {
        User u = new User();
        u.setIdUser(id(1));
        u.setCorporateEmailUser("u@coacmagisterio.fin.ec");
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        return u;
    }

    /** Simula lo que hay en la tabla. */
    private void configurado(String codigo, boolean nombres) {
        var c = new com.mesaayuda.entity.ChatConfig();
        c.setIdChatConfig(id(1));
        c.setReadVisibleTo(codigo);
        c.setShowReadByNames(nombres);
        when(repository.findTopByOrderByIdChatConfigAsc()).thenReturn(Optional.of(c));
        // Se invalida la cache para que el test no dependa del reloj.
        policy.invalidarCache();
    }

    @Test
    @DisplayName("TODOS: qualquer rol ve los acuses")
    void todos() {
        configurado(ChatVisibilityPolicy.TODOS, true);
        assertThat(policy.puedeVer(usuario("USER"))).isTrue();
        assertThat(policy.puedeVer(usuario("AGENT"))).isTrue();
        assertThat(policy.puedeVer(usuario("ADMIN"))).isTrue();
        assertThat(policy.puedeVer(usuario("SUPER_ADMIN"))).isTrue();
    }

    @Test
    @DisplayName("TODOS_EXCEPTO_USUARIOS: el personal si, el usuario final no")
    void todosExceptoUsuarios() {
        configurado(ChatVisibilityPolicy.TODOS_EXCEPTO_USUARIOS, true);
        assertThat(policy.puedeVer(usuario("USER"))).isFalse();
        assertThat(policy.puedeVer(usuario("AGENT"))).isTrue();
        assertThat(policy.puedeVer(usuario("ADMIN"))).isTrue();
        assertThat(policy.puedeVer(usuario("SUPER_ADMIN"))).isTrue();
    }

    @Test
    @DisplayName("ADMIN: ni los agentes ni los usuarios finales")
    void soloAdmin() {
        configurado(ChatVisibilityPolicy.ADMIN, true);
        assertThat(policy.puedeVer(usuario("USER"))).isFalse();
        assertThat(policy.puedeVer(usuario("AGENT"))).isFalse();
        assertThat(policy.puedeVer(usuario("ADMIN"))).isTrue();
        assertThat(policy.puedeVer(usuario("SUPER_ADMIN"))).isTrue();
    }

    @Test
    @DisplayName("SUPER_ADMIN: el unico que ve los acuses")
    void soloSuperAdmin() {
        configurado(ChatVisibilityPolicy.SUPER_ADMIN, true);
        assertThat(policy.puedeVer(usuario("USER"))).isFalse();
        assertThat(policy.puedeVer(usuario("AGENT"))).isFalse();
        assertThat(policy.puedeVer(usuario("ADMIN"))).isFalse();
        assertThat(policy.puedeVer(usuario("SUPER_ADMIN"))).isTrue();
    }

    @Test
    @DisplayName("AGENTES: solo el agente, no el admin")
    void soloAgentes() {
        configurado(ChatVisibilityPolicy.AGENTES, true);
        assertThat(policy.puedeVer(usuario("USER"))).isFalse();
        assertThat(policy.puedeVer(usuario("AGENT"))).isTrue();
        assertThat(policy.puedeVer(usuario("ADMIN"))).isFalse();
        // Sigue entrando el super admin: es una excepcion deliberada.
        assertThat(policy.puedeVer(usuario("SUPER_ADMIN"))).isTrue();
    }

    @Test
    @DisplayName("sin fila en la tabla se usa el valor por defecto, no un error")
    void sinFilaUsaDefecto() {
        when(repository.findTopByOrderByIdChatConfigAsc()).thenReturn(Optional.empty());
        policy.invalidarCache();
        assertThat(policy.config().getReadVisibleTo()).isEqualTo(ChatVisibilityPolicy.TODOS_EXCEPTO_USUARIOS);
    }

    @Test
    @DisplayName("un valor desconocido en la base cae al valor por defecto")
    void valorDesconocido() {
        configurado("CHATTEANDO_UN_RATON", true);
        assertThat(policy.config().getReadVisibleTo()).isEqualTo(ChatVisibilityPolicy.TODOS_EXCEPTO_USUARIOS);
    }

    @Test
    @DisplayName("un rol en minúsculas o con espacios sigue funcionando")
    void rolConMayusculasImpares() {
        configurado(ChatVisibilityPolicy.AGENTES, true);
        assertThat(policy.puedeVer(usuario("agent"))).isTrue();
    }

    @Test
    @DisplayName("un usuario sin rol no ve nada, no falla")
    void sinRol() {
        configurado(ChatVisibilityPolicy.TODOS, true);
        assertThat(policy.puedeVer(new User())).isFalse();
        assertThat(policy.puedeVer(null)).isFalse();
    }

    @Test
    @DisplayName("el interruptor de nombres se respeta")
    void interruptorNombres() {
        configurado(ChatVisibilityPolicy.TODOS, false);
        assertThat(policy.puedeVer(usuario("USER"))).isTrue();
        assertThat(policy.muestraNombres()).isFalse();
    }

    @Test
    @DisplayName("guardar un valor no permitido se rechaza con un mensaje claro")
    void guardarValorInvalido() {
        assertThatThrownBy(() -> policy.actualizar("NO_EXISTE", true, id(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no permitido");
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("guardar sin valor se rechaza")
    void guardarVacio() {
        assertThatThrownBy(() -> policy.actualizar("  ", true, id(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("guardar normaliza a mayúsculas y crea la fila si no existe")
    void guardarNormalizaYCrea() {
        when(repository.findTopByOrderByIdChatConfigAsc()).thenReturn(Optional.empty());
        // save() devuelve la misma entidad que recibe, que es lo que hace JPA.
        when(repository.save(any(com.mesaayuda.entity.ChatConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Se manda en minúsculas a proposito: el servicio lo normaliza.
        var guardado = policy.actualizar("admin", false, id(7));

        assertThat(guardado.getReadVisibleTo()).isEqualTo("ADMIN");
        assertThat(guardado.getShowReadByNames()).isFalse();
        assertThat(guardado.getIdChatConfig()).isEqualTo(com.mesaayuda.entity.IdsConfiguracion.CHAT);
        assertThat(guardado.getUpdatedBy()).isEqualTo(id(7));
        verify(repository).save(any(com.mesaayuda.entity.ChatConfig.class));
    }

    @Test
    @DisplayName("normalizar a mayúsculas no convierte un nombre inventado en valido")
    void noAceptaNombresInventados() {
        when(repository.findTopByOrderByIdChatConfigAsc()).thenReturn(Optional.empty());
        // "solo_admin" parece la version en palabras de ADMIN, pero no es un
        // codigo de la lista y debe rechazarse en vez de aceptarse por parecido.
        assertThatThrownBy(() -> policy.actualizar("solo_admin", true, id(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no permitido");
    }

    @Test
    @DisplayName("guardar invalida la cache, para que el cambio se vea de inmediato")
    void guardarInvalidaCache() {
        configurado(ChatVisibilityPolicy.TODOS, true);
        assertThat(policy.puedeVer(usuario("USER"))).isTrue();

        when(repository.save(any(com.mesaayuda.entity.ChatConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        policy.actualizar(ChatVisibilityPolicy.SUPER_ADMIN, true, id(1));

        // Sin invalidar la cache, este assert seguiria viendo el valor anterior.
        assertThat(policy.puedeVer(usuario("USER"))).isFalse();
        assertThat(policy.puedeVer(usuario("SUPER_ADMIN"))).isTrue();
    }
}
