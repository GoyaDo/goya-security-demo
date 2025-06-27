package com.ysmjjsy.goya.security.bus.spi;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 传输结果
 *
 * @author goya
 * @since 2025/6/24
 */
@Data
@Builder
public class TransportResult {

    /**
     * 是否成功
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
     * 传输时间
     */
    private LocalDateTime transportTime;

    /**
     * 响应数据
     */
    private Object responseData;

    /**
     * 创建成功结果
     *
     * @param messageId 消息ID
     * @return 成功结果
     */
    public static TransportResult success(String messageId) {
        return TransportResult.builder()
                .success(true)
                .messageId(messageId)
                .transportTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败结果
     *
     * @param errorMessage 错误信息
     * @return 失败结果
     */
    public static TransportResult failure(String errorMessage) {
        return TransportResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .transportTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败结果
     *
     * @param errorMessage 错误信息
     * @param throwable    异常详情
     * @return 失败结果
     */
    public static TransportResult failure(String errorMessage, Throwable throwable) {
        return TransportResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .throwable(throwable)
                .transportTime(LocalDateTime.now())
                .build();
    }
} 