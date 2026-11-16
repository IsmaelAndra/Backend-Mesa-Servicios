package com.mesaayuda.activos;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Pinta el acta FOR-POL-TIC-01-10 con el membrete de la cooperativa de fondo
 * (reports/membrete-coac.png, tamaño carta). Mantiene las secciones del formato:
 * encabezado con código y versión, Para/Cc/De/Asunto, identificación del acto,
 * custodio, detalle del bien (el equipo principal; los demás van en
 * "Accesorios entregados"), responsabilidades y el cuadro de firmas, que se
 * firma a mano.
 */
@Component
public class ActaPdf {

    private static final Color AZUL = new Color(0x06, 0x2E, 0x86);
    private static final Color GRIS = new Color(0xF1, 0xF4, 0xF9);
    private static final Font TITULO = new Font(Font.HELVETICA, 10, Font.BOLD, AZUL);
    private static final Font NEGRITA = new Font(Font.HELVETICA, 8f, Font.BOLD, Color.BLACK);
    private static final Font NORMAL = new Font(Font.HELVETICA, 8f, Font.NORMAL, Color.BLACK);
    private static final Font PEQUENA = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.DARK_GRAY);
    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
            "septiembre", "octubre", "noviembre", "diciembre"};
    private static final List<String> RESPONSABILIDADES = List.of(
            "Recibe el bien detallado en la presente acta en el estado consignado y con los accesorios descritos.",
            "Asume la custodia del bien y responde por su uso adecuado, conservación y presentación cuando le sea requerido.",
            "Utilizará el equipo exclusivamente para las funciones propias de su puesto de trabajo y no instalará software no autorizado por el Área TIC.",
            "Comunicará de forma inmediata al Área TIC cualquier daño, pérdida, sustracción o traslado del bien.");

    public byte[] generar(ActaActivoService.ActaVista a, AssetConfig c, String areaCustodio, String cedula) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Márgenes: el contenido va entre el logo (arriba) y el pie con la dirección (abajo).
            Document doc = new Document(PageSize.LETTER, 48, 48, 74, 68);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Membrete(cargarMembrete()));
            doc.addTitle("Acta " + a.numero());
            doc.open();

            encabezado(doc, c);
            Paragraph lugar = new Paragraph(a.ciudad() + ", " + a.fecha().getDayOfMonth() + " de "
                    + MESES[a.fecha().getMonthValue() - 1] + " de " + a.fecha().getYear(), NORMAL);
            lugar.setAlignment(Element.ALIGN_RIGHT);
            lugar.setSpacingAfter(3);
            doc.add(lugar);

            PdfPTable memo = tabla(new float[]{1.2f, 6f});
            memo.addCell(sinBorde("Para:", NEGRITA));
            memo.addCell(sinBorde(nombreCargo(a.autorizadoPor(), "JEFE DE TIC"), NORMAL));
            memo.addCell(sinBorde("Cc:", NEGRITA));
            memo.addCell(sinBorde(Objects.toString(a.cc(), ""), NORMAL));
            memo.addCell(sinBorde("De:", NEGRITA));
            memo.addCell(sinBorde(nombreCargo(a.entregadoPor(), "ASISTENTE DE TECNOLOGÍA"), NORMAL));
            memo.addCell(sinBorde("Asunto:", NEGRITA));
            memo.addCell(sinBorde(Objects.toString(a.asunto(), ""), NORMAL));
            memo.setSpacingAfter(2);
            doc.add(memo);

            // Identificación del acto
            doc.add(seccion("Identificación del acto:"));
            PdfPTable acto = tabla(new float[]{3f, 2f, 2f});
            acto.addCell(celda(etiquetaValor("Tipo de acto:",
                    marca("ENTREGA".equals(a.tipo())) + " Entrega   " + marca("DEVOLUCION".equals(a.tipo())) + " Devolución   "
                            + marca("REASIGNACION".equals(a.tipo())) + " Reasignación")));
            acto.addCell(celda(etiquetaValor("Número de Acta:", a.numero())));
            acto.addCell(celda(etiquetaValor("Fecha:", a.fecha().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))));
            doc.add(acto);

            // Custodio
            PdfPTable cust = tabla(new float[]{1f, 1f});
            cust.addCell(celda(etiquetaValor("Custodio:", nombreCargo(a.custodio(), ""))));
            cust.addCell(celda(etiquetaValor("Cédula de identidad:", cedula)));
            cust.addCell(celda(etiquetaValor("Área:", areaCustodio)));
            cust.addCell(celda(etiquetaValor("Agencia o sucursal:", Objects.toString(a.sucursal(), ""))));
            if (a.custodioAnterior() != null) {
                PdfPCell ant = celda(etiquetaValor("Custodio anterior:", nombreCargo(a.custodioAnterior(), "")));
                ant.setColspan(2);
                cust.addCell(ant);
            }
            doc.add(cust);

            // Detalle del bien: el principal; los demás en accesorios.
            ActaActivoService.ItemVista p = a.items().isEmpty() ? null : a.items().get(0);
            doc.add(seccion("Detalle del bien entregado o recibido:"));
            PdfPTable bien = tabla(new float[]{1f, 1f});
            bien.addCell(celda(etiquetaValor("Código del activo:", p == null ? "" : p.codigo())));
            bien.addCell(celda(etiquetaValor("Tipo de equipo:", p == null ? "" : Objects.toString(p.tipoEquipo(), ""))));
            bien.addCell(celda(etiquetaValor("Marca y modelo:", p == null ? "" : Objects.toString(p.marcaModelo(), ""))));
            bien.addCell(celda(etiquetaValor("Número de serie:", p == null ? "" : Objects.toString(p.serie(), ""))));
            bien.addCell(celda(etiquetaValor("Accesorios entregados:", accesorios(a))));
            String ef = p == null ? "" : Objects.toString(p.estadoFisico(), "");
            bien.addCell(celda(etiquetaValor("Estado del bien:", marca("NUEVO".equals(ef)) + " Nuevo   " + marca("BUENO".equals(ef))
                    + " Bueno   " + marca("REGULAR".equals(ef)) + " Regular   " + marca("CON_NOVEDADES".equals(ef)) + " Con novedades")));
            PdfPCell conf = celda(etiquetaValor("Configuración y software instalado:", p == null ? "" : Objects.toString(p.configuracion(), "")));
            conf.setColspan(2);
            bien.addCell(conf);
            PdfPCell nov = celda(etiquetaValor("Novedades observadas:", novedades(a)));
            nov.setColspan(2);
            bien.addCell(nov);
            doc.add(bien);

            // Responsabilidades
            doc.add(seccion("Responsabilidades del custodio:"));
            Paragraph decl = new Paragraph("El custodio declara que:", NORMAL);
            doc.add(decl);
            for (String r : RESPONSABILIDADES) {
                Paragraph item = new Paragraph("•  " + r, NORMAL);
                item.setIndentationLeft(10);
                doc.add(item);
            }

            // Suscripción: nombres impresos, firma a mano.
            doc.add(seccion("Suscripción:"));
            PdfPTable firmas = tabla(new float[]{1.4f, 3f, 2.6f});
            firmas.addCell(cabecera("RUBRO"));
            firmas.addCell(cabecera("CARGO"));
            firmas.addCell(cabecera("FIRMA"));
            fila(firmas, "ENTREGADO POR:", a.entregadoPor(), "ENTREGA".equals(a.tipo()) ? "ASISTENTE DE TECNOLOGÍA" : "");
            fila(firmas, "RECIBIDO POR:", a.recibidoPor(), "DEVOLUCION".equals(a.tipo()) ? "ASISTENTE DE TECNOLOGÍA" : "CUSTODIO DEL BIEN");
            fila(firmas, "AUTORIZADO POR:", a.autorizadoPor(), "JEFE DE TIC");
            fila(firmas, "REVISADO POR:", a.revisadoPor(), "");
            // El cuadro de firmas nunca se parte entre dos hojas.
            firmas.setKeepTogether(true);
            doc.add(firmas);

            if (a.aceptadaAt() != null) {
                Paragraph ok = new Paragraph("Aceptada en la Mesa de Servicios por " + a.custodio().nombre() + " el "
                        + a.aceptadaAt().atZoneSameInstant(java.time.ZoneId.of("America/Guayaquil"))
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ".", PEQUENA);
                ok.setSpacingBefore(4);
                doc.add(ok);
            }
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el acta en PDF", e);
        }
    }

    // ---------------------------------------------------------------- piezas

    private static void encabezado(Document doc, AssetConfig c) throws DocumentException {
        PdfPTable t = tabla(new float[]{4f, 1.6f});
        PdfPCell titulo = new PdfPCell(new Phrase("ACTA DE ENTREGA O RECEPCIÓN DE EQUIPOS TECNOLÓGICOS", TITULO));
        titulo.setPadding(4);
        t.addCell(titulo);
        t.addCell(celda(new Phrase("CÓDIGO: " + c.getCodigoFormulario(), NEGRITA)));
        PdfPCell area = new PdfPCell(new Phrase("TECNOLOGÍA DE LA INFORMACIÓN Y COMUNICACIÓN", NEGRITA));
        area.setPadding(4);
        t.addCell(area);
        t.addCell(celda(new Phrase("VERSIÓN: " + c.getVersionFormulario(), NEGRITA)));
        t.setSpacingAfter(4);
        doc.add(t);
    }

    private static String accesorios(ActaActivoService.ActaVista a) {
        List<String> partes = new ArrayList<>();
        ActaActivoService.ItemVista p = a.items().isEmpty() ? null : a.items().get(0);
        if (p != null && p.accesorios() != null && !p.accesorios().isBlank()) partes.add(p.accesorios().trim());
        for (int i = 1; i < a.items().size(); i++) {
            ActaActivoService.ItemVista x = a.items().get(i);
            String serie = x.serie() == null || x.serie().isBlank() ? "" : ", serie " + x.serie();
            partes.add(Objects.toString(x.tipoEquipo(), "Equipo") + " " + Objects.toString(x.marcaModelo(), "").trim()
                    + " (" + x.codigo() + serie + ")");
        }
        return String.join("\n", partes);
    }

    private static String novedades(ActaActivoService.ActaVista a) {
        List<String> partes = new ArrayList<>();
        for (ActaActivoService.ItemVista x : a.items()) {
            if (x.novedades() != null && !x.novedades().isBlank()) {
                partes.add(a.items().size() > 1 ? x.codigo() + ": " + x.novedades().trim() : x.novedades().trim());
            }
        }
        if (a.observaciones() != null && !a.observaciones().isBlank()) partes.add(a.observaciones().trim());
        return partes.isEmpty() ? "Ninguna" : String.join("\n", partes);
    }

    private static void fila(PdfPTable t, String rubro, ActaActivoService.Persona p, String cargoPorDefecto) {
        PdfPCell r = celda(new Phrase(rubro, NEGRITA));
        r.setFixedHeight(36);
        r.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(r);
        PdfPCell nc = celda(new Phrase(nombreCargo(p, cargoPorDefecto), NORMAL));
        nc.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(nc);
        t.addCell(celda(new Phrase("", NORMAL)));
    }

    private static String nombreCargo(ActaActivoService.Persona p, String cargoPorDefecto) {
        if (p == null) return "Nombre y Apellido\n" + cargoPorDefecto;
        String cargo = p.cargo() == null || p.cargo().isBlank() ? cargoPorDefecto : p.cargo().toUpperCase(Locale.ROOT);
        return p.nombre() + (cargo.isBlank() ? "" : "\n" + cargo);
    }

    private static String marca(boolean si) {
        return si ? "(X)" : "(  )";
    }

    private static Paragraph seccion(String texto) {
        Paragraph p = new Paragraph(texto, TITULO);
        p.setSpacingBefore(5);
        p.setSpacingAfter(2);
        return p;
    }

    private static PdfPTable tabla(float[] anchos) {
        PdfPTable t = new PdfPTable(anchos);
        t.setWidthPercentage(100);
        return t;
    }

    private static Phrase etiquetaValor(String etiqueta, String valor) {
        Phrase f = new Phrase();
        f.add(new Chunk(etiqueta + "\n", NEGRITA));
        f.add(new Chunk(valor == null ? "" : valor, NORMAL));
        return f;
    }

    private static PdfPCell celda(Phrase contenido) {
        PdfPCell c = new PdfPCell(contenido);
        c.setPadding(3);
        c.setBorderColor(new Color(0x9C, 0xA3, 0xAF));
        return c;
    }

    private static PdfPCell cabecera(String texto) {
        PdfPCell c = celda(new Phrase(texto, NEGRITA));
        c.setBackgroundColor(GRIS);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        return c;
    }

    private static PdfPCell sinBorde(String texto, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(texto, f));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(1.5f);
        return c;
    }

    private static Image cargarMembrete() {
        try (InputStream in = new ClassPathResource("reports/membrete-coac.png").getInputStream()) {
            return Image.getInstance(in.readAllBytes());
        } catch (Exception e) {
            return null;
        }
    }

    /** Dibuja la hoja membretada a página completa, debajo del contenido. */
    private static final class Membrete extends PdfPageEventHelper {
        private final Image fondo;

        Membrete(Image fondo) {
            this.fondo = fondo;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            if (fondo == null) return;
            try {
                Rectangle pagina = document.getPageSize();
                fondo.scaleAbsolute(pagina.getWidth(), pagina.getHeight());
                fondo.setAbsolutePosition(0, 0);
                writer.getDirectContentUnder().addImage(fondo);
            } catch (DocumentException ignorada) {
                // Sin membrete el acta sigue siendo válida.
            }
        }
    }
}
