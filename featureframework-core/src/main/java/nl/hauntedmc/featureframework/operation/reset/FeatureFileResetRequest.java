package nl.hauntedmc.featureframework.operation.reset;

import java.util.Objects;

/** Typed description of the feature-owned file set to regenerate. */
public sealed interface FeatureFileResetRequest
        permits FeatureFileResetRequest.Config, FeatureFileResetRequest.Messages {

    record Config() implements FeatureFileResetRequest { }

    record Messages(MessageResetScope scope) implements FeatureFileResetRequest {
        public Messages {
            Objects.requireNonNull(scope, "scope");
        }
    }

    static FeatureFileResetRequest config() {
        return new Config();
    }

    static FeatureFileResetRequest messages(MessageResetScope scope) {
        return new Messages(scope);
    }
}
