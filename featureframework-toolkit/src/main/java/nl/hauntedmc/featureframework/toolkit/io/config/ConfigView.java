package nl.hauntedmc.featureframework.toolkit.io.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Typed, thread-safe CRUD view over a YAML file, optionally rooted at a base path. */
public class ConfigView {
    public final YamlFile file;
    protected final String base;

    public ConfigView(YamlFile file, String basePath) {
        this.file = Objects.requireNonNull(file, "file");
        this.base = basePath == null ? "" : basePath;
    }

    public ConfigView scope(String childBase) { return new ConfigView(file, base(childBase)); }

    protected String base(String key) {
        if (base.isEmpty()) return key == null || key.isEmpty() ? "" : key;
        return base + (key == null || key.isEmpty() ? "" : "." + key);
    }

    public Object get(String key) { return file.getRaw(base(key)); }
    public <T> T get(String key, Class<T> type) { return ConfigTypes.convert(get(key), type); }
    public <T> T get(String key, Class<T> type, T def) { return ConfigTypes.convertOrDefault(get(key), type, def); }
    public <T> Optional<T> getOptional(String key, Class<T> type) {
        return Optional.ofNullable(get(key, type));
    }
    public boolean contains(String key) { return node(key).isPresent(); }
    public <T> List<T> getList(String key, Class<T> type) { return ConfigTypes.convertList(get(key), type); }

    public <T> List<T> getList(String key, Class<T> type, List<T> def) {
        try {
            List<T> converted = ConfigTypes.convertList(get(key), type);
            return converted != null ? converted : def;
        } catch (RuntimeException ignored) {
            return def;
        }
    }

    public <V> Map<String, V> getMapValues(String key, Class<V> type) {
        return ConfigTypes.convertMapValues(get(key), type);
    }

    public <V> Map<String, V> getMapValues(String key, Class<V> type, Map<String, V> def) {
        try {
            Map<String, V> converted = ConfigTypes.convertMapValues(get(key), type);
            return converted != null ? converted : def;
        } catch (RuntimeException ignored) {
            return def;
        }
    }

    public ConfigNode node() { return ConfigNode.ofRaw(file.getRaw(base), base.isEmpty() ? "<root>" : base); }
    public ConfigNode node(String key) { return ConfigNode.ofRaw(get(key), base(key)); }
    public ConfigNode nodeAt(String path) { return node().getAt(path); }
    public <T> T getAt(String path, Class<T> type) { return node().getAt(path).asRequired(type); }
    public <T> T getAt(String path, Class<T> type, T def) { return node().getAt(path).as(type, def); }
    public <T> Optional<T> getAtOptional(String path, Class<T> type) {
        return node().getAt(path).asOptional(type);
    }

    public void put(String path, Object value) { file.setRawAndSave(base(path), value); }
    public void remove(String path) { put(path, null); }

    /** Applies multiple path/value updates in one atomic file write. */
    public void putAll(Map<String, ?> values) {
        Map<String, ?> required = Objects.requireNonNull(values, "values");
        if (required.isEmpty()) return;
        batch(batch -> batch.putAll(required));
    }

