package com.example.lifecycle;

public final class ExampleRemoteClient implements AutoCloseable {
    private boolean closed;

    public String fetchSnapshot() {
        if (closed) throw new IllegalStateException("Client is closed");
        return "snapshot";
    }

    @Override
    public void close() {
        closed = true;
    }
}
