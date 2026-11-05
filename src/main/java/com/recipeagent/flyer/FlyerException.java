package com.recipeagent.flyer;

/**
 * A flyer could not be read (F10), e.g. the online source is not set up or unreachable,
 * or an uploaded file is empty or not text. The message is meant for the user.
 */
public class FlyerException extends Exception {

    private static final long serialVersionUID = 1L;
    private final boolean uploadInstead;

    /** @param uploadInstead true if the user should upload the flyer file instead (UC10 1a) */
    public FlyerException(String message, boolean uploadInstead) {
        super(message);
        this.uploadInstead = uploadInstead;
    }

    /** True if uploading the flyer as a file would solve the problem. */
    public boolean isUploadInstead() {
        return uploadInstead;
    }
}
