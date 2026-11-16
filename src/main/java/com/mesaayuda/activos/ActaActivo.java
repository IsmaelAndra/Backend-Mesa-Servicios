package com.mesaayuda.activos;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Acta FOR-POL-TIC-01-10 de entrega, devolución o reasignación. */
@Entity
@Table(name = "acta_activo")
@Getter
@Setter
@NoArgsConstructor
public class ActaActivo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_acta_activo", updatable = false)
    private UUID idActa;

    @Column(name = "numero_acta_activo", nullable = false, length = 40)
    private String numero;

    @Column(name = "tipo_acta_activo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "estado_acta_activo", nullable = false, length = 20)
    private String estado;

    @Column(name = "fecha_acta_activo", nullable = false)
    private LocalDate fecha;

    @Column(name = "ciudad_acta_activo", nullable = false, length = 80)
    private String ciudad;

    @Column(name = "asunto_acta_activo", length = 300)
    private String asunto;

    @Column(name = "cc_acta_activo", length = 300)
    private String cc;

    @Column(name = "custodio_acta_activo", nullable = false)
    private UUID custodioId;

    @Column(name = "custodio_anterior_acta_activo")
    private UUID custodioAnteriorId;

    @Column(name = "entregado_por_acta_activo")
    private UUID entregadoPorId;

    @Column(name = "recibido_por_acta_activo")
    private UUID recibidoPorId;

    @Column(name = "autorizado_por_acta_activo")
    private UUID autorizadoPorId;

    @Column(name = "revisado_por_acta_activo")
    private UUID revisadoPorId;

    @Column(name = "sucursal_acta_activo")
    private UUID sucursalId;

    @Column(name = "observaciones_acta_activo", columnDefinition = "text")
    private String observaciones;

    @Column(name = "aceptada_at_acta_activo")
    private OffsetDateTime aceptadaAt;

    @Column(name = "aceptada_ip_acta_activo", length = 64)
    private String aceptadaIp;

    @Column(name = "firmada_ruta_acta_activo", length = 500)
    private String firmadaRuta;

    @Column(name = "firmada_at_acta_activo")
    private OffsetDateTime firmadaAt;

    @Column(name = "creada_por_acta_activo")
    private UUID creadaPor;

    @CreationTimestamp
    @Column(name = "created_at_acta_activo", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

}
