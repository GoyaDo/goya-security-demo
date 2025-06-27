package com.ysmjjsy.goya.security.bus.api;

import com.ysmjjsy.goya.security.bus.enums.TransportType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息发布结果
 *
 * @author goya
 * @since 2025/6/24
 */
@Data
@Builder
public class PublishResult {

    /**
     * 是否发布成功
     */
    private boolean success;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 异常详情
     */
    private Throwable throwable;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 使用的传输层类型
     */
    private TransportType transportType;

    /**
     * 路由信息
     */
    private String routeInfo;

    /**
     * 创建成功结果
     *
     * @param messageId     消息ID
     * @param transportType 传输层类型
     * @return 成功结果
     */
    public static PublishResult success(String messageId, TransportType transportType) {
        return PublishResult.builder()
                .success(true)
                .messageId(messageId)
                .transportType(transportType)
                .publishTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败结果
     *
     * @param errorMessage 错误信息
     * @return 失败结果
     */
    public static PublishResult failure(String errorMessage) {
        return PublishResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .publishTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败结果
     *
     * @param errorMessage 错误信息
     * @param throwable    异常详情
     * @return 失败结果
     */
    public static PublishResult failure(String errorMessage, Throwable throwable) {
        return PublishResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .throwable(throwable)
                .publishTime(LocalDateTime.now())
                .build();
    }
} 