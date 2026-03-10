package com.pos.system.service;

import com.pos.system.dto.user.CreateUserRequest;
import com.pos.system.dto.user.UpdateUserRequest;
import com.pos.system.dto.user.UserPermissionResponse;
import com.pos.system.model.auth.*;
import com.pos.system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AuthorizationRepository authorizationRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;

    public String create(CreateUserRequest request) {
        if (authorizationRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && authorizationRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        AuthorizationType type = request.getType() == null
                ? AuthorizationType.CASHIER
                : request.getType();

        if (type == AuthorizationType.SUPERADMIN) {
            if (request.getBranchId() != null) {
                throw new RuntimeException("SUPERADMIN should not have a branch");
            }
        } else {
            if (request.getBranchId() == null) {
                throw new RuntimeException("Branch is required for non-superadmin users");
            }
            if (!branchRepository.existsById(request.getBranchId())) {
                throw new RuntimeException("Branch not found");
            }
        }

        Authorization auth = new Authorization();
        auth.setUsername(request.getUsername());
        auth.setEmail(request.getEmail());
        auth.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        auth.setType(type);
        auth.setStatus(AuthorizationStatus.ACTIVE);

        Authorization savedAuth = authorizationRepository.save(auth);

        User user = new User();
        user.setAuthId(savedAuth.getAuthId());
        user.setBranchId(type == AuthorizationType.SUPERADMIN ? null : request.getBranchId());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setIsActive(true);
        userRepository.save(user);

        return "User created successfully";
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Authorization auth = authorizationRepository.findById(user.getAuthId())
                .orElseThrow(() -> new RuntimeException("Authorization not found"));

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            Optional<Authorization> existingEmail = authorizationRepository.findByEmail(request.getEmail());
            if (existingEmail.isPresent() && !existingEmail.get().getAuthId().equals(auth.getAuthId())) {
                throw new RuntimeException("Email already exists");
            }
            auth.setEmail(request.getEmail());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            auth.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        AuthorizationType newType = request.getType() == null ? auth.getType() : request.getType();

        if (newType == AuthorizationType.SUPERADMIN) {
            if (request.getBranchId() != null) {
                throw new RuntimeException("SUPERADMIN should not have a branch");
            }
            user.setBranchId(null);
        } else {
            Long branchId = request.getBranchId() == null ? user.getBranchId() : request.getBranchId();

            if (branchId == null) {
                throw new RuntimeException("Branch is required for non-superadmin users");
            }

            if (!branchRepository.existsById(branchId)) {
                throw new RuntimeException("Branch not found");
            }

            user.setBranchId(branchId);
        }

        auth.setType(newType);

        if (request.getStatus() != null) {
            auth.setStatus(request.getStatus());
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }

        authorizationRepository.save(auth);
        return userRepository.save(user);
    }

    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Authorization auth = authorizationRepository.findById(user.getAuthId())
                .orElseThrow(() -> new RuntimeException("Authorization not found"));

        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (!userRoles.isEmpty()) {
            userRoleRepository.deleteAll(userRoles);
        }

        userRepository.delete(user);
        authorizationRepository.delete(auth);
    }

    public String assignRole(Long userId, Long roleId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            throw new RuntimeException("Role already assigned to user");
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRoleRepository.save(userRole);

        return "Role assigned to user successfully";
    }

    public String removeRole(Long userId, Long roleId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        UserRole userRole = userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                .orElseThrow(() -> new RuntimeException("Role is not assigned to user"));

        userRoleRepository.delete(userRole);

        return "Role removed from user successfully";
    }

    public List<Role> getUserRoles(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        List<Role> roles = new ArrayList<>();

        for (UserRole ur : userRoles) {
            roleRepository.findById(ur.getRoleId()).ifPresent(roles::add);
        }

        return roles;
    }

    public List<UserPermissionResponse> getUserPermissions(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Authorization auth = authorizationRepository.findById(user.getAuthId())
                .orElseThrow(() -> new RuntimeException("Authorization not found"));

        if (auth.getType() == AuthorizationType.SUPERADMIN) {
            return List.of(new UserPermissionResponse(
                    0L,
                    "ALL_PRIVILEGES",
                    "Superadmin full system access",
                    "SYSTEM"
            ));
        }

        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);

        Set<Long> permissionIds = new HashSet<>();
        List<UserPermissionResponse> result = new ArrayList<>();

        for (UserRole ur : userRoles) {
            List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleId(ur.getRoleId());
            for (RolePermission rp : rolePermissions) {
                permissionIds.add(rp.getPermissionId());
            }
        }

        for (Long permissionId : permissionIds) {
            Permission permission = permissionRepository.findById(permissionId)
                    .orElseThrow(() -> new RuntimeException("Permission not found"));

            result.add(new UserPermissionResponse(
                    permission.getPermissionId(),
                    permission.getCode(),
                    permission.getDescription(),
                    permission.getModule()
            ));
        }

        return result;
    }
}