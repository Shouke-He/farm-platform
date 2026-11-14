package com.farm.platform.agent;

import java.util.List;

/** 助手出参：toolsUsed 证明真查了库；degraded 降级；guard 读数闸门判定 */
public record ChatReply(String reply, List<String> toolsUsed, boolean degraded, String guard) {
}
