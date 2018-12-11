package org.aion.mcf.db.exception;

/** Invalid file path exception. */
public class InvalidFilePathException extends Exception {

    private static final long serialVersionUID = -947565871490808141L;

    public InvalidFilePathException() {
        super();
    }

    public InvalidFilePathException(String message) {
        super(message);
    }

    public InvalidFilePathException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidFilePathException(Throwable cause) {
        super(cause);
    }
}
