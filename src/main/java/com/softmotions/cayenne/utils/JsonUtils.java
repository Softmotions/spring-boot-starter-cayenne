package com.softmotions.cayenne.utils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.commons.lang3.ArrayUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;

/**
 * Various JSON Utilities
 *
 * @author Adamansky Anton (adamansky@gmail.com)
 */
public class JsonUtils {

    private JsonUtils() {
    }

    public static Map populateMapByJsonNode(ObjectNode n, Map m, String... keys) {
        Iterator<Map.Entry<String, JsonNode>> fields = n.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> f = fields.next();
            String key = f.getKey();
            if (keys != null && keys.length > 0 && ArrayUtils.indexOf(keys, key) == -1) {
                continue;
            }
            m.put(key, nodeAsObject(f.getValue()));
        }
        return m;
    }

    public static Object nodeAsObject(JsonNode n) {
        JsonNodeType type = n.getNodeType();
        switch (type) {
            case NULL:
            case MISSING:
                return null;
            case STRING:
                return n.asText();
            case NUMBER:
                if (n.isInt()) {
                    return n.asInt();
                } else if (n.isLong()) {
                    return n.asLong();
                } else if (n.isFloatingPointNumber() || n.isFloat() || n.isDouble()) {
                    return n.asDouble();
                }
                break;
            case BOOLEAN:
                return n.asBoolean();
            case OBJECT:
                return populateMapByJsonNode((ObjectNode) n, new HashMap());
            case ARRAY:
                ArrayNode an = (ArrayNode) n;
                ArrayList al = new ArrayList(an.size());
                Iterator<JsonNode> elements = an.elements();
                while (elements.hasNext()) {
                    al.add(nodeAsObject(elements.next()));
                }
                return al;
            case POJO:
                return ((POJONode) n).getPojo();
            case BINARY:
                return ((BinaryNode) n).binaryValue();
        }
        return new AssertionError("Unknown node type");
    }


    public static ObjectNode populateObjectNode(Object bean, ObjectNode o, String... keys) {
        if (bean == null) {
            return o;
        }
        if (bean instanceof Map) {
            return populateObjectNode((Map) bean, o, keys);
        }
        PropertyUtilsBean pu = BeanUtilsBean.getInstance().getPropertyUtils();
        try {
            return populateObjectNode(pu.describe(bean), o, keys);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectNode populateObjectNode(Map map, ObjectNode o, String... keys) {
        if (map == null) {
            return o;
        }
        for (final Object oe : map.entrySet()) {
            Map.Entry e = (Map.Entry) oe;
            String key = (String) e.getKey();
            Object val = e.getValue();
            if (keys != null && keys.length > 0 && ArrayUtils.indexOf(keys, key) == -1) {
                continue;
            }
            if (val == null) {
                o.putNull(key);
                continue;
            }
            if (val instanceof String) {
                o.put(key, (String) val);
            } else if (val instanceof Number) {
                if (val instanceof Float || val instanceof Double) {
                    o.put(key, ((Number) val).doubleValue());
                } else {
                    o.put(key, ((Number) val).longValue());
                }
            } else if (val instanceof Date) {
                o.put(key, ((Date) val).getTime());
            } else if (val instanceof Boolean) {
                o.put(key, (Boolean) val);
            } else if (val instanceof Map) {
                populateObjectNode((Map) val, o.putObject(key));
            } else {
                o.putPOJO(key, val);
            }
        }
        return o;
    }
}
