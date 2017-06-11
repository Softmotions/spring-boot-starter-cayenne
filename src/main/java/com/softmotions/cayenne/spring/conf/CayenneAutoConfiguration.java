package com.softmotions.cayenne.spring.conf;

import java.util.List;
import javax.sql.DataSource;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.CayenneRuntime;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.configuration.server.ServerRuntimeBuilder;
import org.apache.cayenne.di.Module;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

import com.softmotions.cayenne.spring.tx.CayenneTransactionManager;
import com.softmotions.cayenne.utils.ExtBaseContext;

/**
 * @author Adamansky Anton (adamansky@softmotions.com)
 */
@SpringBootConfiguration
@ComponentScan("com.softmotions.cayenne.spring")
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(CayenneProperties.class)
@ConditionalOnClass({ServerRuntime.class})
@ConditionalOnProperty(prefix = "spring.cayenne", name = "config")
public class CayenneAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CayenneAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public CayenneRuntime cayenneServerRuntime(DataSource dataSource,
                                               CayenneProperties props,
                                               ObjectProvider<List<CayenneRuntimeCustomizer>> cayenneCustomizers,
                                               ObjectProvider<List<Module>> cayenneModules) {
        log.info("Creating cayenne runtime, configuration: {}", props.getConfig());
        ServerRuntimeBuilder builder =
                ServerRuntime.builder()
                             .dataSource(dataSource)
                             .addConfig(props.getConfig());

        List<Module> modules = cayenneModules.getIfAvailable();
        if (!CollectionUtils.isEmpty(modules)) {
            modules.forEach(builder::addModule);
        }
        List<CayenneRuntimeCustomizer> customizers = cayenneCustomizers.getIfAvailable();
        if (!CollectionUtils.isEmpty(customizers)) {
            for (CayenneRuntimeCustomizer c : customizers) {
                c.customize(builder);
            }
        }
        return builder.build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(DataSource dataSource,
                                                         CayenneRuntime runtime) {
        log.info("Creating CayenneTransactionManager instance");
        return new CayenneTransactionManager(runtime, dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public FactoryBean<ObjectContext> cayenneObjectContext(CayenneRuntime cayenneRuntime) {
        return new FactoryBean<ObjectContext>() {

            @Override
            public ObjectContext getObject() throws Exception {
                ObjectContext octx = ExtBaseContext.getThreadObjectContextNull();
                if (octx != null) {
                    return octx;
                }
                octx = cayenneRuntime.newContext();
                ExtBaseContext.bindThreadObjectContext(octx);
                return octx;
            }

            @Override
            public Class<?> getObjectType() {
                return ObjectContext.class;
            }

            @Override
            public boolean isSingleton() {
                return false;
            }
        };
    }
}
