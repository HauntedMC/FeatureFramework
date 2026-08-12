package nl.hauntedmc.featureframework.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

public final class FeatureCommandView {

    public record FeatureListEntry(String name, String version) {
    }

    private FeatureCommandView() {
    }

    public static Component buildFeatureInfoMessage(
            String name,
            boolean enabled,
            String version,
            List<String> pluginDeps,
            List<String> featureDeps
    ) {
        return Component.text("Feature: ", NamedTextColor.GOLD)
                .append(Component.text(name, NamedTextColor.YELLOW))
                .append(Component.text("\n  • Status: ", NamedTextColor.GRAY))
                .append(Component.text(enabled ? "enabled" : "disabled",
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text("\n  • Version: ", NamedTextColor.GRAY))
                .append(Component.text(version == null ? "?" : "v" + version, NamedTextColor.WHITE))
                .append(Component.text("\n  • Plugin deps: ", NamedTextColor.GRAY))
                .append(renderCsvColored(pluginDeps, NamedTextColor.AQUA, NamedTextColor.DARK_GRAY, true))
                .append(Component.text("\n  • Feature deps: ", NamedTextColor.GRAY))
                .append(renderCsvColored(featureDeps, NamedTextColor.GREEN, NamedTextColor.DARK_GRAY, true));
    }

    public static Component renderCsvColored(
            List<String> items,
            NamedTextColor itemColor,
            NamedTextColor commaColor,
            boolean showNone
    ) {
        if (items == null || items.isEmpty()) {
            return Component.text(showNone ? "none" : "", NamedTextColor.DARK_GRAY);
        }
        Component out = Component.empty();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                out = out.append(Component.text(", ", commaColor));
            }
            out = out.append(Component.text(items.get(i), itemColor));
        }
        return out;
    }

    public static Component buildLoadedFeaturesOneLine(List<FeatureListEntry> entries, boolean withVersion) {
        int n = entries.size();
        Component header = Component.text("Enabled Features (", NamedTextColor.YELLOW)
                .append(Component.text(n, NamedTextColor.AQUA))
                .append(Component.text("): ", NamedTextColor.YELLOW));

        Component list = Component.empty();
        for (int i = 0; i < entries.size(); i++) {
            FeatureListEntry entry = entries.get(i);
            String name = entry.name() == null ? "?" : entry.name();
            String version = entry.version() == null ? "?" : entry.version();

            if (i > 0) {
                list = list.append(Component.text(", ", NamedTextColor.DARK_GRAY));
            }

            Component value = Component.text(name, NamedTextColor.GREEN);
            if (withVersion) {
                value = value.append(Component.text(" (", NamedTextColor.DARK_GRAY))
                        .append(Component.text("v" + version, NamedTextColor.WHITE))
                        .append(Component.text(")", NamedTextColor.DARK_GRAY));
            }

            list = list.append(value);
        }

        return header.append(list);
    }
}
