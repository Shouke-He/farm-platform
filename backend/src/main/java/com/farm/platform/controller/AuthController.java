package com.farm.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.annotation.OpLog;
import com.farm.platform.common.Result;
import com.farm.platform.common.UserContext;
import com.farm.platform.dto.LoginRequest;
import com.farm.platform.dto.LoginResponse;
import com.farm.platform.entity.SysRole;
import com.farm.platform.entity.SysUser;
import com.farm.platform.mapper.SysRoleMapper;
import com.farm.platform.mapper.SysUserMapper;
import com.farm.platform.service.AuthService;
import com.farm.platform.service.DeviceService;
import com.farm.platform.service.PlantService;
import com.farm.platform.service.ReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 认证与账号管理：登录发 JWT 通行证、自助注册、个人资料与密码维护、账号增删改查、用户种植概览。 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PlantService plantService;
    private final DeviceService deviceService;
    private final ReminderService reminderService;

    /** 登录：账号密码交给 AuthService 校验并签发 JWT。 */
    @PostMapping("/login")   // 【只做参数】
    @OpLog(module = "账号", operation = "用户登录")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    /** 用户自助注册：校验用户名/密码/昵称 → 用户名查重 → BCrypt 加密密码 → 按 role_code=USER 绑角色 → 入库， */
    @PostMapping("/register")
    @OpLog(module = "账号", operation = "用户注册")
    public Result<LoginResponse> register(@RequestBody SysUser u) {
        if (u.getUsername() == null || u.getUsername().isBlank()   // 【查重名】
                || u.getPassword() == null || u.getPassword().length() < 6
                || u.getRealName() == null || u.getRealName().isBlank()) {
            return Result.error(400, "用户名、密码（至少6位）和昵称不能为空");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, u.getUsername()));
        if (exists != null && exists > 0) {
            return Result.error(400, "用户名已被注册");
        }
        SysUser nu = new SysUser();
        nu.setUsername(u.getUsername());
        nu.setPassword(passwordEncoder.encode(u.getPassword()));   // 【密码加密】
        nu.setRealName(u.getRealName());
        nu.setPhone(u.getPhone());
        // ★ 防越权注册的关键一行：不读请求体里的 roleId，而是按 role_code=USER 去 sys_role 反查真实 id。
        SysRole userRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "USER"));   // 【角色反查】   // 【追问·注册提权】
        nu.setRoleId(userRole != null ? userRole.getId() : 2L);
        nu.setStatus(1);
        // 所在区域（和风城市 ID）：注册时选择，NULL=南宁；天气提醒按区域过滤
        nu.setCity(u.getCity());
        userMapper.insert(nu);
        return Result.ok(authService.login(new LoginRequest(u.getUsername(), u.getPassword(), null)));   // 【注册即登录】
    }

    /** 当前登录用户的信息：按 UserContext 里的 userId 查库，带上 roleCode/roleName，密码置空后返回。 */
    @GetMapping("/me")
    public Result<SysUser> me() {
        SysUser u = userMapper.selectById(UserContext.getUserId());
        SysRole r = roleMapper.selectById(u.getRoleId());
        if (r != null) {
            u.setRoleCode(r.getRoleCode());
            u.setRoleName(r.getRoleName());
        }
        u.setPassword(null);
        return Result.ok(u);
    }

    /** 改自己的资料：昵称 / 手机号 / 所在城市，只覆盖传了值的字段，用户名不参与更新。 */
    @PutMapping("/profile")
    @OpLog(module = "账号", operation = "修改个人信息")
    public Result<SysUser> updateProfile(@RequestBody SysUser u) {
        SysUser me = userMapper.selectById(UserContext.getUserId());
        if (me == null) {
            return Result.error(404, "用户不存在");
        }
        if (u.getRealName() != null && !u.getRealName().isBlank()) {
            me.setRealName(u.getRealName());
        }
        if (u.getPhone() != null) {
            me.setPhone(u.getPhone());
        }
        if (u.getCity() != null) {
            me.setCity(u.getCity());
        }
        userMapper.updateById(me);
        me.setPassword(null);
        return Result.ok(me);
    }

    /** 改自己的密码：先用 BCrypt 比对原密码，对得上才把新密码加密写回；新密码要求至少 6 位。 */
    @PutMapping("/password")
    @OpLog(module = "账号", operation = "修改密码")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        if (oldPwd == null || oldPwd.isBlank() || newPwd == null || newPwd.length() < 6) {
            return Result.error(400, "请填写原密码，新密码至少 6 位");
        }
        SysUser me = userMapper.selectById(UserContext.getUserId());
        if (me == null) {
            return Result.error(404, "用户不存在");
        }
        if (!passwordEncoder.matches(oldPwd, me.getPassword())) {
            return Result.error(400, "原密码不正确");
        }
        me.setPassword(passwordEncoder.encode(newPwd));
        userMapper.updateById(me);
        return Result.ok();
    }

    /** 角色字典：sys_role 全表，供账号管理页的角色下拉和任务页的用户下拉取值。 */
    @GetMapping("/roles")
    public Result<List<SysRole>> roles() {
        return Result.ok(roleMapper.selectList(null));
    }

    /** 账号列表：全表按 id 升序返回，逐个把密码字段置空。 */
    @GetMapping("/users")
    public Result<List<SysUser>> users() {
        if (UserContext.getRoleId() == null || UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理员可查看账号列表");
        }
        List<SysUser> list = userMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId));
        list.forEach(u -> u.setPassword(null));
        return Result.ok(list);
    }

    /** 新建账号：密码 BCrypt 加密、状态置 1（正常）后插入 sys_user，返回时把密码清空。 */
    @PostMapping("/users")
    @OpLog(module = "账号", operation = "创建账号")
    public Result<SysUser> createUser(@RequestBody SysUser u) {
        if (UserContext.getRoleId() == null || UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理员可创建账号");
        }
        u.setPassword(passwordEncoder.encode(u.getPassword()));
        u.setStatus(1);
        userMapper.insert(u);
        u.setPassword(null);
        return Result.ok(u);
    }

    /** 更新账号：传了新密码就重新 BCrypt 加密，没传则把 password 置为 null */
    @PutMapping("/users")
    @OpLog(module = "账号", operation = "更新账号")
    public Result<Void> updateUser(@RequestBody SysUser u) {
        if (UserContext.getRoleId() == null || UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理员可修改账号");
        }
        if (u.getPassword() != null && !u.getPassword().isBlank()) {
            u.setPassword(passwordEncoder.encode(u.getPassword()));
        } else {
            u.setPassword(null);
        }
        userMapper.updateById(u);
        return Result.ok();
    }

    /** 删除账号：按路径上的 id 直接删 sys_user 表里的那一行。 */
    @DeleteMapping("/users/{id}")
    @OpLog(module = "账号", operation = "删除账号")
    public Result<Void> deleteUser(@PathVariable Long id) {
        if (UserContext.getRoleId() == null || UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理员可删除账号");
        }
        userMapper.deleteById(id);
        return Result.ok();
    }

    /** 用户种植概览：一次返回四块数据 —— 用户信息、该用户的种植物（带品种/设备/阶段/浇水建议）、 */
    @GetMapping("/users/{id}/overview")
    @OpLog(module = "账号", operation = "查看用户种植概览")
    public Result<Map<String, Object>> overview(@PathVariable Long id) {
        if (UserContext.getRoleId() == null || UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理员可查看用户种植概览");
        }
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            return Result.error(404, "用户不存在");
        }
        u.setPassword(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", u);
        out.put("plants", plantService.listByUser(id));      // 种植物（已带品种/设备/阶段/浇水建议）
        out.put("tasks", reminderService.reminderStats(id)); // 提醒统计（该用户产生的全部提醒）
        out.put("devices", deviceService.listByUser(id));    // 设备（已带最新指标）
        return Result.ok(out);
    }
}
