package cn.lz.seq.service;

import cn.lz.seq.conf.DynamicDataSource;
import cn.lz.seq.conf.DynamicDataSourceContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
public class RandomRoutingStrategy implements RoutingStrategy {

    @Autowired
    private DynamicDataSource dynamicDataSource;

    @Override
    public String selectDb() {
        List<String> dataSourceKeys = dynamicDataSource.getAllAliveDataSourceKeys();
        // 创建一个 Random 对象
        Random random = new Random();
        int randomIndex = random.nextInt(dataSourceKeys.size());
        String randomItem = dataSourceKeys.get(randomIndex);
        // 设置当前上下文的数据源No
        DynamicDataSourceContextHolder.setDataSourceNo(randomItem);
        return randomItem;
    }
}
