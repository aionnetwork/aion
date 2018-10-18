package org.aion.zero.exceptions;

/**
 * <p>Denotes an exception occuring because a field in the header structure
 * was ruled to be invalid. This can range from an invalid length in
 * the header's structure to a missing field when constructing the header.</p>
 *
 * <p>This exception does not cover violating <b>logical</b> rules, for example
 * an incorrectly added transaction, or a incorrect difficulty calculation</p>
 *
 * @implNote extends {@link Exception} so that callers are forced to deal with
 * the case of an invalid header, if the position is unacceptable then simply
 * wrap the exception in a {@link RuntimeException}
 */
public class HeaderStructureException extends Exception {

    private final String field;
    private final int offset;

    /**
     * @param field name of the field
     * @param offset offset of the field refer to {@link org.aion.zero.types.A0BlockHeader}
     */
    public HeaderStructureException(String field, int offset, String message) {
        super(String.format("HeaderStructure field: %s/%d violated, %s", field, offset, message));
        this.field = field;
        this.offset = offset;
    }

    public HeaderStructureException(String field, int offset) {
        super(String.format("HeaderStructure field: %s/%d violated", field, offset));
        this.field = field;
        this.offset = offset;
    }

    public HeaderStructureException(String field, int offset, Throwable cause) {
        super(String.format("HeaderStructure field: %s/%d violated", field, offset), cause);

        this.field = field;
        this.offset = offset;
    }

    public String getField() {
        return this.field;
    }

    public int getOffset() {
        return this.offset;
    }
}
