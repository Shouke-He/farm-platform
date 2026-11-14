package com.farm.platform.agent;

/** 助手入参：conversationId 是认出多轮会话的钥匙 */
public record ChatRequest(String conversationId, String message) {
}
