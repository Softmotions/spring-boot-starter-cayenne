package com.softmotions.cayenne.spring;

import java.util.List;
import javax.servlet.http.HttpServlet;
import javax.sql.DataSource;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.rop.client.ClientRuntime;
import org.apache.cayenne.configuration.rop.client.ClientRuntimeBuilder;
import org.apache.cayenne.configuration.rop.server.ROPServerModule;
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
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

import com.softmotions.cayenne.spring.server.rop.CayenneServerRopServlet;
import com.softmotions.cayenne.spring.server.tx.CayenneTransactionManager;
import com.softmotions.cayenne.utils.ExtBaseContext;

/**
 * @author Adamansky Anton (adamansky@softmotions.com)
 */
@SpringBootConfiguration
@ComponentScan("com.softmotions.cayenne.spring")
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
public class CayenneAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CayenneAutoConfiguration.class);

    @Configuration
    @EnableConfigurationProperties(CayenneClientProperties.class)
    @ConditionalOnProperty(prefix = "spring.cayenne.client", name = "url")
    @ConditionalOnClass(ClientRuntimeBuilder.class)
    protected static class CayenneClientAutoConfiguration {

        @Bean
        public ClientRuntime cayenneClientRuntime(CayenneClientProperties props) {
            log.info("Creating the client cayenne runtime: {}", props);
            ClientRuntimeBuilder crb = ClientRuntime.builder();
            crb.properties(props.getProps());
            return crb.build();
        }

    }

    @Configuration
    @ConditionalOnProperty(prefix = "spring.cayenne.server", name = "config")
    @ConditionalOnClass(ServerRuntime.class)
    @EnableConfigurationProperties(CayenneServerProperties.class)
    protected static class CayenneServerAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ServerRuntime cayenneServerRuntime(DataSource dataSource,
                                                  CayenneServerProperties props,
                                                  ObjectProvider<List<CayenneServerRuntimeCustomizer>> cayenneCustomizers,
                                                  ObjectProvider<List<Module>> cayenneModules) {
            log.info("Creating the server cayenne runtime, configuration: {}", props.getConfig());
            ServerRuntimeBuilder builder =
                    ServerRuntime.builder()
                                 .dataSource(dataSource)
                                 .addConfig(props.getConfig());

            List<Module> modules = cayenneModules.getIfAvailable();
            if (!CollectionUtils.isEmpty(modules)) {
                modules.forEach(builder::addModule);
            }
            List<CayenneServerRuntimeCustomizer> customizers = cayenneCustomizers.getIfAvailable();
            if (!CollectionUtils.isEmpty(customizers)) {
                for (CayenneServerRuntimeCustomizer c : customizers) {
                    c.customize(builder);
                }
            }
            return builder.build();
        }

        @Bean
        @Primary
        public PlatformTransactionManager transactionManager(DataSource dataSource,
                                                             ServerRuntime runtime) {
            log.info("Creating CayenneTransactionManager instance");
            return new CayenneTransactionManager(runtime, dataSource);
        }

        @Bean
        @ConditionalOnMissingBean
        public FactoryBean<ObjectContext> cayenneObjectContext(ServerRuntime cayenneRuntime) {
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

    @Configuration
    @ConditionalOnClass({ServerRuntime.class, HttpServlet.class, ServletRegistrationBean.class})
    @ConditionalOnProperty(prefix = "spring.cayenne.server.rop", name = "endpoint")
    @Import(CayenneServerAutoConfiguration.class)
    static class CayenneServerRopAutoConfiguration {

        @Bean
        public CayenneServerRuntimeCustomizer cayenneRopCustomizer(CayenneServerProperties props) {
            CayenneServerProperties.Rop rop = props.getRop();
            return builder -> {
                builder.addModule(new ROPServerModule(rop.getProps()));
            };
        }

        @Bean
        public ServletRegistrationBean cayenneROPServletRegistration(ServerRuntime srt,
                                                                     CayenneServerProperties cfg) {
            return new ServletRegistrationBean(
                    new CayenneServerRopServlet(srt, cfg),
                    cfg.getRop().getEndpoint()
            );
        }
    }
}
