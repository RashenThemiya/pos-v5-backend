package com.pos.system.service;

import com.pos.system.dto.user.BranchUserPermissionResponse;
import com.pos.system.dto.user.CreateUserRequest;
import com.pos.system.dto.user.UpdateUserRequest;
import com.pos.system.dto.user.UserPermissionResponse;
import com.pos.system.dto.user.UserResponseDto;
import com.pos.system.model.auth.*;
import com.pos.system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

        User user = new User();
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setIsActive(true);

        if (request.getBranchId() != null) {
            if (!branchRepository.existsById(request.getBranchId())) {
                throw new RuntimeException("Branch not found");
            }
            user.setBranchId(request.getBranchId());
        }

        boolean createAuth =
                request.getUsername() != null && !request.getUsername().isBlank()
                        && request.getPassword() != null && !request.getPassword().isBlank();

        if (createAuth) {
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
                user.setBranchId(null);
            } else {
                if (user.getBranchId() == null) {
                    throw new RuntimeException("Branch is required for non-superadmin login users");
                }
            }

            Authorization auth = new Authorization();
            auth.setUsername(request.getUsername());
            auth.setEmail(request.getEmail());
            auth.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            auth.setType(type);
            auth.setStatus(AuthorizationStatus.ACTIVE);

            Authorization savedAuth = authorizationRepository.save(auth);
            user.setAuthId(savedAuth.getAuthId());
        }

        userRepository.save(user);
        return "User created successfully";
    }

    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::mapUserWithOptionalAuth)
                .toList();
    }

    public UserResponseDto getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return mapUserWithOptionalAuth(user);
    }

    public User updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Authorization auth = getAuthOrNull(user);

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }

        if (request.getBranchId() != null) {
            if (!branchRepository.existsById(request.getBranchId())) {
                throw new RuntimeException("Branch not found");
            }
            user.setBranchId(request.getBranchId());
        }

        if (auth != null) {
            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                Optional<Authorization> existingEmail = authorizationRepository.findByEmail(request.getEmail());

                if (existingEmail.isPresent()
                        && !existingEmail.get().getAuthId().equals(auth.getAuthId())) {
                    throw new RuntimeException("Email already exists");
                }

                auth.setEmail(request.getEmail());
            }

            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                auth.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            }

            if (request.getType() != null) {
                auth.setType(request.getType());

                if (request.getType() == AuthorizationType.SUPERADMIN) {
                    user.setBranchId(null);
                } else if (user.getBranchId() == null) {
                    throw new RuntimeException("Branch is required for non-superadmin login users");
                }
            }

            if (request.getStatus() != null) {
                auth.setStatus(request.getStatus());
            }

            authorizationRepository.save(auth);
        }

        return userRepository.save(user);
    }

    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (!userRoles.isEmpty()) {
            userRoleRepository.deleteAll(userRoles);
        }

        userRepository.delete(user);
    }

    public String assignAuthToUser(Long userId, Long authId) {
        if (authId == null) {
            throw new RuntimeException("authId is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Authorization auth = authorizationRepository.findById(authId)
                .orElseThrow(() -> new RuntimeException("Authorization not found"));

        Optional<User> usedUser = userRepository.findByAuthId(authId);
        if (usedUser.isPresent() && !usedUser.get().getUserId().equals(userId)) {
            throw new RuntimeException("This authorization is already assigned to another user");
        }

        if (auth.getType() == AuthorizationType.SUPERADMIN) {
            user.setBranchId(null);
        } else if (user.getBranchId() == null) {
            throw new RuntimeException("Branch is required before assigning non-superadmin auth");
        }

        user.setAuthId(authId);
        userRepository.save(user);

        return "Authorization assigned to user successfully";
    }

    public String deassignAuthFromUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getAuthId() == null) {
            throw new RuntimeException("User has no authorization assigned");
        }

        user.setAuthId(null);

        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (!userRoles.isEmpty()) {
            userRoleRepository.deleteAll(userRoles);
        }

        userRepository.save(user);

        return "Authorization deassigned successfully";
    }

    public String assignRole(Long userId, Long roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getAuthId() == null) {
            throw new RuntimeException("Cannot assign role. User has no authorization account");
        }

        roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        List<UserRole> existingRoles = userRoleRepository.findByUserId(userId);
        if (!existingRoles.isEmpty()) {
            userRoleRepository.deleteAll(existingRoles);
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRole.setCreatedAt(LocalDateTime.now());

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

        for (UserRole userRole : userRoles) {
            roleRepository.findById(userRole.getRoleId()).ifPresent(roles::add);
        }

        return roles;
    }

    public List<UserPermissionResponse> getUserPermissions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Authorization auth = getAuthOrNull(user);

        if (auth == null) {
            return List.of();
        }

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

        for (UserRole userRole : userRoles) {
            List<RolePermission> rolePermissions =
                    rolePermissionRepository.findByRoleId(userRole.getRoleId());

            for (RolePermission rolePermission : rolePermissions) {
                permissionIds.add(rolePermission.getPermissionId());
            }
        }

        List<UserPermissionResponse> result = new ArrayList<>();

        for (Long permissionId : permissionIds) {
            permissionRepository.findById(permissionId).ifPresent(permission -> {
                result.add(new UserPermissionResponse(
                        permission.getPermissionId(),
                        permission.getCode(),
                        permission.getDescription(),
                        permission.getModule()
                ));
            });
        }

        return result;
    }

    private UserResponseDto mapUserWithOptionalAuth(User user) {
        Authorization auth = getAuthOrNull(user);

        List<String> roles = new ArrayList<>();

        if (user.getAuthId() != null) {
            roles = userRoleRepository.findByUserId(user.getUserId())
                    .stream()
                    .map(userRole -> roleRepository.findById(userRole.getRoleId()).orElse(null))
                    .filter(Objects::nonNull)
                    .map(Role::getName)
                    .toList();
        }

        return UserResponseDto.builder()
                .userId(user.getUserId())
                .authId(user.getAuthId())
                .branchId(user.getBranchId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .isActive(user.getIsActive())

                .username(auth != null ? auth.getUsername() : null)
                .email(auth != null ? auth.getEmail() : null)
                .type(auth != null ? auth.getType() : null)
                .status(auth != null ? auth.getStatus() : null)
                .lastLoginAt(auth != null && auth.getLastLoginAt() != null
                        ? auth.getLastLoginAt().toString()
                        : null)

                .roles(roles)
                .build();
    }

    private Authorization getAuthOrNull(User user) {
        if (user.getAuthId() == null) {
            return null;
        }

        return authorizationRepository.findById(user.getAuthId()).orElse(null);
    }
    public List<UserResponseDto> getUsersByBranch(Long branchId) {
    if (!branchRepository.existsById(branchId)) {
        throw new RuntimeException("Branch not found");
    }

    return userRepository.findByBranchId(branchId)
            .stream()
            .map(this::mapUserWithOptionalAuth)
            .toList();
}

public List<BranchUserPermissionResponse> getBranchUserPermissions(Long branchId) {
    if (!branchRepository.existsById(branchId)) {
        throw new RuntimeException("Branch not found");
    }

    return userRepository.findByBranchId(branchId)
            .stream()
            .map(user -> {
                Authorization auth = getAuthOrNull(user);

                List<String> roles = getUserRoleNames(user.getUserId());

                List<UserPermissionResponse> permissions =
                        auth == null ? List.of() : getUserPermissions(user.getUserId());

                return new BranchUserPermissionResponse(
                        user.getUserId(),
                        user.getFullName(),
                        user.getAuthId(),
                        auth != null ? auth.getUsername() : null,
                        roles,
                        permissions
                );
            })
            .toList();
}

private List<String> getUserRoleNames(Long userId) {
    return userRoleRepository.findByUserId(userId)
            .stream()
            .map(userRole -> roleRepository.findById(userRole.getRoleId()).orElse(null))
            .filter(Objects::nonNull)
            .map(Role::getName)
            .toList();
}
}