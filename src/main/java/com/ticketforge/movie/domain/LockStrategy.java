package com.ticketforge.movie.domain;

public enum LockStrategy {
    /** SELECT ... FOR UPDATE on all requested rows, then mutate. */
    PESSIMISTIC,
    /** One conditional UPDATE per seat; rows-affected decides the winner. */
    ATOMIC
}