    public boolean putIfAbsent(String path, Object value) {
        String absolute = base(path);
        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode candidate = file.copyRootUnsafe();
            CommentedConfigurationNode node = candidate.node(YamlFile.splitPath(absolute));
            if (!node.virtual()) return false;
            node.set(value);
            file.commitCandidateUnsafe(candidate);
            return true;
        } catch (SerializationException exception) {
            throw new IllegalStateException("Unable to set absent configuration value: " + absolute, exception);
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    public <T> T compute(String path, Class<T> type, UnaryOperator<T> update, Supplier<T> init)
            throws SerializationException {
        Objects.requireNonNull(update, "update");
        String absolute = base(path);
        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode candidate = file.copyRootUnsafe();
            CommentedConfigurationNode node = candidate.node(YamlFile.splitPath(absolute));
            T current;
            try { current = node.virtual() ? null : ConfigTypes.convert(node.get(Object.class), type); }
            catch (RuntimeException ignored) { current = null; }
            if (current == null && init != null) current = init.get();
            T next = Objects.requireNonNull(update.apply(current), "update returned null");
            node.set(next);
            file.commitCandidateUnsafe(candidate);
            return next;
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    public void appendToList(String path, Object value) {
        String absolute = base(path);
        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode candidate = file.copyRootUnsafe();
            CommentedConfigurationNode node = candidate.node(YamlFile.splitPath(absolute));
            List<Object> list = mutableRawList(node);
            list.add(value);
            node.raw(list);
            file.commitCandidateUnsafe(candidate);
        } catch (ConfigPersistenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to append configuration list: " + absolute, exception);
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    public int removeFromList(String path, Predicate<Object> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        String absolute = base(path);
        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode candidate = file.copyRootUnsafe();
            CommentedConfigurationNode node = candidate.node(YamlFile.splitPath(absolute));
            List<Object> list = mutableRawList(node);
            int before = list.size();
            list.removeIf(predicate);
            int removed = before - list.size();
            if (removed > 0) {
                node.raw(list);
                file.commitCandidateUnsafe(candidate);
            }
            return removed;
        } catch (ConfigPersistenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return 0;
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    private static List<Object> mutableRawList(CommentedConfigurationNode node) {
        Object raw = node.raw();
        if (raw == null) return new ArrayList<>();
        if (raw instanceof List<?> list) return new ArrayList<>(list);
        throw new IllegalStateException("Configuration value is not a list: " + node.path());
    }

    public void batch(BatchMutation transaction) {
        Objects.requireNonNull(transaction, "transaction");
        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode candidate = file.copyRootUnsafe();
            Batch batch = new Batch(candidate);
            transaction.accept(batch);
            if (batch.changed) file.commitCandidateUnsafe(candidate);
        } catch (SerializationException exception) {
            throw new IllegalStateException("Unable to apply atomic configuration update at "
                    + (base.isEmpty() ? "<root>" : base) + '.', exception);
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    @FunctionalInterface
    public interface BatchMutation {
        void accept(Batch batch) throws SerializationException;
    }

    public void mutateRaw(Consumer<CommentedConfigurationNode> mutator) { file.mutateAndSave(mutator); }

    public final class Batch {
        private final CommentedConfigurationNode root;
        private boolean changed;
        private Batch(CommentedConfigurationNode root) { this.root = root; }

        public Batch put(String path, Object value) {
            set(root.node(YamlFile.splitPath(base(path))), value, path);
            changed = true;
            return this;
        }

        public Batch putAll(Map<String, ?> values) {
            Objects.requireNonNull(values, "values").forEach(this::put);
            return this;
        }

        public Batch putIfAbsent(String path, Object value) {
            CommentedConfigurationNode node = root.node(YamlFile.splitPath(base(path)));
            if (node.virtual()) {
                set(node, value, path);
                changed = true;
            }
            return this;
        }

        public <T> Batch compute(String path, Class<T> type, UnaryOperator<T> update, Supplier<T> init) {
            CommentedConfigurationNode node = root.node(YamlFile.splitPath(base(path)));
            T current;
            try { current = node.virtual() ? null : ConfigTypes.convert(node.get(Object.class), type); }
            catch (SerializationException | RuntimeException ignored) { current = null; }
            if (current == null && init != null) current = init.get();
            set(node, Objects.requireNonNull(update.apply(current), "update returned null"), path);
            changed = true;
            return this;
        }

        public Batch appendToList(String path, Object value) {
            CommentedConfigurationNode node = root.node(YamlFile.splitPath(base(path)));
            List<Object> list = mutableRawList(node);
            list.add(value);
            node.raw(list);
            changed = true;
            return this;
        }

        public Batch removeFromList(String path, Predicate<Object> predicate) {
            CommentedConfigurationNode node = root.node(YamlFile.splitPath(base(path)));
            List<Object> list = mutableRawList(node);
            int before = list.size();
            list.removeIf(predicate);
            if (list.size() != before) {
                node.raw(list);
                changed = true;
            }
            return this;
        }

        public Batch remove(String path) {
            set(root.node(YamlFile.splitPath(base(path))), null, path);
            changed = true;
            return this;
        }

        private void set(CommentedConfigurationNode node, Object value, String path) {
            try {
                node.set(value);
            } catch (SerializationException exception) {
                throw new IllegalStateException("Unable to serialize configuration value at '" + base(path) + "'.",
                        exception);
            }
        }
    }

    public ConfigView root() { return base.isEmpty() ? this : new ConfigView(file, ""); }
    public ConfigView at(String path) { return path == null || path.isBlank() ? root() : new ConfigView(file, path); }
    public ConfigView globals() { return at("global"); }
    public ConfigView features() { return at("features"); }
}
