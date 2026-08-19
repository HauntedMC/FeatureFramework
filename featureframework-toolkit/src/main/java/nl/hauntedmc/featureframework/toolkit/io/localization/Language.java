package nl.hauntedmc.featureframework.toolkit.io.localization;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Supported framework localization languages. */
public enum Language {
    AUTO("AUTO", false),
    NL("NL", true),
    EN("EN", true);

    private static final List<Language> LOCALIZABLE_VALUES = Arrays.stream(values())
            .filter(Language::isLocalizable)
            .toList();

    private final String code;
    private final boolean localizable;

    Language(String code, boolean localizable) {
        this.code = code;
        this.localizable = localizable;
    }

    public String code() { return code; }
    public String getFileName() { return "messages_" + code + ".yml"; }
    public boolean isLocalizable() { return localizable; }

    /** Resolves a language code case-insensitively without throwing for external input. */
    public static Optional<Language> fromCode(String code) {
        if (code == null) return Optional.empty();
        String normalized = code.trim();
        if (normalized.isEmpty()) return Optional.empty();
        return Arrays.stream(values())
                .filter(language -> language.code.equalsIgnoreCase(normalized))
                .findFirst();
    }

    public static List<Language> localizableValues() {
        return LOCALIZABLE_VALUES;
    }
}
