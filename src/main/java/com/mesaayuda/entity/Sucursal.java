package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Sede física de la institución.
 *
 * Es distinta del área: el área dice a qué se dedica la persona (sistemas,
 * transparencia, keuangan) y la sucursal dónde lo hace. Por eso las dos se
 * guardan por separado, y un ticket puede ser de una sede distinta a la del
 * solicitante.
 */
@Entity
@Table(name = "sucursal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Sucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_sucursal", updatable = false)
    private UUID idSucursal;
    /** Código corto (PCH, GYE,...) para verlo en los reportes. */
    @Column(name = "code_sucursal", nullable = false, unique = true, length = 20)
    private String codeSucursal;

    @Column(name = "name_sucursal", nullable = false, length = 100)
    private String nameSucursal;

    @Column(name = "description_sucursal")
    private String descriptionSucursal;

    @Column(name = "address_sucursal")
    private String addressSucursal;

    @Column(name = "phone_sucursal", length = 40)
    private String phoneSucursal;

    @Column(name = "icon_sucursal", length = 100)
    private String iconSucursal;

    @Column(name = "color_sucursal", length = 30)
    private String colorSucursal;

    @Column(name = "status_sucursal", nullable = false)
    private Boolean statusSucursal = true;

    @CreationTimestamp
    @Column(name = "created_at_sucursal", nullable = false, updatable = false)
    private OffsetDateTime createdAtSucursal;

    @UpdateTimestamp
    @Column(name = "updated_at_sucursal", nullable = false)
    private OffsetDateTime updatedAtSucursal;

    @Column(name = "deleted_sucursal", nullable = false)
    private Boolean deletedSucursal = false;
}