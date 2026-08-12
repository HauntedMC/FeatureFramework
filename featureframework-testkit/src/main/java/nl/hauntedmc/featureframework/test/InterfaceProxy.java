package nl.hauntedmc.featureframework.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class InterfaceProxy {

    private InterfaceProxy() {
    }

    public static <T> T of(Class<T> type, Map<String, Function<Object[], Object>> handlers) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handlers, "handlers");

        InvocationHandler invocationHandler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            Function<Object[], Object> fn = handlers.get(method.getName());
            if (fn != null) {
                Object[] safeArgs = args == null ? new Object[0] : args;
                return fn.apply(safeArgs);
            }
            return defaultValue(method.getReturnType());
        };

        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, invocationHandler);
        return type.cast(proxy);
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "InterfaceProxy(" + proxy.getClass().getInterfaces()[0].getSimpleName() + ")";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
            default -> null;
        };
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
