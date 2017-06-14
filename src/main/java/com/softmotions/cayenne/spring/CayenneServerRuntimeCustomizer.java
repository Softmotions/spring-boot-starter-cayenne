package com.softmotions.cayenne.spring;

import org.apache.cayenne.configuration.server.ServerRuntimeBuilder;

/**
 * @author Adamansky Anton (adamansky@softmotions.com)
 */
public interface CayenneServerRuntimeCustomizer {

    void customize(ServerRuntimeBuilder builder);
}
