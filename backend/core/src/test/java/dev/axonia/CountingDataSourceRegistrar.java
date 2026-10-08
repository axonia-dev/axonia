package dev.axonia;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class CountingDataSourceRegistrar {

    @Bean
    static BeanPostProcessor countingDataSourceBeanPostProcessor() {

        return new BeanPostProcessor() {

            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                return "dataSource".equals(name) ? new CountingDataSource((DataSource) bean) : bean;
            }
        };
    }
}
