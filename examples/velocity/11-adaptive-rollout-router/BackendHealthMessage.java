package com.example.rollouts;

import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;

/** Wire DTO. In a multi-project network this class belongs in a tiny shared contracts module. */
public final class BackendHealthMessage extends AbstractEventMessage {
    public static final String TYPE = "example.backend-health.v1";

    private String server;
    private boolean acceptingPlayers;
    private int online;
    private long observedAtEpochMillis;

    @SuppressWarnings("unused")
    private BackendHealthMessage() {
        super(TYPE);
    }

    public BackendHealthMessage(String server, boolean acceptingPlayers, int online, long observedAtEpochMillis) {
        super(TYPE);
        this.server = server;
        this.acceptingPlayers = acceptingPlayers;
        this.online = online;
        this.observedAtEpochMillis = observedAtEpochMillis;
    }

    public String server() { return server; }
    public boolean acceptingPlayers() { return acceptingPlayers; }
    public int online() { return online; }
    public long observedAtEpochMillis() { return observedAtEpochMillis; }
}
