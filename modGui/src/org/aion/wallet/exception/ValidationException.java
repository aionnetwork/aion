package org.aion.wallet.exception;

public class ValidationException extends Exception{

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(Throwable cause) {
        super(cause);
    }

    @Override
    public String getMessage() {
        return getCause() != null ? getCause().getMessage() : super.getMessage();
    }
}
