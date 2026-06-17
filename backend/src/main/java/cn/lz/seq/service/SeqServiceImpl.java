package cn.lz.seq.service;

import cn.lz.seq.api.SeqService;
import cn.lz.seq.dao.SeqDao;
import cn.lz.seq.zk.ZNodeWatcherService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;

import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@DubboService
public class SeqServiceImpl implements SeqService {

    @Autowired
    private SeqDao seqDao;

    @Autowired
    private ZNodeWatcherService zNodeWatcherService;

    public BigInteger getSeq(String token) {
        String tableName = zNodeWatcherService.getTableNameByToken(token);
        if (tableName == null) {
            throw new IllegalArgumentException("Invalid token: " + token);
        }
        BigInteger seq = seqDao.getSeq(tableName);
        log.info("token: {}, seq: {}", token, seq);
        return seq;
    }
}
