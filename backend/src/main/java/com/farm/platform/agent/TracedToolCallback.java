package com.farm.platform.agent;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;

/** 工具回调的装饰器，只干一件事：记录"这一轮到底真的调了哪几个工具"，供前端显示 toolsUsed。 */
public class TracedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final String name;
    private final List<String> trace;

    public TracedToolCallback(ToolCallback delegate, List<String> trace) {
        this.delegate = delegate;
        // 名字在构造时取一次即可，工具定义是不可变的
        this.name = delegate.getToolDefinition().name();
        this.trace = trace;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        record();
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        record();
        return delegate.call(toolInput, toolContext);
    }

    /** 先记后调：即使工具执行抛异常，也要留下"它被调过"的痕迹，方便排查 */
    private void record() {   // 【工具审计】
        if (trace != null) {
            trace.add(name);
        }
    }
}
