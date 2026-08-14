package nl.hauntedmc.featureframework.paper.integration.placeholder;

import nl.hauntedmc.featureframework.paper.localization.PaperMessageDecorator;

/** PlaceholderAPI-backed message decoration for hosts that opt into the integration artifact. */
public final class PlaceholderMessageDecorator {
    private PlaceholderMessageDecorator() { }

    public static PaperMessageDecorator create() {
        return PlaceholderAPIHook::applyPlaceholders;
    }
}
