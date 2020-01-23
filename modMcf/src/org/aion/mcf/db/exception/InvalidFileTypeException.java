package org.aion.mcf.db.exception;

/** Invalid file path exception. */
public class InvalidFileTypeException extends Exception {

    private static final long serialVersionUID = -7022793792489250954L;

    public InvalidFileTypeException() {
        super();
    }

    public InvalidFileTypeException(String message) {
        super(message);
    }
}
