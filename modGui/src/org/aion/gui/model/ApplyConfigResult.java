package org.aion.gui.model;

public class ApplyConfigResult {

    private final boolean succeeded;
    private final String displayableError;
    private final Throwable cause;

    public ApplyConfigResult(boolean succeeded, String displayableError, Throwable cause) {
        this.succeeded = succeeded;
        this.displayableError = displayableError;
        this.cause = cause;
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public String getDisplayableError() {
        return displayableError;
    }

    public Throwable getCause() {
        return cause;
    }
}
