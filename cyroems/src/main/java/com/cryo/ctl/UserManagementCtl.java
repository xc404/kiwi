package com.cryo.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.cryo.dao.GroupRepository;
import com.cryo.dao.RoleRepository;
import com.cryo.dao.TaskRepository;
import com.cryo.dao.UserRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.Microscope;
import com.cryo.model.MicroscopeConfig;
import com.cryo.model.Task;
import com.cryo.model.user.Group;
import com.cryo.model.user.Role;
import com.cryo.model.user.User;
import com.cryo.model.user.UserDetail;
import com.cryo.model.Project;
import com.cryo.service.MicroscopeService;
import com.cryo.service.ProjectService;
import com.cryo.service.UserService;
import com.cryo.service.session.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/management")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class UserManagementCtl {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final RoleRepository roleRepository;
    private final TaskRepository taskRepository;
    private final TaskDataSetRepository taskDataSetRepository;
    private final SessionService sessionService;
    private final MicroscopeService microscopeService;
    private final UserService userService;
    private final ProjectService projectService;

    // ==================== Super Admin 接口 ====================

    /**
     * 查询所有用户，支持按 role_id 过滤，返回关联了 Role 和 Group 完整信息的 UserDetail
     * 仅 super_admin 可访问
     */
    @GetMapping("/users")
    public ManagementAPIResult<UserDetail> listUsers(@RequestParam(required = false) String role_id, Pageable pageable) {
        requireRole(List.of(Role.SUPER_ADMIN));
        Query query = new Query();
        if (role_id != null) {
            query.addCriteria(Criteria.where("role_id").is(role_id));
        }
        Page<User> users = userRepository.findByQuery(query, pageable);

        // 批量加载 Role 和 Group，避免 N+1
        List<String> groupIds = users.stream().map(User::getGroup_id).filter(java.util.Objects::nonNull).toList();
        Map<String, Role> roleMap = roleRepository.findAll().stream()
                .collect(Collectors.toMap(Role::getId, r -> r));
        Map<String, Group> groupMap = groupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(com.cryo.common.model.IdEntity::getId, g -> g));

        return ManagementAPIResult.of(users.map(u -> new UserDetail(u, roleMap.get(u.getRole_id()), groupMap.get(u.getGroup_id()))));
    }

    /**
     * 按 role_id 筛选用户，返回带 Role / Group 完整信息的 UserDetail 分页列表
     * super_admin / admin 可访问
     */
    @GetMapping("/users/filter")
    public ManagementAPIResult<UserDetail> filterUsersByRole(@RequestParam int role_id, Pageable pageable) {
        requireRole(List.of(Role.SUPER_ADMIN, Role.ADMIN));

        Page<User> users = userService.findByRoleId(role_id, pageable);

        List<String> groupIds = users.stream().map(User::getGroup_id).filter(Objects::nonNull).toList();
        Map<Integer, Role> roleMap = roleRepository.findAll().stream()
                .collect(Collectors.toMap(Role::getRole_id, r -> r));
        Map<String, Group> groupMap = groupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(com.cryo.common.model.IdEntity::getId, g -> g));

        return ManagementAPIResult.of(users.map(u ->
                new UserDetail(u, roleMap.get(u.getRole_id()), groupMap.get(u.getGroup_id()))));
    }

    /**
     * 查询所有组及其成员，按组聚合（groupId → GroupMembersResult）
     * 仅 super_admin 可访问
     */
    @GetMapping("/users/grouped")
    public Map<String, GroupMembersResult> listUsersGrouped() {
        requireRole(List.of(Role.SUPER_ADMIN));
        List<Group> allGroups = groupRepository.findAll();

        // 批量加载所有组涉及的 user
        Set<String> allMemberIds = allGroups.stream()
                .filter(g -> g.getMembers() != null)
                .flatMap(g -> g.getMembers().keySet().stream())
                .collect(Collectors.toSet());
        Map<String, User> userMap = allMemberIds.isEmpty() ? Map.of()
                : userRepository.findByQuery(Query.query(Criteria.where("_id").in(allMemberIds)))
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Integer, Role> roleMap = roleRepository.findAll().stream()
                .collect(Collectors.toMap(Role::getRole_id, r -> r));

        Map<String, GroupMembersResult> result = new LinkedHashMap<>();
        for (Group group : allGroups) {
            List<MemberInfo> members = new ArrayList<>();
            group.getMembers().forEach((userId, member) -> {
                User user = userMap.get(userId);
                if (user == null) return;
                Role role = roleMap.get(user.getRole_id());
                me.zhyd.oauth.model.AuthUser itechUser = user.getOAuthUser(com.cryo.oauth.service.OAuthPlatform.Itech);
                MemberInfo info = new MemberInfo();
                info.setEmail(user.getEmail());
                info.setName(user.getName());
                info.setSysUsername(user.getSys_username());
                info.setOauthUsername(itechUser != null ? itechUser.getUsername() : user.getSys_username());
                info.setDisplayRole(role != null ? role.getDisplay_name() : null);
                info.setGroupName(group.getGroup_name());
                info.setJoinDate(member != null ? member.getJoin_date() : null);
                info.setExitDate(member != null ? member.getExit_date() : null);
                members.add(info);
            });
            result.put(group.getId(), new GroupMembersResult(group.getId(), group.getGroup_name(), members));
        }
        return result;
    }

    // ==================== 数据库迁移接口 ====================

    @PostMapping("/migration/user-role")
    @Operation(summary = "Should only operated once to migrate user.role to user.role_id, requires super_admin role")
    public MigrationResult migrateUserRole() {
        requireRole(List.of(Role.SUPER_ADMIN, Role.ADMIN));
        List<User> allUsers = userRepository.findAll();
        Map<String, Role> roleByName = roleRepository.findAll().stream()
                .collect(Collectors.toMap(Role::getRole_name, r -> r, (a, b) -> a));
        int updated = 0, skipped = 0;
        for (User user : allUsers) {
            if (user.getRole_id() > 0 || user.getRole() == null) { skipped++; continue; } //已经assign role_id
            Role role = roleByName.get(user.getRole());
            if (role != null) {
                user.setRole_id(role.getRole_id());
                user.setRole(role.getRole_name());
                userRepository.save(user);
                updated++;
            } else {
                log.warn("[migration] no role found for role_name={} userId={}", user.getRole(), user.getId());
                skipped++;
            }
        }
        log.info("[migration] role done. updated={} skipped={}", updated, skipped);
        return new MigrationResult(allUsers.size(), updated, skipped);
    }

    @PostMapping("/migration/user-group")
    @Operation(summary = "Should only operated once to map user.group to group_id, requires super_admin role")
    public MigrationResult migrateUserGroup() {
        requireRole(List.of(Role.SUPER_ADMIN, Role.ADMIN));
        List<User> allUsers = userRepository.findAll();

        // 初始加载所有 group，遇到缺失时新建并刷新
        Map<String, Group> groupByName = new java.util.HashMap<>(groupRepository.findAll().stream()
                .filter(g -> g.getGroup_name() != null)
                .collect(Collectors.toMap(Group::getGroup_name, g -> g, (a, b) -> a)));

        // 确保 admin_group 存在
        Group adminGroup = groupByName.computeIfAbsent("admin_group", name -> {
            Group g = new Group();
            g.setGroup_name(name);
            Group saved = groupRepository.save(g);
            log.info("[migration] created admin_group");
            return saved;
        });
        groupByName.put(adminGroup.getGroup_name(), adminGroup);

        int updated = 0, skipped = 0;
        for (User user : allUsers) {
            if (user.getUser_group() == null) { skipped++; continue; }

            // 找不到 group 则新建
            Group group = groupByName.get(user.getUser_group());
            if (group == null) {
                group = new Group();
                group.setGroup_name(user.getUser_group());
                group = groupRepository.save(group);
                groupByName.put(group.getGroup_name(), group);
                log.info("[migration] created new group: {}", group.getGroup_name());
            }

            // 更新 user.group_id
            if (user.getGroup_id() == null) {
                user.setGroup_id(group.getId());
            }

            // 将 user 加入 group.members，key 为 user_id，自动去重
            Group.Member member = new Group.Member();
            member.setUser_id(user.getId());
            member.setUser_name(user.getName());
            member.setRole(user.getRole());
            member.setJoin_date(java.time.LocalDate.now().toString());
            group.getMembers().put(user.getId(), member);

            // 如果是 group_admin，追加到 group.group_admin 列表
            if (Role.GROUP_AMDMIN.equals(user.getRole())) {
                if (group.getGroup_admin() == null) group.setGroup_admin(new java.util.ArrayList<>());
                if (!group.getGroup_admin().contains(user.getId())) {
                    group.getGroup_admin().add(user.getId());
                }
            }

            group = groupRepository.save(group);
            groupByName.put(group.getGroup_name(), group);

            // 非普通用户额外加入 admin_group
            if (!Role.NORMAL.equals(user.getRole())) {
                adminGroup = groupByName.get("admin_group");
                adminGroup.getMembers().putIfAbsent(user.getId(), member);
                adminGroup = groupRepository.save(adminGroup);
                groupByName.put(adminGroup.getGroup_name(), adminGroup);
            }

            userRepository.save(user);
            updated++;
        }

        log.info("[migration] group done. updated={} skipped={}", updated, skipped);
        return new MigrationResult(allUsers.size(), updated, skipped);
    }

    @PostMapping("/migration/task-group")
    @Operation(summary = "Should only operated once to map task.group_name to task.group_id, requires super_admin role")
    public MigrationResult migrateTaskGroup() {
        requireRole(List.of(Role.SUPER_ADMIN, Role.ADMIN));
        List<Task> allTasks = taskRepository.findAll();
        Map<String, Group> groupByName = groupRepository.findAll().stream()
                .filter(g -> g.getGroup_name() != null)
                .collect(Collectors.toMap(Group::getGroup_name, g -> g, (a, b) -> a));

        int updated = 0, skipped = 0;
        for (Task task : allTasks) {
            if (task.getGroup_id() != null) { skipped++; continue; }
            String groupName = task.getGroup_name();
            if (groupName == null) { skipped++; continue; }
            Group group = groupByName.get(groupName);
            if (group != null) {
                task.setGroup_id(group.getId());
                taskRepository.save(task);
                updated++;
            } else {
                log.warn("[migration] no group found for group_name={} taskId={}", groupName, task.getId());
                skipped++;
            }
        }
        log.info("[migration] task-group done. updated={} skipped={}", updated, skipped);
        return new MigrationResult(allTasks.size(), updated, skipped);
    }

    /**
     * 修改任意用户的角色
     * 仅 super_admin 可操作
     */
    @PutMapping("/users/{userId}/role")
    public User updateUserRole(@PathVariable String userId, @RequestBody String input) {
        requireRole(List.of(Role.SUPER_ADMIN));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        String previous = user.getRole();
        user.setRole(input);
        // TODO： need change here
        user.setRole_id(4);
        userRepository.save(user);
        log.info("[admin] updated role of user {} from {} to {}", userId, previous, input);
        return user;
    }

    /**
     * 删除用户
     * 仅 super_admin 可操作
     */
    @DeleteMapping("/users/{userId}")
    public void deleteUser(@PathVariable String userId) {
        requireRole(List.of(Role.SUPER_ADMIN));
        userRepository.deleteById(userId);
        log.info("[super_admin] deleted user {}", userId);
    }

    // ==================== Group Admin 接口 ====================

    /**
     * group_admin 专用：查看自己管理的组的所有成员（无需传 groupId，从 session 自动解析）
     */
    @GetMapping("/groups/my/members")
    public ManagementAPIResult<MemberInfo> listMyGroupMembers(Pageable pageable) {
        requireAnyRole(Role.GROUP_AMDMIN);
        User currentUser = sessionService.getSessionUser().getUser();
        Group group = groupRepository.findAll().stream()
                .filter(g -> g.getGroup_admin() != null && g.getGroup_admin().contains(currentUser.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No group found for current group_admin"));
        return buildMemberInfoPage(group, pageable);
    }

    /**
     * 查询指定组内所有成员（admin / super_admin 可查任意组）
     */
    @GetMapping("/groups/{groupId}/members")
    public ManagementAPIResult<MemberInfo> listGroupMembers(@PathVariable String groupId, Pageable pageable) {
        requireAnyRole(Role.SUPER_ADMIN, Role.ADMIN);
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found: " + groupId));
        return buildMemberInfoPage(group, pageable);
    }

    /**
     * 将用户加入组，同步更新 group.members 的 join_date
     * group_admin 只能操作自己管理的组
     */
    @PostMapping("/groups/{groupId}/members")
    public void addGroupMember(@PathVariable String groupId, @RequestBody String userId) {
        requireAnyRole(Role.SUPER_ADMIN, Role.ADMIN, Role.GROUP_AMDMIN);
        Group group = findGroupAndCheckPermission(groupId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        doAddMember(group, user);
    }

    /**
     * 将用户移出组，同步更新 group.members 的 exit_date
     * group_admin 只能操作自己管理的组
     */
    @DeleteMapping("/groups/{groupId}/members/{userId}")
    public void removeGroupMember(@PathVariable String groupId, @PathVariable String userId) {
        requireAnyRole(Role.SUPER_ADMIN, Role.ADMIN, Role.GROUP_AMDMIN);
        Group group = findGroupAndCheckPermission(groupId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        doRemoveMember(group, user);
    }

    /**
     * 将用户从一个组移动到另一个组
     * 权限：super_admin / admin，或者是 fromGroup 或 toGroup 的 group_admin
     */
    @PostMapping("/groups/transfer")
    public void transferGroupMember(@RequestBody TransferMemberRequest req) {
        requireAnyRole(Role.SUPER_ADMIN, Role.ADMIN, Role.GROUP_AMDMIN);

        Group fromGroup = groupRepository.findById(req.getFromGroupId())
                .orElseThrow(() -> new RuntimeException("Group not found: " + req.getFromGroupId()));
        Group toGroup = groupRepository.findById(req.getToGroupId())
                .orElseThrow(() -> new RuntimeException("Group not found: " + req.getToGroupId()));
        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + req.getUserId()));

        // group_admin 必须是 fromGroup 或 toGroup 的管理员之一
        Role currentRole = currentUserRole();
        if (!Role.SUPER_ADMIN.equals(currentRole.getRole_name()) && !Role.ADMIN.equals(currentRole.getRole_name())) {
            String currentUserId = sessionService.getSessionUser().getUser().getId();
            boolean isFromAdmin = fromGroup.getGroup_admin() != null && fromGroup.getGroup_admin().contains(currentUserId);
            boolean isToAdmin   = toGroup.getGroup_admin()   != null && toGroup.getGroup_admin().contains(currentUserId);
            if (!isFromAdmin && !isToAdmin) {
                throw new RuntimeException("Permission denied: you are not the admin of either group");
            }
        }

        doRemoveMember(fromGroup, user);
        doAddMember(toGroup, user);

        log.info("[transfer] user {} moved from group {} to {}", req.getUserId(), fromGroup.getGroup_name(), toGroup.getGroup_name());
    }

    /**
     * 查询组关联的所有项目
     * group_admin 只能查看自己管理的组的项目
     */
    @GetMapping("/groups/{groupId}/tasks")
    public ManagementAPIResult<TaskInfo> listGroupTasks(@PathVariable String groupId, Pageable pageable) {
        requireAnyRole(Role.SUPER_ADMIN, Role.ADMIN, Role.GROUP_AMDMIN);
        Group group = findGroupAndCheckPermission(groupId);

        Page<Task> taskPage = taskRepository.findByQuery(
                Query.query(Criteria.where("group_id").is(group.getId())), pageable);

        List<String> ownerIds = taskPage.stream()
                .map(Task::getOwner).filter(Objects::nonNull).distinct().toList();
        Map<String, User> ownerMap = userRepository.findByQuery(
                Query.query(Criteria.where("_id").in(ownerIds))
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        Page<TaskInfo> result = taskPage.map(t -> {
            User owner = ownerMap.get(t.getOwner());
            TaskInfo info = new TaskInfo();
            info.setTaskId(t.getId());
            info.setTaskName(t.getTask_name());
            info.setOwnerId(t.getOwner());
            info.setOwnerName(owner != null ? owner.getName() : null);
            info.setOwnerEmail(owner != null ? owner.getEmail() : null);
            info.setGroupName(group.getGroup_name());
            info.setMicroscope(t.getMicroscope());
            info.setCreatedAt(t.getCreated_at() != null ? t.getCreated_at().toString() : null);
            info.setStatus(t.getStatus() != null ? t.getStatus().name() : null);
            return info;
        });

        return ManagementAPIResult.of(result);
    }

    /**
     * 查询组内所有成员拥有的数据集（TaskDataset）
     * 取 group.members 的 key 集合作为 ownerIds，查 data 集合中 owner in (ownerIds)
     * super_admin / admin 可查任意组；group_admin 只能查自己管理的组
     */
    @GetMapping("/groups/{groupId}/datasets")
    public ManagementAPIResult<DatasetInfo> listGroupDatasets(@PathVariable String groupId, Pageable pageable) {
        requireAnyRole(Role.SUPER_ADMIN, Role.ADMIN, Role.GROUP_AMDMIN);
        Group group = findGroupAndCheckPermission(groupId);

        Set<String> memberIds = group.getMembers() == null
                ? Set.of()
                : group.getMembers().keySet();

        if (memberIds.isEmpty()) {
            return ManagementAPIResult.of(List.of());
        }

        Page<TaskDataset> datasetPage = taskDataSetRepository.findByQuery(
                Query.query(Criteria.where("owner").in(memberIds))
                        .with(org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.desc("created_at"))),
                pageable);

        // 批量解析 owner 用户信息，避免 N+1
        List<String> ownerIds = datasetPage.stream()
                .map(TaskDataset::getOwner)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, User> ownerMap = ownerIds.isEmpty() ? Map.of()
                : userRepository.findByQuery(Query.query(Criteria.where("_id").in(ownerIds)))
                        .stream().collect(Collectors.toMap(User::getId, u -> u));

        return ManagementAPIResult.of(datasetPage.map(ds -> {
            User owner = ownerMap.get(ds.getOwner());
            DatasetInfo info = new DatasetInfo();
            info.setDatasetId(ds.getId());
            info.setRawPath(ds.getRaw_path());
            info.setMoviePath(ds.getMovie_path());
            info.setMicroscope(ds.getMicroscope());
            info.setMoviesCount(ds.getMovies_count());
            info.setMdocCount(ds.getMdoc_count());
            info.setTomo(ds.getIs_tomo());
            info.setCreatedAt(ds.getCreated_at() != null ? ds.getCreated_at().toString() : null);
            info.setOwnerId(ds.getOwner());
            info.setOwnerName(owner != null ? owner.getName() : null);
            info.setOwnerEmail(owner != null ? owner.getEmail() : null);
            return info;
        }));
    }

    /**
     * 查询组关联的所有项目（Project）
     * super_admin / admin 可查任意组；group_admin 只能查自己管理的组
     */
    @GetMapping("/groups/{groupId}/projects")
    public ManagementAPIResult<Project> listGroupProjects(
            @PathVariable String groupId,
            @RequestParam(required = false, defaultValue = "all") String type,
            Pageable pageable) {
        requireAnyRole(Role.SUPER_ADMIN, Role.ADMIN, Role.GROUP_AMDMIN);
        findGroupAndCheckPermission(groupId);
        return ManagementAPIResult.of(projectService.findByGroupId(groupId, type, pageable));
    }

    // ==================== Device Admin 接口 ====================

    /**
     * 查询所有可用显微镜设备列表，managed_by userId 自动解析为管理员姓名
     */
    @GetMapping("/devices")
    public ManagementAPIResult<DeviceInfo> listDevices() {
        Role r = requireRole(List.of(Role.SUPER_ADMIN, Role.DEVICE_ADMIN));
        List<Microscope> microscopes = switch (r.getRole_name()) {
            case Role.SUPER_ADMIN   -> microscopeService.listAllMicroscopes();
            case Role.DEVICE_ADMIN  -> microscopeService.getMicroscopeByAdmin(
                    sessionService.getSessionUser().getUser().getId());
            default -> throw new RuntimeException("Invalid user role");
        };

        // 批量查询 managed_by 对应的 User，避免 N+1
        List<String> adminIds = microscopes.stream()
                .map(Microscope::getManaged_by)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, User> adminMap = adminIds.isEmpty()
                ? Map.of()
                : userRepository.findByQuery(Query.query(Criteria.where("_id").in(adminIds)))
                        .stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<DeviceInfo> items = microscopes.stream()
                .map(m -> DeviceInfo.from(m, adminMap.get(m.getManaged_by())))
                .toList();

        return ManagementAPIResult.of(items);
    }

    /**
     * 修改设备参数或管理员
     * - 修改参数（display_name / config）：SUPER_ADMIN 可操作任意设备，DEVICE_ADMIN 只能操作自己管理的设备
     * - 修改管理员（managedBy）：仅 SUPER_ADMIN；若目标用户不是 device_admin，自动升级其角色
     */
    @PutMapping("/devices/{microscopeId}")
    public DeviceInfo updateDevice(@PathVariable String microscopeId,
                                   @RequestBody UpdateDeviceRequest req) {
        Role currentRole = requireRole(List.of(Role.SUPER_ADMIN, Role.DEVICE_ADMIN));
        Microscope microscope = microscopeService.findById(microscopeId);

        // DEVICE_ADMIN 只能操作自己管理的设备，且不能变更管理员
        if (Role.DEVICE_ADMIN.equals(currentRole.getRole_name())) {
            String currentUserId = sessionService.getSessionUser().getUser().getId();
            if (!currentUserId.equals(microscope.getManaged_by())) {
                throw new RuntimeException("Permission denied: you are not the admin of this device");
            }
            if (req.getManagedBy() != null) {
                throw new RuntimeException("Permission denied: only super_admin can reassign device admin");
            }
        }

        // 更新基础参数（非 null 才覆盖）
        if (req.getDisplayName() != null) {
            microscope.setDisplay_name(req.getDisplayName());
        }
        if (req.getConfig() != null) {
            microscope.setConfig(req.getConfig());
        }

        // 更新管理员
        User newAdmin = null;
        if (req.getManagedBy() != null) {
            newAdmin = userRepository.findById(req.getManagedBy())
                    .orElseThrow(() -> new RuntimeException("User not found: " + req.getManagedBy()));

            // 若目标用户既不是 device_admin 也不是 super_admin，自动升级角色
            boolean alreadyPrivileged = Role.DEVICE_ADMIN.equals(newAdmin.getRole())
                    || Role.SUPER_ADMIN.equals(newAdmin.getRole());
            if (!alreadyPrivileged) {
                Role deviceAdminRole = roleRepository.findAll().stream()
                        .filter(r -> Role.DEVICE_ADMIN.equals(r.getRole_name()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Role device_admin not found"));
                log.info("[device] upgrading user {} role from {} to device_admin", newAdmin.getId(), newAdmin.getRole());
                newAdmin.setRole(Role.DEVICE_ADMIN);
                newAdmin.setRole_id(deviceAdminRole.getRole_id());
                userRepository.save(newAdmin);
            }

            microscope.setManaged_by(newAdmin.getId());
        }

        microscope = microscopeService.save(microscope);

        // 若未传 managedBy，从 DB 加载现有管理员用于返回
        if (newAdmin == null && microscope.getManaged_by() != null) {
            newAdmin = userRepository.findById(microscope.getManaged_by()).orElse(null);
        }

        return DeviceInfo.from(microscope, newAdmin);
    }

    /**
     * 查询指定显微镜的所有关联项目
     * @param type 可选过滤：past（已完成）/ active（进行中）/ all（默认，全部）
     * device_admin 可以查看所有设备的项目
     */
    @GetMapping("/devices/{microscopeId}/tasks")
    public ManagementAPIResult<TaskInfo> listDeviceTasks(
            @PathVariable String microscopeId,
            @RequestParam(required = false, defaultValue = "all") String type,
            Pageable pageable) {
        requireRole(List.of(Role.SUPER_ADMIN, Role.ADMIN, Role.DEVICE_ADMIN));
        String microscopeKey = microscopeService.keyFromId(microscopeId);

        Page<Task> taskPage = taskRepository.findByQuery(
                Query.query(Criteria.where("microscope").is(microscopeKey)), pageable);

        List<String> ownerIds = taskPage.stream()
                .map(Task::getOwner).filter(Objects::nonNull).distinct().toList();
        Map<String, User> ownerMap = userRepository.findByQuery(
                Query.query(Criteria.where("_id").in(ownerIds))
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        Page<TaskInfo> result = taskPage.map(t -> {
            User owner = ownerMap.get(t.getOwner());
            boolean completed = t.getStatus() != null && t.completed();
            TaskInfo info = new TaskInfo();
            info.setTaskId(t.getId());
            info.setTaskName(t.getTask_name());
            info.setOwnerId(t.getOwner());
            info.setOwnerName(owner != null ? owner.getName() : null);
            info.setOwnerEmail(owner != null ? owner.getEmail() : null);
            info.setMicroscope(microscopeKey);
            info.setCreatedAt(t.getCreated_at() != null ? t.getCreated_at().toString() : null);
            info.setStatus(t.getStatus() != null ? t.getStatus().name() : null);
            info.setCompleted(completed);
            return info;
        });

        // 按 type 过滤
        List<TaskInfo> filtered = switch (type) {
            case "past"   -> result.getContent().stream().filter(TaskInfo::isCompleted).toList();
            case "active" -> result.getContent().stream().filter(t -> !t.isCompleted()).toList();
            default       -> result.getContent();
        };

        return ManagementAPIResult.of(filtered);
    }

    // ==================== 权限校验 ====================

    private Role requireRole(List<String> required) {
        Role current = currentUserRole();
        log.info("current user role: {}", current.getRole_name());
        if (!required.contains(current.getRole_name())) {
            throw new RuntimeException("Permission denied: requires role " + required);
        }
        return current;
    }

    private Role requireAnyRole(String... roles) {
        Role current = currentUserRole();
        for (String r : roles) {
            if (r.equals(current.getRole_name())) return current;
        }
        throw new RuntimeException("Permission denied");
    }

    private Role currentUserRole() {
        int role_id = sessionService.getSessionUser().getUser().getRole_id();
        Optional<Role> r = roleRepository.findByRoleId(role_id);
        if (r.isPresent()) {
            return r.get();
        }
        throw new RuntimeException("Role not found for role_id: " + role_id);
    }

    private ManagementAPIResult<MemberInfo> buildMemberInfoPage(Group group, Pageable pageable) {
        Map<String, Group.Member> memberMap = group.getMembers();
        List<String> memberIds = new ArrayList<>(memberMap.keySet());
        if (memberIds.isEmpty()) return ManagementAPIResult.of(List.of());

        Page<User> userPage = userRepository.findByQuery(
                Query.query(Criteria.where("_id").in(memberIds)), pageable);
        Map<Integer, Role> roleMap = roleRepository.findAll().stream()
                .collect(Collectors.toMap(Role::getRole_id, r -> r));

        return ManagementAPIResult.of(userPage.map(u -> {
            Group.Member member = memberMap.get(u.getId());
            Role role = roleMap.get(u.getRole_id());
            me.zhyd.oauth.model.AuthUser itechUser = u.getOAuthUser(com.cryo.oauth.service.OAuthPlatform.Itech);
            MemberInfo info = new MemberInfo();
            info.setEmail(u.getEmail());
            info.setName(u.getName());
            info.setSysUsername(u.getSys_username());
            info.setOauthUsername(itechUser != null ? itechUser.getUsername() : u.getSys_username());
            info.setDisplayRole(role != null ? role.getDisplay_name() : null);
            info.setGroupName(group.getGroup_name());
            info.setJoinDate(member != null ? member.getJoin_date() : null);
            info.setExitDate(member != null ? member.getExit_date() : null);
            return info;
        }));
    }

    /**
     * 核心逻辑：将用户加入组（权限验证由调用方完成）
     */
    private void doAddMember(Group group, User user) {
        String userId = user.getId();

        // 检查 user 表：group_id 已指向目标组
        if (group.getId().equals(user.getGroup_id())) {
            throw new RuntimeException("User " + userId + " is already a member of this group");
        }

        // 检查 group.members 记录
        Group.Member existing = group.getMembers().get(userId);
        if (existing != null) {
            boolean hasJoin = existing.getJoin_date() != null;
            boolean hasExit = existing.getExit_date() != null;
            if (hasJoin && !hasExit) {
                // 只有 join_date，没有 exit_date：数据与 user 表不一致，视为已加入
                throw new RuntimeException("User " + userId + " is already a member of this group");
            }
            // hasJoin && hasExit：曾经加入后离开，允许重新加入，覆盖 join_date 并清空 exit_date
        }

        String today = java.time.LocalDate.now().toString();

        // 更新 user 表
        user.setUser_group(group.getGroup_name());
        user.setGroup_id(group.getId());
        userRepository.save(user);

        // 更新 group.members
        Group.Member member = existing != null ? existing : new Group.Member();
        member.setUser_id(userId);
        member.setUser_name(user.getName());
        member.setRole(user.getRole());
        member.setJoin_date(today);
        member.setExit_date(null);
        group.getMembers().put(userId, member);
        groupRepository.save(group);

        log.info("[group] added user {} to group {}, join_date={}", userId, group.getGroup_name(), today);
    }

    /**
     * 核心逻辑：将用户移出组（权限验证由调用方完成）
     */
    private void doRemoveMember(Group group, User user) {
        String userId = user.getId();

        if (!group.getGroup_name().equals(user.getUser_group())) {
            throw new RuntimeException("User " + userId + " is not in this group");
        }

        String today = java.time.LocalDate.now().toString();

        // 更新 user 表：用 $unset 确保字段被清空而非保留旧值
        userRepository.update(
                new org.springframework.data.mongodb.core.query.Update()
                        .unset("user_group")
                        .unset("group_id"),
                Query.query(Criteria.where("_id").is(userId))
        );

        // 更新 group.members：记录 exit_date（保留历史记录，不删除 member 条目）
        Group.Member member = group.getMembers().get(userId);
        if (member != null) {
            member.setExit_date(today);
        } else {
            // members 中无记录，但 user 确认属于该组，新建一条仅含 exit_date 的记录
            member = new Group.Member();
            member.setUser_id(userId);
            member.setUser_name(user.getName());
            member.setRole(user.getRole());
            member.setExit_date(today);
            group.getMembers().put(userId, member);
        }
        groupRepository.save(group);

        log.info("[group] removed user {} from group {}, exit_date={}", userId, group.getGroup_name(), today);
    }

    /**
     * 查找 Group 并校验 group_admin 只能操作自己管理的组
     */
    private Group findGroupAndCheckPermission(String groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found: " + groupId));
        Role role = currentUserRole();
        if (Role.SUPER_ADMIN.equals(role.getRole_name()) || Role.ADMIN.equals(role.getRole_name())) {
            return group;
        }
        User currentUser = sessionService.getSessionUser().getUser();
        if (group.getGroup_admin() == null || !group.getGroup_admin().contains(currentUser.getId())) {
            throw new RuntimeException("Permission denied: you are not the admin of this group");
        }
        return group;
    }

    // ==================== DTOs ====================

    @Data
    @AllArgsConstructor
    public static class GroupMembersResult {
        private String groupId;
        private String groupName;
        private List<MemberInfo> members;
    }

    @Data
    public static class UpdateDeviceRequest {
        private String displayName;          // nullable，不传则不修改
        private MicroscopeConfig config;     // nullable，不传则不修改
        private String managedBy;            // nullable，不传则不修改；传 userId
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DeviceInfo extends Microscope {
        private String managedByName;
        private String managedByEmail;

        public static DeviceInfo from(Microscope m, User admin) {
            DeviceInfo info = new DeviceInfo();
            // copy all Microscope fields
            info.setId(m.getId());
            info.setDisplay_name(m.getDisplay_name());
            info.setMicroscope_key(m.getMicroscope_key());
            info.setManaged_by(m.getManaged_by());
            info.setConfig(m.getConfig());
            info.setCreated_at(m.getCreated_at());
            info.setUpdated_at(m.getUpdated_at());
            // resolve admin
            info.managedByName  = admin != null ? admin.getName()  : null;
            info.managedByEmail = admin != null ? admin.getEmail() : null;
            return info;
        }
    }

    @Data
    public static class TransferMemberRequest {
        private String userId;
        private String fromGroupId;
        private String toGroupId;
    }

    @Data
    public static class MigrationResult {
        private final int total;
        private final int updated;
        private final int skipped;
    }

    @Data
    public static class ManagementAPIResult<T> {
        private final long total;
        private final int page;
        private final int pageSize;
        private final int totalPages;
        private final List<T> items;

        public static <T> ManagementAPIResult<T> of(Page<T> page) {
            return new ManagementAPIResult<>(
                    page.getTotalElements(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalPages(),
                    page.getContent()
            );
        }

        public static <T> ManagementAPIResult<T> of(List<T> items) {
            return new ManagementAPIResult<>(items.size(), 0, items.size(), 1, items);
        }
    }

    @Data
    public static class MemberInfo {
        private String email;
        private String name;
        private String sysUsername;
        private String oauthUsername;
        private String displayRole;
        private String groupName;
        private String joinDate;
        private String exitDate;
    }

    @Data
    public static class TaskInfo {
        private String taskId;
        private String taskName;
        private String createdAt;
        private String ownerId;
        private String ownerName;
        private String ownerEmail;
        private String groupName;
        private String microscope;
        private String status;
        private boolean completed;
    }

    @Data
    public static class DatasetInfo {
        private String datasetId;
        private String rawPath;
        private String moviePath;
        private String microscope;
        private long moviesCount;
        private long mdocCount;
        private boolean tomo;
        private String createdAt;
        private String ownerId;
        private String ownerName;
        private String ownerEmail;
    }
}
