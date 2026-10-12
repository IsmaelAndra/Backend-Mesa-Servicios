package com.mesaayuda.config;

import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.TeamUser;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.RoleRepository;
import com.mesaayuda.repository.SupportTeamRepository;
import com.mesaayuda.repository.TeamUserRepository;
import com.mesaayuda.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Crea las cuentas administrativas iniciales al arrancar.
 * <p>
 * Antes estas cuentas vivian en data.sql con la contrasena {@code Default123!}
 * escrita en el repositorio, lo que hacia que cualquiera con acceso al codigo
 * (o a un clon del proyecto) pudiera entrar como SUPER_ADMIN en cualquier
 * instalacion. Aqui la clave llega desde {@code ADMIN_SEED_PASSWORD} y, si no
 * viene definida, no se crea ninguna cuenta administrativa.
 * <p>
 * Las cuentas quedan con {@code mustChangePassword = TRUE}: el filtro de
 * seguridad impide usarlas para nada mas que cambiar la clave.
 */
@Component
@ConditionalOnProperty(name = "app.admin-seed.password")
public class AdminSeedInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedInitializer.class);

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String TI_AREA = "TI";
    private static final String TI_TEAM = "Equipo TI";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AreaRepository areaRepository;
    private final SupportTeamRepository supportTeamRepository;
    private final TeamUserRepository teamUserRepository;
    private final PasswordEncoder passwordEncoder;

    private final String seedPassword;
    private final String superAdminEmail;
    private final String adminEmail;

    public AdminSeedInitializer(UserRepository userRepository,
                                RoleRepository roleRepository,
                                AreaRepository areaRepository,
                                SupportTeamRepository supportTeamRepository,
                                TeamUserRepository teamUserRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.admin-seed.password}") String seedPassword,
                                @Value("${app.admin-seed.superadmin-email:superadmin@coacmagisterio.fin.ec}") String superAdminEmail,
                                @Value("${app.admin-seed.admin-email:admin@coacmagisterio.fin.ec}") String adminEmail) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.areaRepository = areaRepository;
        this.supportTeamRepository = supportTeamRepository;
        this.teamUserRepository = teamUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
        this.superAdminEmail = superAdminEmail;
        this.adminEmail = adminEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(seedPassword)) {
            log.info("ADMIN_SEED_PASSWORD vacia: no se crea ninguna cuenta administrativa. "
                    + "Define la variable para sembrar el primer SUPER_ADMIN.");
            return;
        }

        Role superAdminRole = roleRepository.findByNameRole(SUPER_ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el rol " + SUPER_ADMIN_ROLE + " en la base. Revisa db/migration/R__datos_base.sql."));
        Role adminRole = roleRepository.findByNameRole(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el rol " + ADMIN_ROLE + " en la base. Revisa db/migration/R__datos_base.sql."));
        Area tiArea = areaRepository.findByNameArea(TI_AREA)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el area " + TI_AREA + " en la base. Revisa db/migration/R__datos_base.sql."));

        var creados = new java.util.ArrayList<String>();
        if (crearSiFalta(superAdminEmail, "1000000001", "0990000001", superAdminRole, tiArea)) {
            creados.add(superAdminEmail + " (SUPER_ADMIN)");
        }
        if (crearSiFalta(adminEmail, "1000000002", "0990000002", adminRole, tiArea)) {
            creados.add(adminEmail + " (ADMIN)");
        }

        if (creados.isEmpty()) {
            log.info("Ya existian las cuentas administrativas: no se modifica ninguna.");
            return;
        }

        meterEnEquipoTi();

        log.warn("Cuentas administrativas iniciales creadas: {}. "
                + "Cada una debe cambiar su contrasena en el primer login.", String.join(", ", creados));
    }

    private boolean crearSiFalta(String email, String identification, String phone, Role role, Area area) {
        if (userRepository.existsByCorporateEmailUser(email)) {
            return false;
        }
        User user = new User();
        user.setIdentificationUser(identification);
        user.setCorporateEmailUser(email);
        user.setPasswordUser(passwordEncoder.encode(seedPassword));
        user.setPhoneUser(phone);
        user.setStatusUser("ACTIVE");
        user.setRolUser(role);
        user.setArea(area);
        user.setMustChangePassword(true);
        user.setDeletedUser(false);
        userRepository.save(user);
        return true;
    }

    private void meterEnEquipoTi() {
        supportTeamRepository.findByNameTeam(TI_TEAM).ifPresent(team -> {
            List<String> emails = List.of(superAdminEmail, adminEmail);
            for (String email : emails) {
                userRepository.findByCorporateEmailUser(email).ifPresent(user -> {
                    if (!teamUserRepository.existsByTeamIdTeamAndUserIdUser(team.getIdTeam(), user.getIdUser())) {
                        TeamUser link = new TeamUser();
                        link.setTeam(team);
                        link.setUser(user);
                        link.setActiveTeamUser(true);
                        teamUserRepository.save(link);
                    }
                });
            }
        });
    }
}
