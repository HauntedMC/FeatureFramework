package nl.hauntedmc.featureframework.toolkit.io.localization;

import java.util.Arrays;
import java.util.List;

/** Supported framework localization languages. */
public enum Language {
    AUTO("AUTO", false),
    NL("NL", true),
    EN("EN", true);

    private final String code;
    private final boolean localizable;

    Language(String code, boolean localizable) {
        this.code = code;
        this.localizable = localizable;
    }

    public String getFileName() { return "messages_" + code + ".yml"; }
    public boolean isLocalizable() { return localizable; }
    public static List<Language> localizableValues() {
        return Arrays.stream(values()).filter(Language::isLocalizable).toList();
    }
}
