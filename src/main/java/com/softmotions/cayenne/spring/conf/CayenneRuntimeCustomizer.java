package com.softmotions.cayenne.spring.conf;

import org.apache.cayenne.configuration.server.ServerRuntimeBuilder;

/**
 * @author Adamansky Anton (adamansky@softmotions.com)
 */
public interface CayenneRuntimeCustomizer {

    void customize(ServerRuntimeBuilder builder);
}
