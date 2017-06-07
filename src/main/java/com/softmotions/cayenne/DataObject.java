package com.softmotions.cayenne;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.cayenne.Cayenne;
import org.apache.cayenne.CayenneContext;
import org.apache.cayenne.CayenneDataObject;
import org.apache.cayenne.DataChannel;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.PersistenceState;
import org.apache.cayenne.Persistent;
import org.apache.cayenne.PersistentObject;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.PropertyUtilsBean;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.softmotions.cayenne.utils.JsonUtils;

/**
 * @author Adamansky Anton (adamansky@gmail.com)
 */
@SuppressWarnings({"ChainOfInstanceofChecks", "ObjectEquality"})
public abstract class DataObject extends CayenneDataObject {

    public Long getId() {
        return (objectId != null ? Cayenne.longPKForObject(this) : null);
    }

    protected ObjectContext getObjectContextOrFail() throws IllegalStateException {
        ObjectContext ctx = objectContext;
        //noinspection ObjectEquality
        if (ctx == null || ctx.getClass() == DumbCayenneContext.class) {
            throw new IllegalStateException("Data object is not bound to cayenne object context");
        }
        return ctx;
    }

    public boolean hasDumbObjectContext() {
        //noinspection ObjectEquality
        return (getObjectContext() != null && getObjectContext().getClass() == DumbCayenneContext.class);
    }

    @Override
    public void addToManyTarget(String relName, org.apache.cayenne.DataObject value, boolean setReverse) {
        if (getObjectContext() == null) {
            setObjectContext(new DumbCayenneContext());
        }
        super.addToManyTarget(relName, value, setReverse);
    }

    @Override
    public void setToOneTarget(String relationshipName, org.apache.cayenne.DataObject value, boolean setReverse) {
        if (getObjectContext() == null) {
            setObjectContext(new DumbCayenneContext());
        }
        super.setToOneTarget(relationshipName, value, setReverse);
    }

    @Override
    protected void setReverseRelationship(String relName, org.apache.cayenne.DataObject val) {
        if (hasDumbObjectContext()) {
            return;
        }
        super.setReverseRelationship(relName, val);
    }

    @Override
    protected void unsetReverseRelationship(String relName, org.apache.cayenne.DataObject val) {
        if (hasDumbObjectContext()) {
            return;
        }
        super.unsetReverseRelationship(relName, val);
    }

    /**
     * Merge current object with specified sources object.
     * Source object can be either of POJO bean, Map or ObjectNode
     */
    public void mergeAllPlain(Object src) throws Exception {
        if (src instanceof ObjectNode) {
            src = JsonUtils.populateMapByJsonNode((ObjectNode) src, new HashMap<>());
        }
        if (src instanceof Map) {
            //noinspection unchecked
            copyNotNullMap(this, (Map<String, Object>) src, false, false);
        } else {
            copyNotNullProperties(this, src, false, false);
        }
    }

    /**
     * Merge current object with specified sources object.
     * Source object can be either of POJO bean, Map or ObjectNode
     */
    public void mergeNotNullPlain(Object src) throws Exception {
        if (src instanceof ObjectNode) {
            src = JsonUtils.populateMapByJsonNode((ObjectNode) src, new HashMap<>());
        }
        if (src instanceof Map) {
            copyNotNullMap(this, (Map<String, Object>) src, false, false);
        } else {
            copyNotNullProperties(this, src, false, false);
        }
    }

