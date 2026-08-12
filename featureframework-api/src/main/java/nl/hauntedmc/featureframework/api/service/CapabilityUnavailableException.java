package nl.hauntedmc.featureframework.api.service;

import java.util.Objects;

/** Thrown when a required optional capability has no active provider. */
public final class CapabilityUnavailableException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    private final Class<?> capabilityType;

    public CapabilityUnavailableException(Class<?> capabilityType) {
        super("Feature capability is unavailable: "
                + Objects.requireNonNull(capabilityType, "capabilityType").getName());
        this.capabilityType = capabilityType;
    }

    public Class<?> capabilityType() {
        return capabilityType;
    }
}
