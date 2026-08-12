package nl.hauntedmc.featureframework.velocity.command.brigadier;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Locale;
import java.util.stream.Stream;

/** Case-insensitive prefix filtering for custom Brigadier suggestion providers. */
public final class BrigadierSuggestions {

    private BrigadierSuggestions() {
    }

    public static void suggestMatching(Iterable<String> candidates, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(candidate);
            }
        }
    }

    public static void suggestMatching(Stream<String> candidates, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        candidates.filter(candidate -> candidate != null
                        && candidate.toLowerCase(Locale.ROOT).startsWith(remaining))
                .forEach(builder::suggest);
    }
}
