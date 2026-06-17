package cn.lz.seq.zk;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ZNodeWatcherService {

    private final CuratorFramework curatorFramework;

    @Getter
    private String curStrategy;

    private Map<String, String> tokenTableMap;

    private static final String strategyPath = "/strategy";
    private static final String tokenPath = "/token";

    /**
     * 启动时同步zk上的数据<p/>
     * 初始化zk上的数据到内存，如果zk无相关数据，使用默认值，并将默认值同步到zk。
     * @param curatorFramework
     */
    public ZNodeWatcherService(CuratorFramework curatorFramework) {
        this.curatorFramework = curatorFramework;
    }

    @PostConstruct
    public void init() {
        Properties properties = new Properties();
        try {
            byte[] tokenNodeData = curatorFramework.getData().forPath(tokenPath);
            properties.load(new ByteArrayInputStream(tokenNodeData));
            tokenTableMap = new ConcurrentHashMap<>((Map) properties);
            Stat stat = curatorFramework.checkExists().forPath("/strategy");
            if (stat == null) {
                curatorFramework.create().withMode(CreateMode.PERSISTENT).forPath(strategyPath, "random".getBytes(StandardCharsets.UTF_8));
            } else {
                byte[] strategyNodeData = curatorFramework.getData().forPath(strategyPath);
                if (strategyNodeData == null) {
                    curatorFramework.create().withMode(CreateMode.PERSISTENT).forPath(strategyPath, "random".getBytes(StandardCharsets.UTF_8));
                } else {
                    curStrategy = new String(strategyNodeData, StandardCharsets.UTF_8);
                    return;
                }
            }
            curStrategy = "random";

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            log.error("watcher load properties error", e);
            throw new RuntimeException(e);
        }
    }

    public String getTableNameByToken(String token) {
        String tableName = tokenTableMap.get(token);
        log.debug("token : {} <=> tableName: {}", token, tableName);
        return tableName;
    }

    public void registerZNodeWatcher(String zNodePath) {
        try {
            CuratorCache cache = CuratorCache.build(curatorFramework, zNodePath);
            CuratorCacheListener listener = CuratorCacheListener.builder().forChanges((oldNode, node) -> {
                Properties properties = new Properties();
                try {
                    properties.load(new ByteArrayInputStream(node.getData()));
                    tokenTableMap = new ConcurrentHashMap<>((Map) properties);
                } catch (IOException e) {
                    log.error("watcher load properties error", e);
                    throw new RuntimeException(e);
                }
                log.info("oldNode path: {}, data: {}; \n newNode path: {}, data: {}", oldNode.getPath(), new String(oldNode.getData()), node.getPath(), new String(node.getData()));
            }).build();

            cache.listenable().addListener(listener);
            cache.start();
            log.info("Registration is successful");
        } catch (Exception e) {
            log.error("register watch error", e);
        }
    }
}
