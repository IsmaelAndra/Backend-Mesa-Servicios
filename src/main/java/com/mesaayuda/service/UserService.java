package com.mesaayuda.service;

import com.mesaayuda.dto.RegisterRequest;
import com.mesaayuda.dto.UserResponse;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.mapper.UserMapper;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.RoleRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.util.PasswordGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class UserService {

    private static final long TEMPORAL_PASSWORD_MINUTES = 24 * 60;
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SystemAuditService systemAuditService;

    @Transactional(readOnly = true)
    public List<UserResponse> findAll(String role, String status, String search) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deletedUser")));
            if (role != null && !role.isBlank()) {
                predicates.add(cb.equal(root.get("rolUser").get("nameRole"), role.toUpperCase()));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("statusUser"), status.toUpperCase()));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("corporateEmailUser")), "%" + search.toLowerCase() + "%"),
                        cb.like(root.get("identificationUser"), "%" + search + "%")
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return userRepository.findAll(spec).stream().map(userMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userMapper.toResponse(getUser(id));
    }

    public UserResponse create(RegisterRequest request) {
        if (userRepository.existsByCorporateEmailUser(request.corporateEmail())) {
            throw new BadRequestException("El correo corporativo ya está registrado");
        }
        if (userRepository.existsByIdentificationUser(request.identification())) {
            throw new BadRequestException("La identificación ya está registrada");
        }

        Role role = roleRepository.findByNameRole(request.roleName().toUpperCase())
                .orElseThrow(() -> new BadRequestException("Rol inválido: " + request.roleName()));
        Area area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new BadRequestException("Área inválida"));

        String providedPassword = request.password() == null ? "" : request.password().trim();
        String generatedPassword = providedPassword.isBlank() ? PasswordGenerator.generate() : providedPassword;

        User user = new User();
        user.setIdentificationUser(request.identification());
        user.setCorporateEmailUser(request.corporateEmail());
        user.setPasswordUser(passwordEncoder.encode(generatedPassword));
        user.setPhoneUser(request.phone());
        user.setStatusUser("ACTIVE");
        user.setRolUser(role);
        user.setArea(area);
        user.setDeletedUser(false);
        user.setMustChangePassword(true);
        user.setTemporalPasswordExpiresAt(OffsetDateTime.now().plusMinutes(TEMPORAL_PASSWORD_MINUTES));
        User saved = userRepository.save(user);

        systemAuditService.record("USER_CREATE", "User", saved.getIdUser());
        notificationService.notifyNewAccount(saved, generatedPassword);
        log.info("Usuario creado por administración: {}", saved.getCorporateEmailUser());

        return userMapper.toResponse(saved);
    }

    public UserResponse update(Long id, RegisterRequest request) {
        User user = getUser(id);

        if (!user.getCorporateEmailUser().equalsIgnoreCase(request.corporateEmail())
                && userRepository.existsByCorporateEmailUser(request.corporateEmail())) {
            throw new BadRequestException("El correo corporativo ya está registrado");
        }
        if (!user.getIdentificationUser().equalsIgnoreCase(request.identification())
                && userRepository.existsByIdentificationUser(request.identification())) {
            throw new BadRequestException("La identificación ya está registrada");
        }

        Role role = roleRepository.findByNameRole(request.roleName().toUpperCase())
                .orElseThrow(() -> new BadRequestException("Rol inválido"));
        Area area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new BadRequestException("Área inválida"));

        user.setIdentificationUser(request.identification());
        user.setCorporateEmailUser(request.corporateEmail());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordUser(passwordEncoder.encode(request.password()));
            user.setMustChangePassword(true);
            user.setTemporalPasswordExpiresAt(OffsetDateTime.now().plusMinutes(TEMPORAL_PASSWORD_MINUTES));
        }
        user.setPhoneUser(request.phone());
        user.setRolUser(role);
        user.setArea(area);
        systemAuditService.record("USER_UPDATE", "User", user.getIdUser());
        return userMapper.toResponse(userRepository.save(user));
    }

    public void delete(Long id) {
        User user = getUser(id);
        user.setDeletedUser(true);
        user.setStatusUser("INACTIVE");
        userRepository.save(user);
        systemAuditService.record("USER_DELETE", "User", user.getIdUser());
    }

    public UserResponse changeStatus(Long id, String status) {
        User user = getUser(id);
        user.setStatusUser(status);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new BadRequestException("No autenticado");
        }
        return userRepository.findByCorporateEmailUser(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "correo", auth.getName()));
    }

    @Transactional(readOnly = true)
    public boolean hasRole(String roleName) {
        return roleName.equalsIgnoreCase(getCurrentUser().getRolUser().getNameRole());
    }

    @Transactional(readOnly = true)
    public List<String> findAgentEmails() {
        return userRepository.findByRoleName("AGENT").stream()
                .map(User::getCorporateEmailUser)
                .toList();
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .filter(u -> !u.getDeletedUser())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "id", id));
    }
}