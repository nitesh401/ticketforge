package com.ticketforge.common;

import java.util.UUID;

/** Public identifiers: opaque, unguessable, and independent of database sequences. */
public final class Ids {
    private Ids() {
    }

    public static String hold() {
        return generate("HOLD-");
    }

    public static String booking() {
        return generate("BKG-");
    }

    public static String payment() {
        return generate("PAY-");
    }

    public static String reservation() {
        return generate("RES-");
    }

    public static String pnr() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private static String generate(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
