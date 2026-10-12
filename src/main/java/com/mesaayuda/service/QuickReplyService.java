package com.mesaayuda.service;

import com.mesaayuda.dto.QuickReplyRequest;
import com.mesaayuda.dto.QuickReplyResponse;
import com.mesaayuda.entity.QuickReply;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.QuickReplyRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Respuestas rápidas del chat.
 * <p>
 * Cada una es de quien la crea. Marcarla como compartida (visible para todo el
 * personal) es cosa de un administrador: si no, cualquier agente podría poner
 * textos en boca de toda la mesa.
 */
@Service
public class QuickReplyService {

    private final QuickReplyRepository repository;
    private final UserService userService;

    public QuickReplyService(QuickReplyRepository repository, UserService userService) {
        this.repository = repository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<QuickReplyResponse> listar() {
        User actual = userService.getCurrentUser();
        return repository.visiblesPara(actual.getIdUser()).stream()
                .map(q -> respuesta(q, actual))
                .toList();
    }

    @Transactional
    public QuickReplyResponse crear(QuickReplyRequest req) {
        User actual = userService.getCurrentUser();
        QuickReply q = new QuickReply();
        q.setOwnerQuickReply(actual);
        aplicar(q, req, actual);
        return respuesta(repository.save(q), actual);
    }

    @Transactional
    public QuickReplyResponse actualizar(UUID id, QuickReplyRequest req) {
        User actual = userService.getCurrentUser();
        QuickReply q = editable(id, actual);
        aplicar(q, req, actual);
        return respuesta(repository.save(q), actual);
    }

    @Transactional
    public void eliminar(UUID id) {
        User actual = userService.getCurrentUser();
        QuickReply q = editable(id, actual);
        q.setDeletedQuickReply(true);
        repository.save(q);
    }

    private void aplicar(QuickReply q, QuickReplyRequest req, User actual) {
        q.setTitleQuickReply(req.title().trim());
        q.setContentQuickReply(req.content().trim());
        // Un agente no puede compartir: se ignora lo que mande.
        q.setSharedQuickReply(esAdmin(actual) && Boolean.TRUE.equals(req.shared()));
    }

    private QuickReply editable(UUID id, User actual) {
        QuickReply q = repository.findById(id)
                .filter(r -> !Boolean.TRUE.equals(r.getDeletedQuickReply()))
                .orElseThrow(() -> new ResourceNotFoundException("Respuesta rápida", "id", id));
        if (!puedeEditar(q, actual)) {
            throw new AccessDeniedException("Solo puedes cambiar tus propias respuestas rápidas");
        }
        return q;
    }

    private boolean puedeEditar(QuickReply q, User actual) {
        return q.getOwnerQuickReply().getIdUser().equals(actual.getIdUser()) || esAdmin(actual);
    }

    private static boolean esAdmin(User u) {
        String rol = u.getRolUser() == null ? "" : u.getRolUser().getNameRole();
        return "ADMIN".equals(rol) || "SUPER_ADMIN".equals(rol);
    }

    private QuickReplyResponse respuesta(QuickReply q, User actual) {
        User duenio = q.getOwnerQuickReply();
        return new QuickReplyResponse(
                q.getIdQuickReply(),
                q.getTitleQuickReply(),
                q.getContentQuickReply(),
                Boolean.TRUE.equals(q.getSharedQuickReply()),
                duenio.getIdUser().equals(actual.getIdUser()),
                puedeEditar(q, actual),
                NombreVisible.de(duenio));
    }
}
