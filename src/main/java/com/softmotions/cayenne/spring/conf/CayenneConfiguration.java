package com.softmotions.cayenne.spring.conf;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.sql.DataSource;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.CayenneRuntime;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import com.softmotions.cayenne.spring.tx.CayenneTransactionManager;
import com.softmotions.cayenne.utils.ExtBaseContext;

/**
 * @author Adamansky Anton (adamansky@softmotions.com)
 */
@SpringBootConfiguration
@ComponentScan("com.softmotions.cayenne.spring")
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnClass({ServerRuntime.class})
@ConditionalOnProperty(prefix = "spring.cayenne", name = "config")
public class CayenneConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CayenneConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public CayenneRuntime cayenneServerRuntime(DataSource dataSource,
                                               CayenneProperties props) {
        log.info("Creating cayenne runtime, configuration: {}", props.getConfig());
        return ServerRuntime.builder()
                            .dataSource(dataSource)
                            .addConfig(props.getConfig())
                            .build();
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
    public FactoryBean<ObjectContext> objectContext(CayenneRuntime cayenneRuntime) {
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

    @Service
    static class CayenneServletRequestListener implements ServletRequestListener {

        CayenneServletRequestListener() {
            log.info("CayenneServletRequestListener instantiated");
        }

        @Override
        public void requestInitialized(ServletRequestEvent sre) {
        }

        @Override
        public void requestDestroyed(ServletRequestEvent sre) {
            ObjectContext octx = ExtBaseContext.getThreadObjectContextNull();
            if (octx != null) {
                disposeOctx(octx);
                ExtBaseContext.bindThreadObjectContext(null);
            }
        }

        private void disposeOctx(ObjectContext octx) {
            if (octx.hasChanges()) {
                try {
                    octx.commitChanges();
                } catch (Exception e) {
                    log.error("", e);
                }
            }
        }
    }
}
