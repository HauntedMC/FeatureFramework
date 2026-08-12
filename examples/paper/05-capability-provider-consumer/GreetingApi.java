package com.example.myplugin.api;

import java.util.UUID;

public interface GreetingApi {
    String greetingFor(UUID playerId);
}
