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

/**
 * Crea la unica cuenta inicial, el SUPER_ADMIN, al arrancar. El resto de
 * cuentas (administradores, agentes, jefes de area...) las crea el SUPER_ADMIN
 * desde la aplicacion.
 * <p>
 * Antes estas cuentas vivian en data.sql con la contrasena {@code Default123!}
 * escrita en el repositorio, lo que hacia que cualquiera con acceso al codigo
 * (o a un clon del proyecto) pudiera entrar como SUPER_ADMIN en cualquier
 * instalacion. Aqui la clave llega desde {@code ADMIN_SEED_PASSWORD} y, si no
 * viene definida, no se crea ninguna cuenta administrativa.
 * <p>
 * La cuenta queda con {@code mustChangePassword = TRUE}: el filtro de
 * seguridad impide usarlas para nada mas que cambiar la clave.
 */
@Component
@ConditionalOnProperty(name = "app.admin-seed.password")
public class AdminSeedInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedInitializer.class);

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
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

    public AdminSeedInitializer(UserRepository userRepository,
                                RoleRepository roleRepository,
                                AreaRepository areaRepository,
                                SupportTeamRepository supportTeamRepository,
                                TeamUserRepository teamUserRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.admin-seed.password}") String seedPassword,
                                @Value("${app.admin-seed.superadmin-email:superadmin@coacmagisterio.fin.ec}") String superAdminEmail) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.areaRepository = areaRepository;
        this.supportTeamRepository = supportTeamRepository;
        this.teamUserRepository = teamUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
        this.superAdminEmail = superAdminEmail;
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
        Area tiArea = areaRepository.findByNameArea(TI_AREA)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el area " + TI_AREA + " en la base. Revisa db/migration/R__datos_base.sql."));

        if (!crearSiFalta(superAdminEmail, "1000000001", "0990000001", superAdminRole, tiArea)) {
            log.info("Ya existia la cuenta {}: no se modifica.", superAdminEmail);
            return;
        }

        meterEnEquipoTi();

        log.warn("Cuenta inicial creada: {} (SUPER_ADMIN). Debe cambiar su contrasena en el primer login "
                + "y desde ahi crear el resto de cuentas.", superAdminEmail);
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
            userRepository.findByCorporateEmailUser(superAdminEmail).ifPresent(user -> {
                if (!teamUserRepository.existsByTeamIdTeamAndUserIdUser(team.getIdTeam(), user.getIdUser())) {
                    TeamUser link = new TeamUser();
                    link.setTeam(team);
                    link.setUser(user);
                    link.setActiveTeamUser(true);
                    teamUserRepository.save(link);
                }
            });
        });
    }
}
