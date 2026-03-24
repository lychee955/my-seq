package cn.lz.seq.service;

import cn.lz.seq.dao.SeqDao;
import cn.lz.seq.zk.ZNodeWatcherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SeqServiceImpl.
 * Tests token exists/doesn't exist scenarios.
 */
@ExtendWith(MockitoExtension.class)
class SeqServiceImplTest {

    @Mock
    private SeqDao seqDao;

    @Mock
    private ZNodeWatcherService zNodeWatcherService;

    @InjectMocks
    private SeqServiceImpl seqService;

    @Test
    void testGetSeq_TokenExists_ReturnsSeqFromDao() {
        // Setup
        String token = "video";
        String tableName = "video_seq";
        BigInteger expectedSeq = BigInteger.valueOf(12345L);

        when(zNodeWatcherService.getTableNameByToken(token)).thenReturn(tableName);
        when(seqDao.getSeq(tableName)).thenReturn(expectedSeq);

        // Execute
        BigInteger result = seqService.getSeq(token);

        // Verify
        assertThat(result).isEqualTo(expectedSeq);
        verify(zNodeWatcherService).getTableNameByToken(token);
        verify(seqDao).getSeq(tableName);
    }

    @Test
    void testGetSeq_TokenDoesNotExist_ReturnsMinusOne() {
        // Setup
        String token = "unknown_token";

        when(zNodeWatcherService.getTableNameByToken(token)).thenReturn(null);

        // Execute
        BigInteger result = seqService.getSeq(token);

        // Verify
        assertThat(result).isEqualTo(BigInteger.valueOf(-1));
        verify(zNodeWatcherService).getTableNameByToken(token);
        verify(seqDao, never()).getSeq(anyString());
    }

    @Test
    void testGetSeq_DifferentTokens_ReturnCorrectResults() {
        // Setup first token
        String token1 = "video";
        String table1 = "video_seq";
        BigInteger seq1 = BigInteger.valueOf(100L);

        // Setup second token
        String token2 = "order";
        String table2 = "order_seq";
        BigInteger seq2 = BigInteger.valueOf(200L);

        when(zNodeWatcherService.getTableNameByToken(token1)).thenReturn(table1);
        when(zNodeWatcherService.getTableNameByToken(token2)).thenReturn(table2);
        when(seqDao.getSeq(table1)).thenReturn(seq1);
        when(seqDao.getSeq(table2)).thenReturn(seq2);

        // Execute
        BigInteger result1 = seqService.getSeq(token1);
        BigInteger result2 = seqService.getSeq(token2);

        // Verify
        assertThat(result1).isEqualTo(seq1);
        assertThat(result2).isEqualTo(seq2);
        verify(seqDao).getSeq(table1);
        verify(seqDao).getSeq(table2);
    }

    @Test
    void testGetSeq_LargeSeqValue_ReturnsCorrectly() {
        // Setup
        String token = "large_seq";
        String tableName = "large_table";
        BigInteger largeSeq = new BigInteger("9876543210987654321");

        when(zNodeWatcherService.getTableNameByToken(token)).thenReturn(tableName);
        when(seqDao.getSeq(tableName)).thenReturn(largeSeq);

        // Execute
        BigInteger result = seqService.getSeq(token);

        // Verify
        assertThat(result).isEqualTo(largeSeq);
    }

    @Test
    void testGetSeq_TokenIsNull_StillCallsZNodeWatcher() {
        // Setup
        when(zNodeWatcherService.getTableNameByToken(null)).thenReturn(null);

        // Execute
        BigInteger result = seqService.getSeq(null);

        // Verify
        assertThat(result).isEqualTo(BigInteger.valueOf(-1));
        verify(zNodeWatcherService).getTableNameByToken(null);
    }
}
