package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Ajustes de la visualizacion del chat. Se guarda una sola fila (id 1) y la
 * cambia un administrador, de modo que quien puede ver los acuses de lectura
 * ("Leido por ...") no quede fijo en el codigo.
 */
@Entity
@Table(name = "chat_config")
@Getter
@Setter
@NoArgsConstructor
public class ChatConfig {

    // Sin GeneratedValue a proposito: la tabla es un singleton con DEFAULT 1
    // (igual que session_config y routing_config), asi que el id se fija en 1.
    @Id
    @Column(name = "id_chat_config", updatable = false)
    private UUID idChatConfig = IdsConfiguracion.CHAT;
    /**
     * Codigo de quien puede ver los acuses de lectura. Los valores validos estan
     * en {@link com.mesaayuda.service.ChatVisibilityPolicy}.
     * <p>
     * Esto NO controla si se guardan las marcas de lectura, solo si se muestran.
     */
    @Column(name = "read_visible_to", nullable = false)
    private String readVisibleTo = "TODOS_EXCEPTO_USUARIOS";

    /**
     * Si es false, quien puede ver los acuses ve el doble check y cuantos han
     * leido, pero no los nombres. Sirve para AnswerReadentity sin exponer datos
     * personales cuando solo interesa el recuento.
     */
    @Column(name = "show_read_by_names", nullable = false)
    private Boolean showReadByNames = true;

    @Column(name = "updated_by")
    private UUID updatedBy;
    @CreationTimestamp
    @Column(name = "created_at_chat", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at_chat", nullable = false)
    private OffsetDateTime updatedAt;
}
