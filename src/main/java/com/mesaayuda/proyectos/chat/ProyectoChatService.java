package com.mesaayuda.proyectos.chat;

import com.mesaayuda.config.UploadZones;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.proyectos.ProyectoService;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.service.SseEventBroker;
import com.mesaayuda.service.TicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Chat grupal de un proyecto (V39). Lee quien ve el proyecto (también el
 * auditor); escribe el equipo. Los archivos siguen las reglas de los adjuntos
 * de tickets (lista de extensiones + contenido comprobado por bytes) y se
 * guardan en la zona privada; se bajan solo por la API con permiso. Cada
 * mensaje llega en vivo (SSE "proyecto-chat") a los participantes.
 */
@Service
public class ProyectoChatService {

    public static final int MAX_ADJUNTOS = 5;
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    /** El mensaje es HTML con formato (negrita, listas, citas...). */
    private static final int MAX_TEXTO = 20000;
    private static final java.util.regex.Pattern ETIQUETAS_PELIGROSAS = java.util.regex.Pattern.compile(
            "(?is)<\\s*(iframe|object|embed|style|link|meta|form|input|button)[^>]*>(.*?<\\s*/\\s*\\1\\s*>)?");

    private final ProyectoService proyectos;
    private final ProyectoMensajeRepository mensajes;
    private final ProyectoAdjuntoRepository adjuntos;
    private final UserRepository usuarios;
    private final TicketService ticketService;
    private final SseEventBroker sse;
    private final com.mesaayuda.service.NotificationService avisos;

    @Value("${app.uploads.dir}")
    private String uploadDir;

    public ProyectoChatService(ProyectoService proyectos, ProyectoMensajeRepository mensajes,
                               ProyectoAdjuntoRepository adjuntos, UserRepository usuarios,
                               TicketService ticketService, SseEventBroker sse,
                               com.mesaayuda.service.NotificationService avisos) {
        this.proyectos = proyectos;
        this.mensajes = mensajes;
        this.adjuntos = adjuntos;
        this.usuarios = usuarios;
        this.ticketService = ticketService;
        this.sse = sse;
        this.avisos = avisos;
    }

    public record Participante(UUID id, String nombre, String foto) {
    }

    /** Quienes se pueden mencionar con @: los participantes del chat. */
    @Transactional(readOnly = true)
    public List<Participante> participantes(UUID proyectoId) {
        proyectos.accesoChat(proyectoId);
        List<Participante> out = new ArrayList<>();
        usuarios.findAllById(proyectos.participantes(proyectoId)).forEach(u -> {
            if (!Boolean.TRUE.equals(u.getDeletedUser())) {
                out.add(new Participante(u.getIdUser(), u.getFullName(), u.getPhotoUrl()));
            }
        });
        out.sort(java.util.Comparator.comparing(x -> x.nombre() == null ? "" : x.nombre().toLowerCase()));
        return out;
    }

    /** HTML del editor sin lo que podría ejecutarse (scripts, on*=, javascript:, iframes...). */
    static String limpiarHtml(String html) {
        if (html == null) return "";
        String s = com.mesaayuda.plantillas.PlantillaAreaService.limpiar(html);
        return ETIQUETAS_PELIGROSAS.matcher(s).replaceAll("");
    }

    /** Texto plano del HTML (para saber si está vacío y para el aviso de mención). */
    static String textoPlano(String html) {
        if (html == null) return "";
        return html.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("\\s+", " ").strip();
    }

    public record AdjuntoVista(UUID id, String nombre, String mime, long tamano) {
    }

    /** Cita del mensaje al que se responde. */
    public record Cita(UUID id, String autor, String extracto, boolean eliminado) {
    }

    /**
     * Un mensaje eliminado viaja sin texto ni archivos (eliminado = true): el
     * chat pinta "Se eliminó este mensaje", como WhatsApp.
     */
    public record MensajeVista(UUID id, UUID proyectoId, UUID autorId, String autor, String autorFoto, String texto,
                               OffsetDateTime creado, boolean propio, List<AdjuntoVista> adjuntos,
                               OffsetDateTime editado, boolean eliminado, Cita respondeA) {
    }

    /** Minutos durante los que el autor puede editar o eliminar su mensaje (igual que en tickets). */
    public static final int MINUTOS_EDICION = 15;

    public record Descarga(String nombre, String mime, byte[] datos) {
    }

