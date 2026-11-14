package com.farm.platform.agent;

import com.farm.platform.common.Result;
import com.farm.platform.common.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 智能助手接口层。 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "farm.agent.enabled", havingValue = "true")   // agent.enabled=true 才装配
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/chat")   // 【先认身份】
    public Result<ChatReply> chat(@RequestBody ChatRequest req) {
        if (req == null || req.message() == null || req.message().isBlank()) {
            return Result.error("请先输入问题");
        }
        Long userId = UserContext.getUserId();
        // 正常情况下拦截器已经保证了登录态；这里判空是兜底，宁可报错也不让 userId=null 流进业务层
        if (userId == null) {
            return Result.error("登录状态已失效，请重新登录");
        }
        // 角色也要一并带上：工具最终会调 ReminderService.create()，那里的归属校验读的是
        Long roleId = UserContext.getRoleId();
        return Result.ok(agentService.chat(userId, roleId, req.conversationId(), req.message()));
    }

    /** 探活：前端可用它判断助手是否可用（开关关闭时本类不装配，这个路径会 404） */
    @GetMapping("/status")
    public Result<String> status() {
        return Result.ok("ok");
    }
}
