package nl.hauntedmc.featureframework.loader;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Reusable graph traversal and ordered start/stop operations for feature cascades. */
public final class FeatureGraphLifecycle {
    private FeatureGraphLifecycle() { }

    public static Set<String> dependentClosure(
            String root,
            Function<String, ? extends Iterable<String>> dependentProvider,
            Predicate<String> includeDependent
    ) {
        LinkedHashSet<String> closure = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        closure.add(root);
        pending.add(root);
        while (!pending.isEmpty()) {
            String dependency = pending.removeFirst();
            for (String dependent : dependentProvider.apply(dependency)) {
                if (includeDependent.test(dependent) && closure.add(dependent)) pending.addLast(dependent);
            }
        }
        return Set.copyOf(closure);
    }

    public static Throwable stopReverse(List<String> loadOrder, Function<String, Throwable> stopFeature) {
        Throwable failure = null;
        ListIterator<String> iterator = loadOrder.listIterator(loadOrder.size());
        while (iterator.hasPrevious()) {
            Throwable addition = stopFeature.apply(iterator.previous());
            if (addition == null) continue;
            if (failure == null) failure = addition;
            else failure.addSuppressed(addition);
        }
        return failure;
    }

    public static boolean start(List<String> loadOrder, Predicate<String> startFeature) {
        for (String feature : loadOrder) {
            if (!startFeature.test(feature)) return false;
        }
        return true;
    }
}
