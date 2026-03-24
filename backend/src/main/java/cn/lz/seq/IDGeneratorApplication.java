package cn.lz.seq;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
@SpringBootApplication(
        exclude = {DataSourceAutoConfiguration.class}
)
@EnableDubbo
public class IDGeneratorApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext applicationContext = SpringApplication.run(IDGeneratorApplication.class);
        log.info("启动成功!");
    }
}