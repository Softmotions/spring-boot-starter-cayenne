package com.softmotions.cayenne.server.modules.jackson.pg;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;

import org.apache.cayenne.access.types.ExtendedType;
import org.apache.cayenne.configuration.Constants;
import org.apache.cayenne.di.Binder;
import org.apache.cayenne.di.Module;
import org.postgresql.util.PGobject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Adamansky Anton (adamansky@softmotions.com)
 */
public class JacksonPostgresCayenneModule implements Module {

    private final ObjectMapper mapper;

    public JacksonPostgresCayenneModule() {
        this(new ObjectMapper());
    }

    public JacksonPostgresCayenneModule(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void configure(Binder binder) {
        binder
                .bindList(Constants.SERVER_DEFAULT_TYPES_LIST)
                .add(new JacksonJSONType(ObjectNode.class.getName()))
                .add(new JacksonJSONType(ArrayNode.class.getName()))
                .add(new JacksonJSONType(JsonNode.class.getName()));
    }

    private class JacksonJSONType implements ExtendedType {

        private final String type;

        JacksonJSONType(String type) {
            this.type = type;
        }

        @Override
        public String getClassName() {
            return type;
        }

        @Override
        public void setJdbcObject(PreparedStatement ps,
                                  Object value,
                                  int pos,
                                  int type,
                                  int scale) throws Exception {
            if (value == null) {
                ps.setNull(pos, type);
            } else {
                PGobject po = new PGobject();
                po.setType("jsonb");
                po.setValue(mapper.writeValueAsString(value));
                ps.setObject(pos, po);
            }
        }

        @Override
        public Object materializeObject(ResultSet rs, int index, int type) throws Exception {
            String value = rs.getString(index);
            if (value == null) {
                return null;
            } else {
                return mapper.readTree(value);
            }
        }

        @Override
        public Object materializeObject(CallableStatement rs, int index, int type) throws Exception {
            String value = rs.getString(index);
            if (value == null) {
                return null;
            } else {
                return mapper.readTree(value);
            }
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JacksonJSONType that = (JacksonJSONType) o;
            return Objects.equals(type, that.type);
        }

        public int hashCode() {
            return Objects.hash(type);
        }

        @Override
        public String toString(Object value) {
            if (value == null) {
                return "NULL";
            }
            return value.toString();
        }
    }
}
