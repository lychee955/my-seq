package cn.lz.seq.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 路由策略工厂
 */
@Component
public class RoutingStrategyFactory {

    private final Map<String, RoutingStrategy> routingStrategyMap = new HashMap<>();

    @Autowired
    private RandomRoutingStrategy randomRoutingStrategy;

    @PostConstruct
    public void init() {
        this.routingStrategyMap.put("random", randomRoutingStrategy);
    }

    public RoutingStrategy getRoutingStrategy(String strategyType) {
        RoutingStrategy routingStrategy = routingStrategyMap.get(strategyType);
        if (routingStrategy == null) {
            return randomRoutingStrategy;
        }
        return routingStrategy;
    }
}