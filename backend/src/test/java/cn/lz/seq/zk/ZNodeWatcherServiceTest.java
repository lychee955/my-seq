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
import java.io.OutputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ZNodeWatcherService.
 * Tests constructor initialization, token lookup, and watcher registration.
 */
@ExtendWith(MockitoExtension.class)
class ZNodeWatcherServiceTest {

    @Mock
    private CuratorFramework curatorFramework;

    @Mock
    private GetDataBuilder getDataBuilder;

    @Test
    void testGetTableNameByToken_TokenExists_ReturnsTableName() throws Exception {
        // Setup properties
        Properties properties = new Properties();
        properties.setProperty("video", "video_seq");
        properties.setProperty("order", "order_seq");
        byte[] propertiesBytes = toByteArray(properties);

        // Mock
        when(curatorFramework.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath("/token")).thenReturn(propertiesBytes);
        when(curatorFramework.checkExists()).thenReturn(mock(org.apache.curator.framework.api.ExistsBuilder.class));
        when(curatorFramework.checkExists().forPath("/strategy")).thenReturn(new Stat());
        when(getDataBuilder.forPath("/strategy")).thenReturn("random".getBytes());

        // Create service
        ZNodeWatcherService service = new ZNodeWatcherService(curatorFramework);

        // Verify
        assertThat(service.getTableNameByToken("video")).isEqualTo("video_seq");
        assertThat(service.getTableNameByToken("order")).isEqualTo("order_seq");
    }

    @Test
    void testGetTableNameByToken_TokenDoesNotExist_ReturnsNull() throws Exception {
        // Setup properties
        Properties properties = new Properties();
        properties.setProperty("video", "video_seq");
        byte[] propertiesBytes = toByteArray(properties);

        // Mock
        when(curatorFramework.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath("/token")).thenReturn(propertiesBytes);
        when(curatorFramework.checkExists()).thenReturn(mock(org.apache.curator.framework.api.ExistsBuilder.class));
        when(curatorFramework.checkExists().forPath("/strategy")).thenReturn(new Stat());
        when(getDataBuilder.forPath("/strategy")).thenReturn("random".getBytes());

        // Create service
        ZNodeWatcherService service = new ZNodeWatcherService(curatorFramework);

        // Verify
        assertThat(service.getTableNameByToken("unknown")).isNull();
    }

    @Test
    void testConstructor_ThrowsRuntimeException_WhenZkFails() throws Exception {
        // Mock ZK to throw exception
        when(curatorFramework.getData()).thenThrow(new RuntimeException("ZK connection failed"));

        // Verify
        assertThatThrownBy(() -> new ZNodeWatcherService(curatorFramework))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testConstructor_StrategyPathDoesNotExist_CreatesIt() throws Exception {
        // Setup
        Properties properties = new Properties();
        properties.setProperty("video", "video_seq");
        byte[] propertiesBytes = toByteArray(properties);

        // Mock
        when(curatorFramework.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath("/token")).thenReturn(propertiesBytes);

        // Strategy path doesn't exist
        org.apache.curator.framework.api.ExistsBuilder existsBuilder = mock(org.apache.curator.framework.api.ExistsBuilder.class);
        when(curatorFramework.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath("/strategy")).thenReturn(null);

        // Mock create
        org.apache.curator.framework.api.CreateBuilder createBuilder = mock(org.apache.curator.framework.api.CreateBuilder.class);
        when(curatorFramework.create()).thenReturn(createBuilder);
        when(createBuilder.withMode(any())).thenReturn(createBuilder);
        when(createBuilder.forPath(anyString(), any(byte[].class))).thenReturn("/strategy");

        // Create service
        ZNodeWatcherService service = new ZNodeWatcherService(curatorFramework);

        // Verify strategy was created
        verify(createBuilder).forPath("/strategy", "random".getBytes());
    }

    @Test
    void testConstructor_StrategyPathIsNull_SetsDefault() throws Exception {
        // Setup
        Properties properties = new Properties();
        properties.setProperty("video", "video_seq");
        byte[] propertiesBytes = toByteArray(properties);

        // Mock
        when(curatorFramework.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath("/token")).thenReturn(propertiesBytes);

        // Strategy path exists but data is null
        org.apache.curator.framework.api.ExistsBuilder existsBuilder = mock(org.apache.curator.framework.api.ExistsBuilder.class);
        when(curatorFramework.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath("/strategy")).thenReturn(new Stat());
        when(getDataBuilder.forPath("/strategy")).thenReturn(null);

        // Mock create
        org.apache.curator.framework.api.CreateBuilder createBuilder = mock(org.apache.curator.framework.api.CreateBuilder.class);
        when(curatorFramework.create()).thenReturn(createBuilder);
        when(createBuilder.withMode(any())).thenReturn(createBuilder);
        when(createBuilder.forPath(anyString(), any(byte[].class))).thenReturn("/strategy");

        // Create service
        ZNodeWatcherService service = new ZNodeWatcherService(curatorFramework);

        // Verify
        verify(createBuilder).forPath("/strategy", "random".getBytes());
    }

    /**
     * Helper to convert Properties to byte array.
     */
    private byte[] toByteArray(Properties properties) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        properties.store(baos, null);
        return baos.toByteArray();
    }
}
