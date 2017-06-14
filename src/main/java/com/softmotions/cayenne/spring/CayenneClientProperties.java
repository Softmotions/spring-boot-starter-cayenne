package com.softmotions.cayenne.spring;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.cayenne.configuration.rop.client.ClientConstants;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @author Adamansky Anton (adamansky@softmotions.com)
 */
@ConfigurationProperties(prefix = "spring.cayenne.client")
@Validated
public class CayenneClientProperties implements Serializable {

    private Map<String, String> props = new HashMap<>();

    public Map<String, String> getProps() {
        return props;
    }

    public void setProps(Map<String, String> props) {
        this.props = props;
    }

    public void setUrl(String url) {
        getProps().put(ClientConstants.ROP_SERVICE_URL_PROPERTY, url);
    }

    @NotEmpty
    public String getUrl() {
        return getProps().get(ClientConstants.ROP_SERVICE_URL_PROPERTY);
    }

    public void setUser(String user) {
        getProps().put(ClientConstants.ROP_SERVICE_USERNAME_PROPERTY, user);
    }

    public String getUser() {
        return getProps().get(ClientConstants.ROP_SERVICE_USERNAME_PROPERTY);
    }

    public void setPassword(String password) {
        getProps().put(ClientConstants.ROP_SERVICE_PASSWORD_PROPERTY, password);
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("props", props)
                .toString();
    }
}
