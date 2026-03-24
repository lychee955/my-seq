package cn.lz.seq.conf;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynamicDataSourceContextHolder {

    public static ThreadLocal<String> DATASOURCE_CONTEXT_HOLDER = new ThreadLocal<>();

    public static void setDataSourceNo(String dataSourceNo) {
        log.debug("设置数据源 = {}", dataSourceNo);
        DATASOURCE_CONTEXT_HOLDER.set(dataSourceNo);
    }

    public static String getDataSourceNo() {
        String dataSourceNo = DATASOURCE_CONTEXT_HOLDER.get();
        log.debug("获得数据源 = {}", dataSourceNo);
        return dataSourceNo;
    }

    public static void clearDataSourceNos() {
        DATASOURCE_CONTEXT_HOLDER.remove();
    }
}
