package cn.lz.seq.service;

import cn.lz.seq.conf.DynamicDataSource;
import cn.lz.seq.conf.DynamicDataSourceContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RandomRoutingStrategy implements RoutingStrategy {

    @Autowired
    private DynamicDataSource dynamicDataSource;

    @Override
    public String selectDb() {
        String key = dynamicDataSource.selectAliveDataSource(null);
        DynamicDataSourceContextHolder.setDataSourceNo(key);
        return key;
    }
}
