package nl.hauntedmc.featureframework.velocity.testutil;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class ComponentLoggerRecorder implements InvocationHandler {

    public record Call(String method, Object[] args) {
    }

    private final List<Call> calls = new ArrayList<>();
    private final ComponentLogger logger;

    private ComponentLoggerRecorder() {
        this.logger = (ComponentLogger) Proxy.newProxyInstance(
                ComponentLogger.class.getClassLoader(),
                new Class<?>[]{ComponentLogger.class},
                this
        );
    }

    public static ComponentLoggerRecorder create() {
        return new ComponentLoggerRecorder();
    }

    public ComponentLogger logger() {
        return logger;
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public boolean hasStringArgumentContaining(String method, String fragment) {
        for (Call call : calls) {
            if (!call.method.equals(method) || call.args.length == 0) {
                continue;
            }
            Object first = call.args[0];
            if (first instanceof String text && text.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            if ("toString".equals(method.getName())) {
                return "ComponentLoggerRecorderProxy";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
        }

        calls.add(new Call(method.getName(), args == null ? new Object[0] : args.clone()));
        return defaultValue(method.getReturnType());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