    @Transactional(readOnly = true)
    public List<MensajeVista> listar(UUID proyectoId, OffsetDateTime antesDe, int tam) {
        ProyectoService.AccesoChat a = proyectos.accesoChat(proyectoId);
        List<ProyectoMensaje> l = mensajes.pagina(proyectoId, antesDe, PageRequest.of(0, Math.max(1, Math.min(tam, 100))));
        return vistas(l, a.u().getIdUser());
    }

    @Transactional
    public MensajeVista enviar(UUID proyectoId, String texto, List<MultipartFile> archivos) {
        return enviar(proyectoId, texto, archivos, null, null);
    }

    @Transactional
    public MensajeVista enviar(UUID proyectoId, String texto, List<MultipartFile> archivos, List<UUID> menciones) {
        return enviar(proyectoId, texto, archivos, menciones, null);
    }

    @Transactional
    public MensajeVista enviar(UUID proyectoId, String texto, List<MultipartFile> archivos, List<UUID> menciones,
                               UUID respondeA) {
        ProyectoService.AccesoChat a = proyectos.accesoChat(proyectoId);
        if (ProyectoService.esCerrado(a.p())) {
            throw new BadRequestException(ProyectoService.mensajeCerrado(a.p()));
        }
        if (!a.escribe()) {
            throw new AccessDeniedException("Solo el equipo del proyecto escribe en el chat");
        }
        String limpio = limpiarHtml(texto == null ? "" : texto.strip());
        // Un editor vacío deja "<p><br></p>": cuenta como vacío.
        if (textoPlano(limpio).isEmpty() && !limpio.contains("<img")) {
            limpio = "";
        }
        List<MultipartFile> files = archivos == null ? List.of()
                : archivos.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (limpio.isEmpty() && files.isEmpty()) {
            throw new BadRequestException("Escriba un mensaje o adjunte un archivo");
        }
        if (limpio.length() > MAX_TEXTO) {
            throw new BadRequestException("El mensaje admite hasta " + MAX_TEXTO + " caracteres");
        }
        if (files.size() > MAX_ADJUNTOS) {
            throw new BadRequestException("Como máximo " + MAX_ADJUNTOS + " archivos por mensaje");
        }
        // Se valida todo antes de guardar nada.
        List<String> tipos = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f.getSize() > MAX_BYTES) {
                throw new BadRequestException("\"" + f.getOriginalFilename() + "\" pasa de 10 MB");
            }
            tipos.add(ticketService.validarTipoAdjunto(f.getOriginalFilename(), f));
        }

        // Solo se cita un mensaje del mismo proyecto.
        if (respondeA != null && mensajes.findById(respondeA)
                .filter(x -> proyectoId.equals(x.getProyectoId())).isEmpty()) {
            throw new BadRequestException("El mensaje al que responde no existe");
        }

        ProyectoMensaje m = new ProyectoMensaje();
        m.setRespondeA(respondeA);
        m.setProyectoId(proyectoId);
        m.setAutorId(a.u().getIdUser());
        m.setTexto(limpio.isEmpty() ? null : limpio);
        m = mensajes.save(m);

        // La carpeta solo hace falta si hay archivos.
        Path dir = files.isEmpty() ? null : carpeta(proyectoId);
        for (int i = 0; i < files.size(); i++) {
            MultipartFile f = files.get(i);
            String nombre = f.getOriginalFilename() == null ? "archivo" : f.getOriginalFilename();
            String unico = UUID.randomUUID() + "_" + nombre.replaceAll("[^a-zA-Z0-9._-]", "_");
            try {
                Files.createDirectories(dir);
                Files.copy(f.getInputStream(), dir.resolve(unico), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new BadRequestException("No se pudo guardar \"" + nombre + "\"");
            }
            ProyectoAdjunto adj = new ProyectoAdjunto();
            adj.setMensajeId(m.getIdMensaje());
            adj.setNombre(nombre.length() > 255 ? nombre.substring(0, 255) : nombre);
            adj.setRuta(proyectoId + "/" + unico);
            adj.setMime(tipos.get(i));
            adj.setTamano(f.getSize());
            adjuntos.save(adj);
        }
        // Quien escribe ya leyó hasta aquí.
        mensajes.marcarLeido(proyectoId, a.u().getIdUser());

        MensajeVista v = vistas(List.of(m), a.u().getIdUser()).get(0);
        Set<UUID> destino = proyectos.participantes(proyectoId);
        destino.add(a.u().getIdUser());
        // "propio" depende de quien recibe: cada cliente lo recalcula con autorId.
        sse.publishAUsuarios(destino, "proyecto-chat", v);
        avisarMenciones(a, menciones, destino, limpio);
        return v;
    }

    /**
     * @menciones: solo a participantes del chat (nunca a uno mismo); campana en
     * vivo + correo con la plantilla PROYECTO_MENCION.
     */
    private void avisarMenciones(ProyectoService.AccesoChat a, List<UUID> menciones, Set<UUID> participantes, String html) {
        if (menciones == null || menciones.isEmpty()) {
            return;
        }
        Set<UUID> ids = new LinkedHashSet<>(menciones);
        ids.retainAll(participantes);
        ids.remove(a.u().getIdUser());
        if (ids.isEmpty()) {
            return;
        }
        String extracto = textoPlano(html);
        if (extracto.length() > 280) {
            extracto = extracto.substring(0, 280) + "…";
        }
        String proyecto = a.p().getNombre();
        String mensaje = "<p><b>" + esc(a.u().getFullName()) + "</b> te mencionó en el chat del proyecto <b>"
                + esc(proyecto) + "</b>:</p><blockquote style=\"margin:12px 0;padding:8px 12px;border-left:3px solid #062e86;"
                + "background:#f3f4f6;\">" + esc(extracto) + "</blockquote>";
        for (User u : usuarios.findAllById(ids)) {
            avisos.notifyProyecto(u, "PROYECTO_MENCION", "Te mencionaron en " + proyecto, mensaje,
                    "proyectos/" + a.p().getIdProyecto() + "?pestana=chat");
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Corrige un mensaje propio durante los primeros {@link #MINUTOS_EDICION} minutos. */
    @Transactional
    public MensajeVista editar(UUID proyectoId, UUID mensajeId, String texto) {
        ProyectoService.AccesoChat a = proyectos.accesoChat(proyectoId);
        ProyectoMensaje m = propioEditable(a, proyectoId, mensajeId);
        String limpio = limpiarHtml(texto == null ? "" : texto.strip());
        if (textoPlano(limpio).isEmpty() && !limpio.contains("<img")) {
            throw new BadRequestException("El mensaje no puede quedar vacío; si quiere quitarlo, elimínelo");
        }
        if (limpio.length() > MAX_TEXTO) {
            throw new BadRequestException("El mensaje admite hasta " + MAX_TEXTO + " caracteres");
        }
        m.setTexto(limpio);
        m.setEditadoAt(OffsetDateTime.now());
        return publicarCambio(a, mensajes.save(m));
    }

    /** Elimina un mensaje propio: queda en el hilo como "Se eliminó este mensaje". */
    @Transactional
    public MensajeVista eliminar(UUID proyectoId, UUID mensajeId) {
        ProyectoService.AccesoChat a = proyectos.accesoChat(proyectoId);
        ProyectoMensaje m = propioEditable(a, proyectoId, mensajeId);
        m.setDeleted(true);
        return publicarCambio(a, mensajes.save(m));
    }

    private ProyectoMensaje propioEditable(ProyectoService.AccesoChat a, UUID proyectoId, UUID mensajeId) {
        if (ProyectoService.esCerrado(a.p())) {
            throw new BadRequestException(ProyectoService.mensajeCerrado(a.p()));
        }
        if (!a.escribe()) {
            throw new AccessDeniedException("Solo el equipo del proyecto escribe en el chat");
        }
        ProyectoMensaje m = mensajes.findById(mensajeId)
                .filter(x -> proyectoId.equals(x.getProyectoId()) && !Boolean.TRUE.equals(x.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Mensaje", "id", mensajeId));
        if (!m.getAutorId().equals(a.u().getIdUser())) {
            throw new AccessDeniedException("Solo quien escribió el mensaje puede cambiarlo");
        }
        if (m.getCreatedAt() != null && m.getCreatedAt().plusMinutes(MINUTOS_EDICION).isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Solo se puede editar o eliminar un mensaje durante los primeros "
                    + MINUTOS_EDICION + " minutos");
        }
        return m;
    }

    /** El cambio llega en vivo a todos (mismo evento: el cliente reemplaza por id). */
    private MensajeVista publicarCambio(ProyectoService.AccesoChat a, ProyectoMensaje m) {
        MensajeVista v = vistas(List.of(m), a.u().getIdUser()).get(0);
        Set<UUID> destino = proyectos.participantes(m.getProyectoId());
        destino.add(a.u().getIdUser());
        sse.publishAUsuarios(destino, "proyecto-chat", v);
        return v;
    }

    @Transactional
    public void marcarLeido(UUID proyectoId) {
        ProyectoService.AccesoChat a = proyectos.accesoChat(proyectoId);
        mensajes.marcarLeido(proyectoId, a.u().getIdUser());
    }

    /** Mensajes sin leer en cada proyecto que ve la persona (los que no aparecen, 0). */
    @Transactional(readOnly = true)
    public Map<UUID, Long> noLeidos() {
        List<UUID> ids = proyectos.listar().stream().map(ProyectoService.Resumen::id).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        UUID yo = proyectos.accesoActual().getIdUser();
        Map<UUID, Long> out = new HashMap<>();
        for (Object[] fila : mensajes.noLeidos(yo, ids)) {
            out.put((UUID) fila[0], ((Number) fila[1]).longValue());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Descarga descargar(UUID proyectoId, UUID adjuntoId) {
        proyectos.accesoChat(proyectoId);
        ProyectoAdjunto adj = adjuntos.findById(adjuntoId)
                .orElseThrow(() -> new ResourceNotFoundException("Archivo", "id", adjuntoId));
        ProyectoMensaje m = mensajes.findById(adj.getMensajeId())
                .filter(x -> proyectoId.equals(x.getProyectoId()) && !Boolean.TRUE.equals(x.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Archivo", "id", adjuntoId));
        Path base = raiz();
        Path ruta = base.resolve(adj.getRuta()).normalize();
        if (!ruta.startsWith(base)) {
            throw new AccessDeniedException("Ruta de archivo no válida");
        }
        try {
            return new Descarga(adj.getNombre(), adj.getMime(), Files.readAllBytes(ruta));
        } catch (IOException e) {
            throw new ResourceNotFoundException("Archivo", "id", m.getIdMensaje());
        }
    }

    private List<MensajeVista> vistas(List<ProyectoMensaje> l, UUID yo) {
        if (l.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<AdjuntoVista>> porMensaje = adjuntos
                .findByMensajeIdIn(l.stream().map(ProyectoMensaje::getIdMensaje).toList()).stream()
                .collect(Collectors.groupingBy(ProyectoAdjunto::getMensajeId,
                        Collectors.mapping(x -> new AdjuntoVista(x.getIdAdjunto(), x.getNombre(), x.getMime(), x.getTamano()),
                                Collectors.toList())));
        // Mensajes citados (pueden ser de otra página): una consulta para todos.
        Map<UUID, ProyectoMensaje> citados = new HashMap<>();
        Set<UUID> idsCitados = l.stream().map(ProyectoMensaje::getRespondeA).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (!idsCitados.isEmpty()) {
            mensajes.findAllById(idsCitados).forEach(c -> citados.put(c.getIdMensaje(), c));
        }
        Set<UUID> idsAutores = new HashSet<>();
        l.forEach(m -> idsAutores.add(m.getAutorId()));
        citados.values().forEach(c -> idsAutores.add(c.getAutorId()));
        Map<UUID, User> autores = new HashMap<>();
        usuarios.findAllById(idsAutores).forEach(u -> autores.put(u.getIdUser(), u));
        return l.stream().map(m -> {
            User u = autores.get(m.getAutorId());
            boolean eliminado = Boolean.TRUE.equals(m.getDeleted());
            Cita cita = null;
            ProyectoMensaje c = m.getRespondeA() == null ? null : citados.get(m.getRespondeA());
            if (c != null) {
                boolean citaEliminada = Boolean.TRUE.equals(c.getDeleted());
                String extracto = citaEliminada ? "" : textoPlano(c.getTexto());
                if (extracto.isEmpty() && !citaEliminada) extracto = "Archivo adjunto";
                if (extracto.length() > 140) extracto = extracto.substring(0, 140) + "…";
                User ua = autores.get(c.getAutorId());
                cita = new Cita(c.getIdMensaje(), ua == null ? "—" : ua.getFullName(), extracto, citaEliminada);
            }
            return new MensajeVista(m.getIdMensaje(), m.getProyectoId(), m.getAutorId(),
                    u == null ? "—" : u.getFullName(), u == null ? null : u.getPhotoUrl(),
                    eliminado ? null : m.getTexto(), m.getCreatedAt(),
                    m.getAutorId().equals(yo), eliminado ? List.of() : porMensaje.getOrDefault(m.getIdMensaje(), List.of()),
                    m.getEditadoAt(), eliminado, eliminado ? null : cita);
        }).toList();
    }

    private Path raiz() {
        return Paths.get(uploadDir).toAbsolutePath().normalize().resolve(UploadZones.ADJUNTOS).resolve("proyectos").normalize();
    }

    private Path carpeta(UUID proyectoId) {
        return raiz().resolve(proyectoId.toString());
    }
}
