package com.softmotions.cayenne.spring;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @author Adamansky Anton (adamansky@softmotions.com)
 */
@ConfigurationProperties(prefix = "spring.cayenne.server")
@Validated
public class CayenneServerProperties implements InitializingBean {

    @NotEmpty
    private String config;

    private Rop rop = new Rop();

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public Rop getRop() {
        return rop;
    }

    public void setRop(Rop rop) {
        this.rop = rop;
    }

    public static class Rop {

        private String endpoint;

        private Map<String, String> props = new HashMap<>();


        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public Map<String, String> getProps() {
            return props;
        }

        public void setProps(Map<String, String> props) {
            this.props = props;
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }

}
