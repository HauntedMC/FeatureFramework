package com.example.proxy.lifecycle;

public final class ExampleNetworkClient implements AutoCloseable {
    private boolean closed;

    public String poll() {
        if (closed) throw new IllegalStateException("Client is closed");
        return "network-state";
    }

    @Override public void close() { closed = true; }
}
