package com.ysmjjsy.goya.security.bus.context;

import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.transport.MessageTransport;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/7/3 17:08
 */
@Slf4j
@RequiredArgsConstructor
public class MessageTransportContext {

    @Getter
    private final Map<TransportType, MessageTransport> transportRegistry = new ConcurrentHashMap<>();

    /**
     * 注册传输层
     *
     * @param transports 传输层列表
     */
    @Autowired(required = false)
    public void registerTransports(List<MessageTransport> transports) {
        if (transports != null) {
            for (MessageTransport transport : transports) {
                transportRegistry.put(transport.getTransportType(), transport);
                log.info("Registered MessageTransport: {} with capabilities: {}",
                        transport.getTransportType(), transport.getSupportedCapabilities());
            }
        }
    }
}
