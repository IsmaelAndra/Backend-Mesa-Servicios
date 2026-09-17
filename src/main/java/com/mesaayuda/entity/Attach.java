package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "attach")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Attach {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_attach", updatable = false)
    private Long idAttach;

    @Column(name = "file_name_attach", nullable = false, length = 255)
    private String fileNameAttach;

    @Column(name = "storage_path_attach", nullable = false, columnDefinition = "text")
    private String storagePathAttach;

    @Column(name = "size_file_attach")
    private Long sizeFileAttach;

    @Column(name = "mime_type_attach", length = 100)
    private String mimeTypeAttach;

    @CreationTimestamp
    @Column(name = "created_at_attach", nullable = false, updatable = false)
    private OffsetDateTime createdAtAttach;

    @Column(name = "deleted_attach", nullable = false)
    private Boolean deletedAttach = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_attach", nullable = false)
    private Ticket ticketAttach;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_attach")
    private Answer answerAttach;
}