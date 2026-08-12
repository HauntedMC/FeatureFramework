package nl.hauntedmc.featureframework.paper.command.sync;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class CommandSyncSupportTest {

    private interface SyncCommandsCapable {
        void syncCommands();
    }

    @Test
    void trySyncCommandsInvokesSyncWhenMethodExists() {
        AtomicInteger syncCalls = new AtomicInteger();
        Server server = newServerProxy((proxy, method, args) -> {
            if (method.getName().equals("syncCommands")) {
                syncCalls.incrementAndGet();
                return null;
            }
            return defaultValue(method.getReturnType());
        });
        Plugin plugin = pluginProxy(server);
        List<String> warnings = new ArrayList<>();

        CommandSyncSupport.trySyncCommands(plugin, warnings::add);

        assertEquals(1, syncCalls.get());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void trySyncCommandsLogsWarningWhenInvocationFails() {
        Server server = newServerProxy((proxy, method, args) -> {
            if (method.getName().equals("syncCommands")) {
                throw new RuntimeException("boom");
            }
            return defaultValue(method.getReturnType());
        });
        Plugin plugin = pluginProxy(server);
        List<String> warnings = new ArrayList<>();

        CommandSyncSupport.trySyncCommands(plugin, warnings::add);

        assertFalse(warnings.isEmpty());
    }

    @Test
    void updatePlayersCommandsContinuesWhenOnePlayerThrows() {
        AtomicInteger updates = new AtomicInteger();
        Player ok = playerProxy("ok", false, updates);
        Player bad = playerProxy("bad", true, updates);
        List<String> warnings = new ArrayList<>();

        CommandSyncSupport.updatePlayersCommands(List.of(ok, bad), warnings::add);

        assertEquals(1, updates.get());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("bad"));
    }

    private static Plugin pluginProxy(Server server) {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getServer" -> server;
                    case "getLogger" -> Logger.getLogger("command-sync-test");
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Server newServerProxy(java.lang.reflect.InvocationHandler handler) {
        return (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class, SyncCommandsCapable.class},
                handler
        );
    }

    private static Player playerProxy(String name, boolean throwOnUpdate, AtomicInteger updates) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("updateCommands")) {
                        if (throwOnUpdate) {
                            throw new RuntimeException("fail");
                        }
                        updates.incrementAndGet();
                        return null;
                    }
                    if (method.getName().equals("getName")) {
                        return name;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
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
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
