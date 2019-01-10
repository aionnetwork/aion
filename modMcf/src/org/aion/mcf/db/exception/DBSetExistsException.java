package org.aion.mcf.db.exception;

/** DBSet exist exception. */
public class DBSetExistsException extends Exception {
    private static final long serialVersionUID = 2774651839936217934L;

    public DBSetExistsException() {
        super();
    }

    public DBSetExistsException(String message) {
        super(message);
    }

    public DBSetExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    public DBSetExistsException(Throwable cause) {
        super(cause);
    }
}
