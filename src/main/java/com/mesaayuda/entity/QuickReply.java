package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Respuesta rápida del chat: un texto guardado que el personal inserta con un
 * clic o escribiendo "/". Es de quien la crea; las compartidas las ve todo el
 * personal y solo las publica un administrador.
 */
@Entity
@Table(name = "quick_reply")
@Getter
@Setter
@NoArgsConstructor
public class QuickReply {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_quick_reply", updatable = false)
    private UUID idQuickReply;

    @Column(name = "title_quick_reply", nullable = false, length = 80)
    private String titleQuickReply;

    @Column(name = "content_quick_reply", nullable = false, columnDefinition = "text")
    private String contentQuickReply;

    @Column(name = "shared_quick_reply", nullable = false)
    private Boolean sharedQuickReply = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_quick_reply", nullable = false)
    private User ownerQuickReply;

    @Column(name = "deleted_quick_reply", nullable = false)
    private Boolean deletedQuickReply = false;

    @CreationTimestamp
    @Column(name = "created_at_quick_reply", nullable = false, updatable = false)
    private OffsetDateTime createdAtQuickReply;

    @UpdateTimestamp
    @Column(name = "updated_at_quick_reply", nullable = false)
    private OffsetDateTime updatedAtQuickReply;
}
