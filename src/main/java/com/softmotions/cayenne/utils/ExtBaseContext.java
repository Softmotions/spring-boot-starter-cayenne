package com.softmotions.cayenne.utils;

import org.apache.cayenne.BaseContext;
import org.apache.cayenne.ObjectContext;

/**
 * @author Adamansky Anton (adamansky@softmotions.com)
 */
public abstract class ExtBaseContext extends BaseContext {
    public static ObjectContext getThreadObjectContextNull() throws IllegalStateException {
        return threadObjectContext.get();
    }
}
