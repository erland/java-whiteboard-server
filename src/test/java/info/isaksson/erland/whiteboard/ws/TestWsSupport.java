package info.isaksson.erland.whiteboard.ws;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.websocket.CloseReason;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;

final class TestWsSupport {

    private TestWsSupport() {}

    static TestSessionState newSession(String uri) {
        return newSession(URI.create(uri), Map.of(), null);
    }

    static TestSessionState newSession(URI uri, Map<String, List<String>> requestParams, Principal principal) {
        TestSessionState state = new TestSessionState(uri, requestParams, principal);

        RemoteEndpoint.Async async = (RemoteEndpoint.Async) Proxy.newProxyInstance(
                TestWsSupport.class.getClassLoader(),
                new Class<?>[]{RemoteEndpoint.Async.class},
                new AsyncRemoteHandler(state));

        Session session = (Session) Proxy.newProxyInstance(
                TestWsSupport.class.getClassLoader(),
                new Class<?>[]{Session.class},
                new SessionHandler(state, async));

        state.session = session;
        return state;
    }

    static final class TestSessionState {
        final URI uri;
        final Map<String, Object> userProperties = new HashMap<>();
        final Map<String, List<String>> requestParams;
        final List<String> sentTexts = new ArrayList<>();
        final Principal principal;
        Session session;
        CloseReason closeReason;
        boolean open = true;

        TestSessionState(URI uri, Map<String, List<String>> requestParams, Principal principal) {
            this.uri = uri;
            this.requestParams = requestParams == null ? Map.of() : requestParams;
            this.principal = principal;
        }
    }

    private static final class SessionHandler implements InvocationHandler {
        private final TestSessionState state;
        private final RemoteEndpoint.Async async;
        private final String id = UUID.randomUUID().toString();

        private SessionHandler(TestSessionState state, RemoteEndpoint.Async async) {
            this.state = state;
            this.async = async;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            String name = method.getName();
            return switch (name) {
                case "getUserProperties" -> state.userProperties;
                case "getRequestParameterMap" -> state.requestParams;
                case "getRequestURI" -> state.uri;
                case "getUserPrincipal" -> state.principal;
                case "getAsyncRemote" -> async;
                case "isOpen" -> state.open;
                case "getId" -> id;
                case "close" -> {
                    state.open = false;
                    if (args != null && args.length == 1 && args[0] instanceof CloseReason cr) {
                        state.closeReason = cr;
                    }
                    yield null;
                }
                case "toString" -> "TestSession[" + id + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            };
        }
    }

    private static final class AsyncRemoteHandler implements InvocationHandler {
        private final TestSessionState state;

        private AsyncRemoteHandler(TestSessionState state) {
            this.state = state;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("sendText".equals(name) && args != null && args.length >= 1 && args[0] instanceof String text) {
                state.sentTexts.add(text);
                if (args.length >= 2 && args[1] instanceof SendHandler handler) {
                    handler.onResult(new SendResult());
                }
                return null;
            }
            if ("getSendTimeout".equals(name)) {
                return 0L;
            }
            if ("setSendTimeout".equals(name)) {
                return null;
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            if (Map.class.equals(returnType)) {
                return Collections.emptyMap();
            }
            if (List.class.equals(returnType)) {
                return Collections.emptyList();
            }
            return null;
        }
        if (boolean.class.equals(returnType)) return false;
        if (byte.class.equals(returnType)) return (byte) 0;
        if (short.class.equals(returnType)) return (short) 0;
        if (int.class.equals(returnType)) return 0;
        if (long.class.equals(returnType)) return 0L;
        if (float.class.equals(returnType)) return 0f;
        if (double.class.equals(returnType)) return 0d;
        if (char.class.equals(returnType)) return '\0';
        return null;
    }
}