    private Object preprocessPropValue(Object value, Class<?> destType) throws Exception {
        if (destType != null) {
            if (destType == Date.class || Date.class.isAssignableFrom(destType)) {
                if (value instanceof String) {
                    try {
                        value = Long.parseLong((String) value);
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (value instanceof Number) {
                    value = new Date(((Number) value).longValue());
                }
            } else if (destType == Integer.class) {
                if (value instanceof String) {
                    value = Integer.valueOf((String) value);
                } else if (value instanceof Number) {
                    value = ((Number) value).intValue();
                }
            } else if (destType == Long.class) {
                if (value instanceof String) {
                    value = Long.valueOf((String) value);
                } else if (value instanceof Number) {
                    value = ((Number) value).longValue();
                }
            }
        }
        return value;
    }

    @SuppressWarnings("OverlyNestedMethod")
    private void copyNotNullProperties(Object dest, Object orig, boolean allowNulls, boolean allowRelations) throws Exception {
        if (orig == null) {
            return;
        }
        PropertyUtilsBean propertyUtils = BeanUtilsBean.getInstance().getPropertyUtils();
        PropertyDescriptor[] origDescriptors = propertyUtils.getPropertyDescriptors(orig);
        for (int i = 0; i < origDescriptors.length; i++) {
            String name = origDescriptors[i].getName();
            PropertyDescriptor destDescriptor = propertyUtils.getPropertyDescriptor(dest, name);
            Class<?> destType = destDescriptor != null ? destDescriptor.getPropertyType() : null;
            if (propertyUtils.isReadable(orig, name) && propertyUtils.isWriteable(dest, name)) {
                try {
                    Method writeMethod = propertyUtils.getWriteMethod(destDescriptor);
                    Class dc = writeMethod.getDeclaringClass();
                    //noinspection ObjectEquality
                    if (dc == CayenneDataObject.class || dc == PersistentObject.class || dc == DataObject.class) {
                        continue;
                    }
                    Object value = propertyUtils.getSimpleProperty(orig, name);
                    if (!allowNulls && value == null) {
                        continue;
                    }
                    if (!allowRelations) {
                        if ((destType != null && Persistent.class.isAssignableFrom(destType)) || value instanceof Persistent) {
                            continue;
                        }
                        if (value instanceof Map
                            && !((Map) value).isEmpty()
                            && (((Map) value).values().iterator().next()) instanceof Persistent) {
                            continue;
                        }
                        if (value instanceof Iterable) {
                            Iterator iter = ((Iterable) value).iterator();
                            if (iter.hasNext() && iter.next() instanceof Persistent) {
                                continue;
                            }
                        }
                    }
                    propertyUtils.setSimpleProperty(dest, name, preprocessPropValue(value, destType));
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
    }

    @SuppressWarnings("OverlyNestedMethod")
    private void copyNotNullMap(Object dest, Map<String, Object> orig, boolean allowNulls, boolean allowRelations) throws Exception {
        if (orig == null) {
            return;
        }
        PropertyUtilsBean propertyUtils = BeanUtilsBean.getInstance().getPropertyUtils();
        for (Map.Entry<String, Object> entry : orig.entrySet()) {
            String name = entry.getKey();
            PropertyDescriptor destDescriptor = propertyUtils.getPropertyDescriptor(dest, name);
            Class<?> destType = destDescriptor != null ? destDescriptor.getPropertyType() : null;
            if (propertyUtils.isWriteable(dest, name)) {
                try {
                    Method writeMethod = propertyUtils.getWriteMethod(destDescriptor);
                    Class dc = writeMethod.getDeclaringClass();
                    //noinspection ObjectEquality
                    if (dc == CayenneDataObject.class || dc == PersistentObject.class || dc == DataObject.class) {
                        continue;
                    }
                    Object value = orig.get(name);
                    if (!allowNulls && value == null) {
                        continue;
                    }
                    if (!allowRelations) {
                        if ((destType != null && Persistent.class.isAssignableFrom(destType)) || value instanceof Persistent) {
                            continue;
                        }
                        if (value instanceof Map
                            && !((Map) value).isEmpty()
                            && (((Map) value).values().iterator().next()) instanceof Persistent) {
                            continue;
                        }
                        if (value instanceof Iterable) {
                            Iterator iter = ((Iterable) value).iterator();
                            if (iter.hasNext() && iter.next() instanceof Persistent) {
                                continue;
                            }
                        }
                    }
                    propertyUtils.setSimpleProperty(dest, name, preprocessPropValue(value, destType));
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
    }

    public static class DumbCayenneContext extends CayenneContext {
        /**
         * Creates a new CayenneContext with no channel and disabled graph events.
         */
        public DumbCayenneContext() {
        }

        private DumbCayenneContext(DataChannel channel) {
            super(channel);
        }

        private DumbCayenneContext(DataChannel channel, boolean changeEventsEnabled, boolean lifecyleEventsEnabled) {
            super(channel, changeEventsEnabled, lifecyleEventsEnabled);
        }

        @Override
        public void registerNewObject(Object object) {
        }


        @Override
        protected boolean attachToRuntimeIfNeeded() {
            return false;
        }

        @Override
        public void propertyChanged(Persistent object, String property, Object oldValue, Object newValue) {
        }

        @Override
        public void prepareForAccess(Persistent object, String property, boolean lazyFaulting) {
        }

        @Override
        protected void injectInitialValue(Object obj) {
            ((Persistent) obj).setPersistenceState(PersistenceState.TRANSIENT);
        }
    }
}