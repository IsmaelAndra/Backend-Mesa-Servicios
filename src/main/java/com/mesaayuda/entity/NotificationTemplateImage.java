package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Imagen de una plantilla de notificación, guardada en la base y no en disco.
 *
 * La ruta pública que se guarda en {@code notification_template.image_url} lleva
 * el {@code tokenImage}, un valor aleatorio, y no el id: el correo la carga sin
 * sesión, así que una ruta correlativa dejaría ver todas las imágenes subidas.
 */
@Entity
@Table(name = "notification_template_image")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplateImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_image", updatable = false)
    private UUID idImage;
    /** Token aleatorio con el que se sirve la imagen: /templates/image/{token}. */
    @Column(name = "token_image", nullable = false, unique = true, length = 64)
    private String tokenImage;

    /** Plantilla a la que pertenece. Es nulo mientras la imagen no se vincula. */
    @Column(name = "id_template")
    private UUID idTemplate;
    @Column(name = "mime_type_image", nullable = false, length = 100)
    private String mimeTypeImage;

    @Column(name = "original_name_image", length = 255)
    private String originalNameImage;

    /**
     * Bytes de la imagen.
 * <p>
 * Sin {@code @Lob}: en Hibernate 6 eso hace que un {@code byte[]} se trate como
 * large object y en PostgreSQL se envie como un OID (bigint), en vez de como
 * bytea, y la insercion falla con "la columna data_image es de tipo bytea pero
 * la expresion es de tipo bigint". Para bytea el tipo ya se corresponde solo.
 */
@Column(name = "data_image", nullable = false, columnDefinition = "bytea")
    private byte[] dataImage;

    @Column(name = "size_bytes_image", nullable = false)
    private Integer sizeBytesImage;

    @CreationTimestamp
    @Column(name = "created_at_image", nullable = false, updatable = false)
    private OffsetDateTime createdAtImage;
}