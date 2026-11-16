package com.mesaayuda.activos;

import com.mesaayuda.entity.Asset;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.AssetRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.service.NotificationService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Cada día a las 08:00 avisa de las garantías que vencen en los próximos
 * "días de aviso" (configuración de activos) a los jefes de las áreas que
 * gestionan activos. Cada activo se avisa una vez por fecha de garantía.
 */
@Component
public class GarantiaScheduler {

    private static final Logger log = LoggerFactory.getLogger(GarantiaScheduler.class);

    private final AssetRepository activos;
    private final UserRepository usuarios;
    private final NumeracionActivos numeracion;
    private final PermisosActivos permisos;
    private final NotificationService notificaciones;

    public GarantiaScheduler(AssetRepository activos, UserRepository usuarios, NumeracionActivos numeracion,
                             PermisosActivos permisos, NotificationService notificaciones) {
        this.activos = activos;
        this.usuarios = usuarios;
        this.numeracion = numeracion;
        this.permisos = permisos;
        this.notificaciones = notificaciones;
    }

    @Scheduled(cron = "${app.scheduler.garantias.cron:0 0 8 * * *}", zone = "America/Guayaquil")
    @SchedulerLock(name = "avisoGarantias", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void avisar() {
        try {
            int n = avisarPorVencer(OffsetDateTime.now());
            if (n > 0) log.info("Avisos de garantía por vencer: {}", n);
        } catch (Exception e) {
            log.error("Error avisando garantías por vencer", e);
        }
    }

    public int avisarPorVencer(OffsetDateTime ahora) {
        int dias = numeracion.config().getDiasAvisoGarantia() == null ? 30 : numeracion.config().getDiasAvisoGarantia();
        List<Asset> porVencer = activos.garantiasPorAvisar(ahora.plusDays(dias));
        if (porVencer.isEmpty()) return 0;
        List<User> jefes = usuarios.findByStatusUserAndDeletedUserFalse("ACTIVE").stream()
                .filter(u -> u.getRolUser() != null && "AREA_MANAGER".equals(u.getRolUser().getNameRole()))
                .filter(permisos::gestiona)
                .toList();
        StringBuilder lista = new StringBuilder("<ul>");
        for (Asset a : porVencer) {
            lista.append("<li><b>").append(esc(a.getCodeAsset())).append("</b> ").append(esc(a.getNameAsset()))
                    .append(" — vence el ").append(a.getWarrantyUntilAsset().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    .append("</li>");
            a.setGarantiaAvisadaAsset(ahora);
        }
        lista.append("</ul>");
        activos.saveAll(porVencer);
        for (User j : jefes) {
            notificaciones.notifyConEnlace(j, "GARANTIA_POR_VENCER",
                    porVencer.size() == 1 ? "Garantía por vencer: " + porVencer.get(0).getCodeAsset()
                            : porVencer.size() + " garantías por vencer",
                    "<p>Estas garantías vencen en los próximos " + dias + " días:</p>" + lista, "activos");
        }
        return porVencer.size();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
