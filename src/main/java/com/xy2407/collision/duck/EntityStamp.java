package com.xy2407.collision.duck;

/**
 * Per-entity query stamp used to deliver each entity once per grid query
 */
public interface EntityStamp {

    long nudgeQueryStamp();

    void nudgeSetQueryStamp(long value);
}
