package nl.hauntedmc.featureframework.config;

import nl.hauntedmc.featureframework.toolkit.io.config.ConfigNode;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigView;

import java.util.*;

/**
 * Adds missing configuration defaults without overwriting or reshaping existing data.
 */
public final class ConfigDefaultsMerger {

    private ConfigDefaultsMerger() {
    }

    /**
     * Adds entries whose keys are configuration paths, saving the target at most once. Map values
     * are recursively expanded so partially configured target sections still receive missing
     * nested defaults.
     *
     * <p>An existing target branch always wins. In particular, an existing scalar or list is not
     * replaced when the defaults contain a map at the same path.</p>
     *
     * @return the paths added to the target
     */
    public static Set<String> mergeMissingPaths(ConfigView target, Map<String, ?> defaultPaths) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(defaultPaths, "defaultPaths");
        ConfigNode targetRoot = target.node();
        Map<String, Object> additions = new LinkedHashMap<>();
        defaultPaths.forEach((path, value) -> {
            if (path == null || path.isBlank() || value == null) {
                return;
            }
            collectMissing(
                    ConfigNode.ofRaw(value, "<defaults>." + path),
                    targetRoot.getAt(path),
                    path,
                    additions
            );
        });
        if (!additions.isEmpty()) {
            target.batch(batch -> additions.forEach(batch::put));
        }
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(additions.keySet()));
    }

    private static void collectMissing(
            ConfigNode defaults,
            ConfigNode target,
            String path,
            Map<String, Object> additions
    ) {
        Map<String, ConfigNode> defaultChildren = defaults.children();
        if (!defaultChildren.isEmpty()) {
            if (!target.isNull() && !(target.raw() instanceof Map<?, ?>)) {
                return;
            }
            for (Map.Entry<String, ConfigNode> entry : defaultChildren.entrySet()) {
                String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                collectMissing(entry.getValue(), target.get(entry.getKey()), childPath, additions);
            }
            return;
        }

        if (target.isNull() && !path.isEmpty()) {
            additions.put(path, defaults.raw());
        }
    }
}
