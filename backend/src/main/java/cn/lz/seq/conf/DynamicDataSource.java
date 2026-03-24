package cn.lz.seq.conf;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class DynamicDataSource extends AbstractRoutingDataSource {

    private List<String> allDataSourceKeys;

    /**
     * 所有存活的节点
     */
    @Getter
    private CopyOnWriteArrayList<String> allAliveDataSourceKeys;

    @Getter
    private Map<String, Long> faultDataSourceMap = new ConcurrentHashMap<>();


    @Override
    protected Object determineCurrentLookupKey() {
        handleFaultDataSource();
        String dateSourceType = DynamicDataSourceContextHolder.getDataSourceNo();
        log.debug("选取数据源No: {}", dateSourceType);
        return dateSourceType;
    }

    /**
     * 剔除/恢复故障数据源
     */
    public void handleFaultDataSource() {
        Set<Map.Entry<String, Long>> entries = faultDataSourceMap.entrySet();
        if (entries.isEmpty()) {
            return;
        }
        synchronized (this) {
            Iterator<Map.Entry<String, Long>> iterator = entries.iterator();
            List<String> faultList = new ArrayList<>();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> next = iterator.next();
                if (System.currentTimeMillis() - next.getValue() > 10000) {
                    faultDataSourceMap.remove(next.getKey());
                    allAliveDataSourceKeys.addIfAbsent(next.getKey());
                } else {
                    faultList.add(next.getKey());
                }
            }
            if (!faultList.isEmpty()) {
                allAliveDataSourceKeys.removeAll(faultList);
            }
        }

    }

    public void setAllAliveDataSourceKeys(CopyOnWriteArrayList<String> allAliveDataSourceKeys) {
        this.allAliveDataSourceKeys = allAliveDataSourceKeys;
        this.allDataSourceKeys = new ArrayList<>(allAliveDataSourceKeys);
    }

    public void setFaultDataSourceMap(Map<String, Long> faultDataSourceMap) {
        this.faultDataSourceMap = faultDataSourceMap;
    }


}