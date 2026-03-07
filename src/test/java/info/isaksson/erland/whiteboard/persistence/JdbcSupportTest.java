package info.isaksson.erland.whiteboard.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JdbcSupportTest {

    @Test
    void setNullableTimestamp_setsNullWhenValueMissing() throws Exception {
        RecordingPreparedStatement recorder = new RecordingPreparedStatement();
        JdbcSupport.setNullableTimestamp(recorder.proxy(), 2, null);
        assertEquals(2, recorder.lastIndex);
        assertEquals(Types.TIMESTAMP_WITH_TIMEZONE, recorder.lastSqlType);
        assertNull(recorder.lastValue);
    }

    @Test
    void setNullableInteger_setsIntWhenValuePresent() throws Exception {
        RecordingPreparedStatement recorder = new RecordingPreparedStatement();
        JdbcSupport.setNullableInteger(recorder.proxy(), 3, 7);
        assertEquals(3, recorder.lastIndex);
        assertEquals(7, recorder.lastValue);
    }

    @Test
    void getInstant_returnsConvertedTimestamp() throws Exception {
        Instant now = Instant.parse("2026-03-07T12:34:56Z");
        ResultSet rs = resultSetWithTimestamps(Map.of("created_at", Timestamp.from(now)));
        assertEquals(now, JdbcSupport.getInstant(rs, "created_at"));
    }

    @Test
    void getInstant_returnsNullForMissingTimestamp() throws Exception {
        ResultSet rs = resultSetWithTimestamps(new HashMap<>());
        assertNull(JdbcSupport.getInstant(rs, "created_at"));
    }

    private static ResultSet resultSetWithTimestamps(Map<String, Timestamp> values) {
        return (ResultSet) Proxy.newProxyInstance(
                JdbcSupportTest.class.getClassLoader(),
                new Class[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("getTimestamp".equals(method.getName())) {
                        return values.get((String) args[0]);
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().equals(int.class)) {
                        return 0;
                    }
                    if (method.getReturnType().equals(long.class)) {
                        return 0L;
                    }
                    return null;
                });
    }

    private static final class RecordingPreparedStatement {
        Integer lastIndex;
        Integer lastSqlType;
        Object lastValue;

        PreparedStatement proxy() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    JdbcSupportTest.class.getClassLoader(),
                    new Class[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "setNull" -> {
                                lastIndex = (Integer) args[0];
                                lastSqlType = (Integer) args[1];
                                lastValue = null;
                                return null;
                            }
                            case "setTimestamp", "setInt", "setString" -> {
                                lastIndex = (Integer) args[0];
                                lastValue = args[1];
                                return null;
                            }
                            default -> {
                                if (method.getReturnType().equals(boolean.class)) {
                                    return false;
                                }
                                if (method.getReturnType().equals(int.class)) {
                                    return 0;
                                }
                                if (method.getReturnType().equals(long.class)) {
                                    return 0L;
                                }
                                return null;
                            }
                        }
                    });
        }
    }
}
