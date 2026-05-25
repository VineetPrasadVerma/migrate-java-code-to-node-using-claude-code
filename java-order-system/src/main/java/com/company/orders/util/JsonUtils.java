package com.company.orders.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;

/**
 * Thin wrapper around Jackson ObjectMapper with consistent configuration.
 *
 * LEGACY NOTE: This class uses a static singleton ObjectMapper, which is thread-safe
 * for the read/write operations used here. The INDENT_OUTPUT flag is intentionally
 * enabled for all output — the original design targeted human-readable CLI output
 * rather than API responses.
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
        // Do not fail on unknown properties — allows partial JSON inputs for testing
        MAPPER.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private JsonUtils() {}

    public static <T> T fromFile(String path, Class<T> type) throws IOException {
        return MAPPER.readValue(new File(path), type);
    }

    public static <T> T fromJson(String json, Class<T> type) throws IOException {
        return MAPPER.readValue(json, type);
    }

    public static String toJson(Object obj) throws IOException {
        return MAPPER.writeValueAsString(obj);
    }
}
