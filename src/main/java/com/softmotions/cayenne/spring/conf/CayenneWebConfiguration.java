package com.softmotions.cayenne.spring.conf;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;

import org.apache.cayenne.ObjectContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.softmotions.cayenne.utils.ExtBaseContext;

/**
 * @author Adamansky Anton (adamansky@softmotions.com)
 */
@SpringBootConfiguration
@AutoConfigureAfter(CayenneConfiguration.class)
@ConditionalOnClass({ServletRequestListener.class})
@ConditionalOnProperty(prefix = "spring.cayenne", name = "config")
public class CayenneWebConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CayenneWebConfiguration.class);

    @Bean
    ServletRequestListener servletRequestListener() {
        log.info("ServletRequestListener instantiated");
        return new ServletRequestListener() {

            @Override
            public void requestDestroyed(ServletRequestEvent servletRequestEvent) {
                ObjectContext octx = ExtBaseContext.getThreadObjectContextNull();
                if (octx != null) {
                    disposeOctx(octx);
                    ExtBaseContext.bindThreadObjectContext(null);
                }
            }

            @Override
            public void requestInitialized(ServletRequestEvent servletRequestEvent) {
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
        };
    }
}
