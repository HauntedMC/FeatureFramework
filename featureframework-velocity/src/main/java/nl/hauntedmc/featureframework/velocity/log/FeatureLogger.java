package nl.hauntedmc.featureframework.velocity.log;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.util.Objects;

/** Adventure logging adapter that prefixes every message with its owning feature name. */
public class FeatureLogger implements FrameworkLogger {
    private final ComponentLogger delegate;
    private final Component prefix;

    public FeatureLogger(ComponentLogger delegate, String featureName) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.prefix = Component.text("[" + Objects.requireNonNull(featureName, "featureName") + "] ");
    }

    public void info(Component message) { delegate.info(prefix.append(message)); }
    public void warn(Component message) { delegate.warn(prefix.append(message)); }
    public void error(Component message) { delegate.error(prefix.append(message)); }
    public void debug(Component message) { delegate.debug(prefix.append(message)); }
    public void trace(Component message) { delegate.trace(prefix.append(message)); }
    @Override public void info(String message) { info(Component.text(message)); }
    public void info(String template, Object argument) { info(replaceFirstPlaceholder(template, argument)); }
    public void warn(String message) { warn(Component.text(message)); }
    @Override public void warn(String message, Throwable failure) {
        delegate.warn(prefix.append(Component.text(message)), failure);
    }
    public void error(String message) { error(Component.text(message)); }
    @Override public void error(String message, Throwable failure) {
        delegate.error(prefix.append(Component.text(message)), failure);
    }
    public void error(String template, Object argument, Throwable failure) {
        error(replaceFirstPlaceholder(template, argument), failure);
    }
    public void debug(String message) { debug(Component.text(message)); }
    public void trace(String message) { trace(Component.text(message)); }

    private static String replaceFirstPlaceholder(String template, Object argument) {
        String text = template == null ? "null" : template;
        int placeholder = text.indexOf("{}");
        if (placeholder < 0) return text + " " + String.valueOf(argument);
        return text.substring(0, placeholder) + argument + text.substring(placeholder + 2);
    }
}
