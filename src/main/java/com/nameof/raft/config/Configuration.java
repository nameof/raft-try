package com.nameof.raft.config;


import cn.hutool.core.io.IoUtil;
import cn.hutool.json.JSONUtil;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Getter
@Setter
public class Configuration {
    private static volatile Configuration instance;

    private int id;
    private List<NodeInfo> nodes;
    private int heartbeatInterval;
    private int minHeartbeatTimeOut;
    private int maxHeartbeatTimeOut;

    private Configuration() {
    }

    public static Configuration get() {
        if (instance == null) {
            synchronized (Configuration.class) {
                if (instance == null) {
                    try {
                        instance = loadConfig();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return instance;
    }

    public static Configuration loadConfig() throws IOException {
        try (InputStream inputStream = Configuration.class.getResourceAsStream("/config.json")) {
            return JSONUtil.toBean(IoUtil.read(inputStream, StandardCharsets.UTF_8), Configuration.class);
        }
    }
}
