package com.ticketforge.infrastructure.db;

import org.springframework.transaction.TransactionDefinition;

/** Named isolation constants so call sites read as intent, not magic ints. */
public final class TransactionDefinitionIsolation {
    public static final int DEFAULT = TransactionDefinition.ISOLATION_DEFAULT;
    public static final int READ_COMMITTED = TransactionDefinition.ISOLATION_READ_COMMITTED;
    public static final int REPEATABLE_READ = TransactionDefinition.ISOLATION_REPEATABLE_READ;
    public static final int SERIALIZABLE = TransactionDefinition.ISOLATION_SERIALIZABLE;

    private TransactionDefinitionIsolation() {
    }
}
