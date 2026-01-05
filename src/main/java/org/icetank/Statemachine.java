package org.icetank;


/*
 * @author IceTank
 * @since 05.01.2026
 */
public interface Statemachine {
    void tick();
    void start();
    boolean isComplete();
    boolean isFailed();
    boolean isSuccessful();
}
