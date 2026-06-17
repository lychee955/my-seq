package cn.lz.seq.conf;

import cn.lz.seq.dao.DynamicSwitch;
import cn.lz.seq.service.RoutingStrategy;
import cn.lz.seq.service.RoutingStrategyFactory;
import cn.lz.seq.zk.ZNodeWatcherService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

@Slf4j
@Aspect
public class DataSourceFailoverAspect {

    @Autowired
    private RoutingStrategyFactory routingStrategyFactory;

    @Autowired
    private DynamicDataSource dynamicDataSource;

    @Autowired
    private ZNodeWatcherService zNodeWatcherService;

    @Pointcut("execution(* cn.lz.seq.dao.SeqDao.getSeq(..))")
    public void dataAccessOperation() {
    }

    @Around("@annotation(dynamicSwitch)")
    public Object afterThrowingDataAccessOperationException(ProceedingJoinPoint joinPoint, DynamicSwitch dynamicSwitch) throws Throwable {
        long start = System.currentTimeMillis();
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        log.debug("Entering method {} of class {}", methodName, className);

        RoutingStrategy routingStrategy = routingStrategyFactory.getRoutingStrategy(zNodeWatcherService.getCurStrategy());
        String dataSourceNo = routingStrategy.selectDb();
        log.debug("当前策略选定的数据源No: {}", dataSourceNo);

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            if (e instanceof CannotGetJdbcConnectionException) {
                dynamicDataSource.markAsFault(dataSourceNo);
                DynamicDataSourceContextHolder.clearDataSourceNos();

                String retrySource = dynamicDataSource.selectAliveDataSource(dataSourceNo);
                DynamicDataSourceContextHolder.setDataSourceNo(retrySource);
                log.info("数据源 {} 故障，转移 -> {}", dataSourceNo, retrySource);
                try {
                    return joinPoint.proceed();
                } catch (Throwable ex) {
                    log.info("重试数据源 {} 也失败", retrySource);
                    throw ex;
                }
            } else {
                log.error("获取id失败, 数据源No: {}", dataSourceNo, e);
                throw e;
            }
        } finally {
            DynamicDataSourceContextHolder.clearDataSourceNos();
            long elapsedTime = System.currentTimeMillis() - start;
            log.debug("Exiting method {} of class {} took {} ms", methodName, className, elapsedTime);
        }
    }
}
