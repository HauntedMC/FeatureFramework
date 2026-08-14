package nl.hauntedmc.featureframework.paper.ui;

import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResources;
import nl.hauntedmc.featureframework.paper.ui.inventory.menu.FeatureGUIManager;
import nl.hauntedmc.featureframework.resource.FeatureResourceContributor;
import nl.hauntedmc.featureframework.resource.FeatureResourceRequest;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/** Installs feature-owned menu infrastructure into a Paper host. */
public final class PaperUiContributor {
    private PaperUiContributor() { }

    public static FeatureResourceContributor<PaperFeatureResources> create(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new FeatureResourceContributor<>() {
            @Override public Class<?> extensionType() { return PaperUiResources.class; }

            @Override
            public void contribute(FeatureResourceRequest request, PaperFeatureResources resources) {
                FeatureGUIManager menus = new FeatureGUIManager(plugin, resources.tasks());
                resources.listeners().registerListener(menus);
                resources.ownership().own(menus, FeatureGUIManager::shutdown);
                resources.extensions().register(PaperUiResources.KEY, new PaperUiResources(menus));
            }
        };
    }
}
