package com.mesaayuda.activos;

import com.mesaayuda.exception.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numeración de activos y actas (configurable en asset_config).
 * Tokens del formato: {TIPO} (prefijo del tipo), {ANIO} (año) y {NUM:n}
 * (número con n dígitos). Ejemplos: "TI-{TIPO}-{NUM:6}" → TI-LAP-000123;
 * "ACTA-{ANIO}-{NUM:4}" → ACTA-2026-0001 (el contador del acta va por año).
 * El número se reserva con un UPSERT ... RETURNING: dos altas a la vez nunca
 * reciben el mismo.
 */
@Component
public class NumeracionActivos {

    /** Fila única de asset_config (V36). */
    public static final UUID ID_CONFIG = UUID.fromString("00000000-0000-0000-0000-00000000a5e7");
    private static final Pattern NUM = Pattern.compile("\\{NUM(?::(\\d{1,2}))?}");
    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");

    private final JdbcTemplate jdbc;
    private final AssetConfigRepository configuracion;
    private final com.mesaayuda.repository.AssetRepository activos;

    public NumeracionActivos(JdbcTemplate jdbc, AssetConfigRepository configuracion,
                             com.mesaayuda.repository.AssetRepository activos) {
        this.jdbc = jdbc;
        this.configuracion = configuracion;
        this.activos = activos;
    }

    public AssetConfig config() {
        return configuracion.findById(ID_CONFIG).orElseGet(() -> {
            AssetConfig c = new AssetConfig();
            c.setIdAssetConfig(ID_CONFIG);
            c.setFormatoCodigo("TI-{TIPO}-{NUM:6}");
            c.setFormatoActa("ACTA-{ANIO}-{NUM:4}");
            c.setCiudad("Quito");
            c.setCodigoFormulario("FOR-POL-TIC-01-10");
            c.setVersionFormulario("V01.00");
            return configuracion.save(c);
        });
    }

    /** Siguiente código libre para un tipo (salta los que ya existan por un alta manual). */
    @Transactional
    public String siguienteCodigo(String prefijoTipo) {
        AssetConfig c = config();
        String clave = Boolean.TRUE.equals(c.getContadorPorTipo()) ? "ACTIVO:" + prefijoTipo.toUpperCase() : "ACTIVO";
        for (int intento = 0; intento < 1000; intento++) {
            String codigo = aplicar(c.getFormatoCodigo(), prefijoTipo, reservar(clave));
            if (!activos.existsByCodeAsset(codigo)) {
                return codigo;
            }
        }
        throw new BadRequestException("No se encontró un código libre: revise el formato de numeración");
    }

    @Transactional
    public String siguienteActa() {
        AssetConfig c = config();
        int anio = LocalDate.now(ZONA).getYear();
        return aplicar(c.getFormatoActa(), "", reservar("ACTA:" + anio));
    }

    private long reservar(String clave) {
        Long v = jdbc.queryForObject(
                "INSERT INTO asset_contador (clave_asset_contador, valor_asset_contador) VALUES (?, 1) "
                        + "ON CONFLICT (clave_asset_contador) DO UPDATE "
                        + "SET valor_asset_contador = asset_contador.valor_asset_contador + 1 "
                        + "RETURNING valor_asset_contador", Long.class, clave);
        return v == null ? 1 : v;
    }

    /** Aplica el formato. Público para la vista previa de la configuración. */
    public static String aplicar(String formato, String prefijoTipo, long numero) {
        String r = formato.replace("{TIPO}", prefijoTipo == null ? "" : prefijoTipo.toUpperCase())
                .replace("{ANIO}", String.valueOf(LocalDate.now(ZONA).getYear()));
        Matcher m = NUM.matcher(r);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int digitos = m.group(1) == null ? 1 : Integer.parseInt(m.group(1));
            m.appendReplacement(sb, String.format("%0" + Math.max(1, digitos) + "d", numero));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Un formato válido lleva {NUM} (si no, todos los códigos serían iguales) y solo caracteres seguros. */
    public static void validarFormato(String formato, String que) {
        if (formato == null || !NUM.matcher(formato).find()) {
            throw new BadRequestException("El formato de " + que + " debe incluir {NUM} o {NUM:n}");
        }
        String sinTokens = formato.replace("{TIPO}", "").replace("{ANIO}", "").replaceAll("\\{NUM(?::\\d{1,2})?}", "");
        if (!sinTokens.matches("[A-Za-z0-9 ._/-]*")) {
            throw new BadRequestException("El formato de " + que + " solo admite letras, números, espacios y - _ . /");
        }
    }
}
