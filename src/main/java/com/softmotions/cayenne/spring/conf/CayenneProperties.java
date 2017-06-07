package com.softmotions.cayenne.spring.conf;

import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * @author Adamansky Anton (adamansky@softmotions.com)
 */
@Service
@ConfigurationProperties(prefix = "spring.cayenne")
@Validated
public class CayenneProperties implements InitializingBean {

    @NotEmpty
    private String config;

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }
}
