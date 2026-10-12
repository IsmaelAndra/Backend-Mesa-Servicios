package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Politica de privacidad tal y como la edita el panel.
 *
 * El cuerpo va en markdown y puede ser largo: una politica real ocupa varias
 * paginas, asi que el limite es de 20000 caracteres. El administrador puede
 * vaciar el cuerpo a proposito (para dejar la pantalla solo con el titulo), pero
 * no puede dejarlo en blanco sin querer al guardar, porque se le pide confirmar
 * y se avisa en la interfaz.
 */
public record PrivacyConfigRequest(

        @NotBlank(message = "El titulo es obligatorio")
        @Size(max = 160, message = "El titulo no puede exceder 160 caracteres")
        String privacyTitle,

        @Size(max = 30, message = "La version no puede exceder 30 caracteres")
        String privacyVersion,

        @Size(max = 20000, message = "El contenido no puede exceder 20000 caracteres")
        String privacyContent,

        @Size(max = 160, message = "El contacto no puede exceder 160 caracteres")
        String privacyContact,

        @Size(max = 60, message = "La fecha de vigencia no puede exceder 60 caracteres")
        String privacyEffective
) {
}