package cn.lz.seq.zk;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.GetDataBuilder;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZNodeWatcherServiceTest {

    @Mock
    private CuratorFramework curatorFramework;

    @Mock
    private GetDataBuilder getDataBuilder;

    @Test
    void testGetTableNameByToken_TokenExists_ReturnsTableName() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("video", "video_seq");
        properties.setProperty("order", "order_seq");
        byte[] propertiesBytes = toByteArray(properties);

        when(curatorFramework.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath("/token")).thenReturn(propertiesBytes);
        when(curatorFramework.checkExists()).thenReturn(mock(org.apache.curator.framework.api.ExistsBuilder.class));
        when(curatorFramework.checkExists().forPath("/strategy")).thenReturn(new Stat());
        when(getDataBuilder.forPath("/strategy")).thenReturn("random".getBytes());

        ZNodeWatcherService service = new ZNodeWatcherService(curatorFramework);
        service.init();

        assertThat(service.getTableNameByToken("video")).isEqualTo("video_seq");
        assertThat(service.getTableNameByToken("order")).isEqualTo("order_seq");
    }

    @Test
    void testGetTableNameByToken_TokenDoesNotExist_ReturnsNull() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("video", "video_seq");
        byte[] propertiesBytes = toByteArray(properties);

        when(curatorFramework.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath("/token")).thenReturn(propertiesBytes);
        when(curatorFramework.checkExists()).thenReturn(mock(org.apache.curator.framework.api.ExistsBuilder.class));
        when(curatorFramework.checkExists().forPath("/strategy")).thenReturn(new Stat());
        when(getDataBuilder.forPath("/strategy")).thenReturn("random".getBytes());

        ZNodeWatcherService service = new ZNodeWatcherService(curatorFramework);
        service.init();

        assertThat(service.getTableNameByToken("unknown")).isNull();
    }

    @Test
    void testInit_ThrowsRuntimeException_WhenZkFails() throws Exception {
        when(curatorFramework.getData()).thenThrow(new RuntimeException("ZK connection failed"));

        ZNodeWatcherService service = new ZNodeWatcherService(curatorFramework);

        assertThatThrownBy(service::init)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testInit_StrategyPathDoesNotExist_CreatesIt() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("video", "video_seq");
        byte[] propertiesBytes = toByteArray(properties);

        when(curatorFramework.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath("/token")).thenReturn(propertiesBytes);

        org.apache.curator.framework.api.ExistsBuilder existsBuilder = mock(org.apache.curator.framework.api.ExistsBuilder.class);
        when(curatorFramework.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath("/strategy")).thenReturn(null);

        org.apache.curator.framework.api.CreateBuilder createBuilder = mock(org.apache.curator.framework.api.CreateBuilder.class);
        when(curatorFramework.create()).thenReturn(createBuilder);
        when(createBuilder.withMode(any())).thenReturn(createBuilder);
        when(createBuilder.forPath(anyString(), any(byte[].class))).thenReturn("/strategy");

        ZNodeWatcherService service = new ZNodeWatcherService(curatorFramework);
        service.init();

        verify(createBuilder).forPath("/strategy", "random".getBytes());
    }

    @Test
    void testInit_StrategyPathIsNull_SetsDefault() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("video", "video_seq");
        byte[] propertiesBytes = toByteArray(properties);

        when(curatorFramework.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath("/token")).thenReturn(propertiesBytes);

        org.apache.curator.framework.api.ExistsBuilder existsBuilder = mock(org.apache.curator.framework.api.ExistsBuilder.class);
        when(curatorFramework.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath("/strategy")).thenReturn(new Stat());
        when(getDataBuilder.forPath("/strategy")).thenReturn(null);

        org.apache.curator.framework.api.CreateBuilder createBuilder = mock(org.apache.curator.framework.api.CreateBuilder.class);
        when(curatorFramework.create()).thenReturn(createBuilder);
        when(createBuilder.withMode(any())).thenReturn(createBuilder);
        when(createBuilder.forPath(anyString(), any(byte[].class))).thenReturn("/strategy");

        ZNodeWatcherService service = new ZNodeWatcherService(curatorFramework);
        service.init();

        verify(createBuilder).forPath("/strategy", "random".getBytes());
    }

    private byte[] toByteArray(Properties properties) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        properties.store(baos, null);
        return baos.toByteArray();
    }
}
