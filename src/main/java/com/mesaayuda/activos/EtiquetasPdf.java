package com.mesaayuda.activos;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mesaayuda.entity.Asset;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Etiquetas de inventario en hoja A4: 3 columnas × 8 filas (70 × 37 mm, el
 * tamaño de las hojas adhesivas más comunes). Código de barras Code 128 con el
 * código del activo, que es lo que lee el escáner de la app.
 */
@Component
public class EtiquetasPdf {

    private static final Font NOMBRE = new Font(Font.HELVETICA, 6.5f, Font.NORMAL, Color.DARK_GRAY);
    private static final Font COOP = new Font(Font.HELVETICA, 6, Font.BOLD, new Color(0x06, 0x2E, 0x86));
    private static final float ALTO = 101f;

    public byte[] generar(List<Asset> activos) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 18, 18, 18, 18);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.addTitle("Etiquetas de activos");
            doc.open();
            PdfContentByte cb = writer.getDirectContent();
            PdfPTable t = new PdfPTable(3);
            t.setWidthPercentage(100);
            for (Asset a : activos) {
                t.addCell(etiqueta(cb, a));
            }
            // Completa la última fila: una tabla solo dibuja filas llenas.
            int resto = activos.size() % 3;
            for (int i = 0; resto != 0 && i < 3 - resto; i++) {
                PdfPCell vacia = new PdfPCell(new Phrase(""));
                vacia.setBorder(Rectangle.NO_BORDER);
                vacia.setFixedHeight(ALTO);
                t.addCell(vacia);
            }
            doc.add(t);
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudieron generar las etiquetas", e);
        }
    }

    private static PdfPCell etiqueta(PdfContentByte cb, Asset a) {
        Barcode128 codigo = new Barcode128();
        codigo.setCode(a.getCodeAsset());
        codigo.setBarHeight(30f);
        codigo.setX(0.9f);
        codigo.setSize(8f);
        Image img = codigo.createImageWithBarcode(cb, Color.BLACK, Color.BLACK);
        img.scaleToFit(160f, 48f);

        PdfPCell c = new PdfPCell();
        c.setFixedHeight(ALTO);
        c.setBorderColor(new Color(0xCB, 0xD5, 0xE1));
        c.setBorderWidth(0.4f);
        c.setPadding(5);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph coop = new Paragraph("COAC MAGISTERIO DE PICHINCHA · ACTIVO", COOP);
        coop.setAlignment(Element.ALIGN_CENTER);
        coop.setSpacingAfter(3);
        c.addElement(coop);
        // La imagen va dentro de un párrafo con su propio alto: suelta, el texto
        // de arriba se montaba sobre las barras.
        Paragraph barras = new Paragraph();
        barras.add(new com.lowagie.text.Chunk(img, 0, 0, true));
        barras.setLeading(img.getScaledHeight() + 1);
        barras.setAlignment(Element.ALIGN_CENTER);
        c.addElement(barras);
        String nombre = a.getNameAsset() == null ? "" : a.getNameAsset();
        Paragraph n = new Paragraph(nombre.length() > 45 ? nombre.substring(0, 45) + "…" : nombre, NOMBRE);
        n.setAlignment(Element.ALIGN_CENTER);
        c.addElement(n);
        return c;
    }
}
