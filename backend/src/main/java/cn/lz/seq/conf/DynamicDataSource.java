package cn.lz.seq.conf;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class DynamicDataSource extends AbstractRoutingDataSource {

    private List<String> allDataSourceKeys;

    /**
     * 所有存活的节点
     */
    @Getter
    private CopyOnWriteArrayList<String> allAliveDataSourceKeys;

    @Getter
    private final Map<String, Long> faultDataSourceMap = new ConcurrentHashMap<>();

    @Override
    protected Object determineCurrentLookupKey() {
        return DynamicDataSourceContextHolder.getDataSourceNo();
    }

    /**
     * 从存活列表中随机选一个，排除 excludeKey。
     * 整个操作在 synchronized 块内，保证选到的一定不在故障列表中。
     */
    public String selectAliveDataSource(String excludeKey) {
        synchronized (this) {
            List<String> candidates = new ArrayList<>(allAliveDataSourceKeys);
            if (excludeKey != null) {
                candidates.remove(excludeKey);
            }
            if (candidates.isEmpty()) {
                candidates = new ArrayList<>(allDataSourceKeys);
                log.warn("所有数据源均不可用，回退到全量列表");
            }
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }
    }

    /**
     * 标记数据源为故障，同时从存活列表移除
     */
    public void markAsFault(String dataSourceKey) {
        synchronized (this) {
            faultDataSourceMap.put(dataSourceKey, System.currentTimeMillis());
            allAliveDataSourceKeys.remove(dataSourceKey);
            log.info("数据源 {} 已标记为故障，剩余存活: {}", dataSourceKey, allAliveDataSourceKeys);
        }
    }

    /**
     * 定时探活：尝试获取故障数据源的连接，成功则恢复。
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 5000)
    public void probeFaultDataSources() {
        if (faultDataSourceMap.isEmpty()) {
            return;
        }
        Map<Object, DataSource> resolved = getResolvedDataSources();
        if (resolved == null) {
            return;
        }
        Iterator<Map.Entry<String, Long>> it = faultDataSourceMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            String key = entry.getKey();
            DataSource ds = (DataSource) resolved.get(key);
            if (ds == null) {
                it.remove();
                continue;
            }
            try (Connection conn = ds.getConnection()) {
                it.remove();
                allAliveDataSourceKeys.addIfAbsent(key);
                log.info("数据源 {} 探活成功，已恢复", key);
            } catch (Exception e) {
                log.debug("数据源 {} 仍然不可用", key);
            }
        }
    }

    public void setAllAliveDataSourceKeys(CopyOnWriteArrayList<String> allAliveDataSourceKeys) {
        this.allAliveDataSourceKeys = allAliveDataSourceKeys;
        this.allDataSourceKeys = new ArrayList<>(allAliveDataSourceKeys);
    }
}