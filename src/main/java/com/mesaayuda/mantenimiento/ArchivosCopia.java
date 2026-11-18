package com.mesaayuda.mantenimiento;

import com.mesaayuda.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Los archivos subidos (carpeta {@code app.uploads.dir}: {@code publico/} con logos
 * y fondos de marca, {@code adjuntos/} con adjuntos de tickets, fotos y documentos
 * de activos, actas firmadas, archivos de proyectos...) dentro de la copia de la
 * base. Sin ellos, una base restaurada apuntaba a imágenes que no existían.
 *
 * <p>En el .zip van bajo {@code archivos/}. Al restaurar, lo que había se aparta
 * primero en {@code uploads/.respaldo-<fecha>/} (misma unidad: mover es
 * instantáneo y sirve también con el volumen de Docker, que no se puede renombrar)
 * y solo se borra cuando todo salió bien; si algo falla, se devuelve a su sitio.
 * Esa carpeta nunca se sirve: lo público es solo {@code /api/uploads/publico/**}.
 */
@Component
public class ArchivosCopia {

    private static final Logger log = LoggerFactory.getLogger(ArchivosCopia.class);
    /** Prefijo de los archivos dentro del .zip. */
    public static final String PREFIJO = "archivos/";
    private static final String RESPALDO = ".respaldo-";

    private final Path raiz;

    public ArchivosCopia(@Value("${app.uploads.dir}") String dir) {
        this.raiz = Paths.get(dir).toAbsolutePath().normalize();
    }

    /** Añade todos los archivos subidos al .zip (y la carpeta archivos/ aunque esté vacía). */
    public void agregarAlZip(ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry(PREFIJO));
        zip.closeEntry();
        if (!Files.isDirectory(raiz)) {
            return;
        }
        Files.walkFileTree(raiz, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // Respaldos de restauraciones anteriores: no forman parte de los datos.
                return dir.getFileName() != null && dir.getFileName().toString().startsWith(RESPALDO)
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path archivo, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE; // ni enlaces ni especiales
                }
                String relativo = raiz.relativize(archivo).toString().replace('\\', '/');
                ZipEntry e = new ZipEntry(PREFIJO + relativo);
                e.setLastModifiedTime(attrs.lastModifiedTime());
                zip.putNextEntry(e);
                Files.copy(archivo, zip);
                zip.closeEntry();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path archivo, IOException exc) {
                log.warn("No se pudo leer {} para la copia", archivo, exc);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Destino seguro de una entrada del .zip dentro de {@code destino}, o null si
     * no es un archivo de la copia (otra carpeta, ruta absoluta, "..").
     */
    public static Path destinoSeguro(Path destino, String nombre) {
        if (nombre == null || !nombre.startsWith(PREFIJO) || nombre.contains("\\") || nombre.contains("\0")) {
            return null;
        }
        String relativo = nombre.substring(PREFIJO.length());
        if (relativo.isEmpty() || relativo.startsWith("/")) {
            return null;
        }
        Path p = destino.resolve(relativo).normalize();
        return p.startsWith(destino) && !p.equals(destino) ? p : null;
    }

    /** Copia lo que hay en {@code entrada} hasta el archivo destino (ya validado). */
    public static long escribir(InputStream entrada, Path destino, long maximo) throws IOException {
        Files.createDirectories(destino.getParent());
        long total = 0;
        byte[] buf = new byte[64 * 1024];
        try (OutputStream out = Files.newOutputStream(destino)) {
            int n;
            while ((n = entrada.read(buf)) > 0) {
                total += n;
                if (total > maximo) {
                    throw new BadRequestException("La copia es demasiado grande al descomprimirla");
                }
                out.write(buf, 0, n);
            }
        }
        return total;
    }

    /** Lo que había, apartado para poder volver atrás. */
    public record Apartado(Path carpeta) {
    }

    /** Aparta todo lo que hay en la carpeta de archivos (para reemplazarlo o vaciarla). */
    public Apartado apartar() {
        try {
            Files.createDirectories(raiz);
            Path respaldo = raiz.resolve(RESPALDO + System.currentTimeMillis());
            Files.createDirectory(respaldo);
            for (Path hijo : hijos()) {
                if (!hijo.equals(respaldo)) {
                    Files.move(hijo, respaldo.resolve(hijo.getFileName()));
                }
            }
            return new Apartado(respaldo);
        } catch (IOException e) {
            throw new BadRequestException("No se pudieron apartar los archivos actuales; no se cambió nada.");
        }
    }

    /** Pone en la carpeta de archivos lo que viene en {@code origen} (los archivos del .zip). */
    public void poner(Path origen) throws IOException {
        if (origen == null || !Files.isDirectory(origen)) {
            return;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(origen)) {
            for (Path hijo : ds) {
                moverOCopiar(hijo, raiz.resolve(hijo.getFileName().toString()));
            }
        }
    }

    /** Todo salió bien: se borra lo apartado. */
    public void descartar(Apartado a) {
        borrarArbol(a == null ? null : a.carpeta());
    }

    /** Algo falló: se quita lo nuevo y vuelve lo que había. */
    public void devolver(Apartado a) {
        if (a == null) {
            return;
        }
        try {
            for (Path hijo : hijos()) {
                if (!hijo.equals(a.carpeta()) && !hijo.getFileName().toString().startsWith(RESPALDO)) {
                    borrarArbol(hijo);
                }
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(a.carpeta())) {
                for (Path hijo : ds) {
                    Files.move(hijo, raiz.resolve(hijo.getFileName()));
                }
            }
            borrarArbol(a.carpeta());
        } catch (IOException e) {
            log.error("No se pudieron devolver los archivos apartados: siguen en {}", a.carpeta(), e);
        }
    }

    private List<Path> hijos() throws IOException {
        List<Path> l = new ArrayList<>();
        if (!Files.isDirectory(raiz)) {
            return l;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(raiz)) {
            ds.forEach(l::add);
        }
        return l;
    }

    /** Mover (misma unidad) o, si no se puede, copiar el árbol. */
    private static void moverOCopiar(Path de, Path a) throws IOException {
        try {
            Files.move(de, a, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.walkFileTree(de, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(a.resolve(de.relativize(dir).toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, a.resolve(de.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public static void borrarArbol(Path p) {
        if (p == null || !Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.walkFileTree(p, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("No se pudo borrar {}", p, e);
        }
    }
}
