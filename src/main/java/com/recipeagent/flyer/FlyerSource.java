package com.recipeagent.flyer;

/**
 * Where a store's flyer text comes from (F10): online, or a file the user uploaded.
 * <p>
 * Strategy pattern: FlyerService picks a source for each store and calls {@link #fetch}
 * without knowing which kind it is, so a new kind of source needs no change elsewhere.
 */
public interface FlyerSource {

    /** The flyer's text for this store. */
    String fetch(String store) throws FlyerException;

    /** Short description for messages, e.g. "the file nofrills.txt". */
    String describe();
}
