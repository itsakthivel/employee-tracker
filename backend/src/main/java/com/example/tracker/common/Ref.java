package com.example.tracker.common;

import java.util.UUID;

/** A reference to another record by id and display name. */
public record Ref(UUID id, String name) {}
