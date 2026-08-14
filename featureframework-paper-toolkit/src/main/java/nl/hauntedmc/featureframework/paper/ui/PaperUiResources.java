package nl.hauntedmc.featureframework.paper.ui;

import nl.hauntedmc.featureframework.paper.ui.inventory.menu.FeatureGUIManager;
import nl.hauntedmc.featureframework.resource.ResourceKey;

import java.util.Objects;

/** Feature-scoped Paper UI services supplied by the optional toolkit artifact. */
public record PaperUiResources(FeatureGUIManager menus) {
    public static final ResourceKey<PaperUiResources> KEY = ResourceKey.of(PaperUiResources.class);

    public PaperUiResources {
        Objects.requireNonNull(menus, "menus");
    }
}
