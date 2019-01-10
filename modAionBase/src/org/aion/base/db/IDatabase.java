package org.aion.base.db;

import java.util.Optional;

/**
 * Interface for database connection functionality, to be implemented by all database
 * implementations.
 *
 * @author Alexandra Roatis
 * @implNote Note on how a DB is defined and discussed in this driver: a DB is simply a persistent
 *     key value store with a unique filesystem path (ie. implying that two stores should not exist
 *     within the same file-system address.
 * @apiNote For the underlying DB connection, if [isClosed() == true], then all function calls which
 *     are documented to throw RuntimeException, must in fact throw a RuntimeException.
 */
public interface IDatabase {

    // Actions that change the state of the database
    // -------------------------------------------------------------------

    /**
     * Opens and creates the database connection. If database is already open, returns true.
     *
     * <p>Implements the "CREATE_IF_NOT_EXISTS" functionality by default (non-configurable in this
     * spec).
     *
     * <p>DB file(s) on disk policy: all DBs shall be enclosed in a directory, regardless of the
     * underlying implementation being a single file store or a multi-file store, to make DB cleanup
     * easiers
     *
     * <p>Does NOT throw an exception on failure, simply returns false and logs reason for failure.
     * Rationale: don't need complex connection open policy in the application at the moment
     *
     * @return True if successful, false if not.
     */
    boolean open();

    /**
     * Closes private "connection" to DB, relinquishing any underlying resources. Calling {@link
     * #close()} on a closed DB has no effect.
     *
     * <p>NOTE: All interface calls on an instance for which [isClosed() == true] will throw a
     * Runtime Exception
     */
    void close();

    /**
     * Makes all changes made since the previous commit/rollback permanent and releases any database
     * locks currently held by this Connection object. This method should be used only when
     * auto-commit mode has been disabled.
     *
     * @return {@code true} if the changes were successfully committed to storage, {@code false} if
     *     the changes could not be committed to storage
     * @throws RuntimeException if the data store is closed
     * @implNote Returns {@code true} with no other effect when auto-commit is already enabled.
     */
    boolean commit();

    /** Reduce the size of the database when possible. */
    void compact();

    /** Drop database. Removes all data from source. */
    void drop();

    // Get information about the database state
    // ------------------------------------------------------------------------

    /**
     * Returns the name of the DB
     *
     * @return name of DB provided at initialization. Optional.empty() if none provided
     */
    Optional<String> getName();

    /**
     * Returns DB directory where all DB files are stored. DBFilePath configuration delegated to
     * driver instantiating a IDatabase
     *
     * @return path (as String) to top-level DB directory containing a multi-file or single file
     *     persistent DB (assuming all driver implementations create paths on filesystem such that
     *     all DB files, whether single file or a multi-file implementation)
     *     <p>Returns empty optional for non-persistent DB.
     */
    Optional<String> getPath();

    /**
     * Returns a flag that indicates if the database is open.
     *
     * @return {@code true} if open, {@code false} otherwise
     */
    boolean isOpen();

    /**
     * Returns a flag that indicates if the database is closed.
     *
     * @return {@code true} if closed, {@code false} otherwise
     */
    boolean isClosed();

    /**
     * Returns a flag that indicates if the database is currently locked for reading or writing.
     *
     * @return {@code true} if locked, {@code false} otherwise
     */
    boolean isLocked();

    /**
     * @return {@code true} if changes are automatically committed to storage, {@code false}
     *     otherwise
     */
    boolean isAutoCommitEnabled();

    /**
     * Indicates the method of persistence this database uses. Whether it's written to disk, only
     * held in memory, or stored inside a database engine's proprietary format.
     *
     * @return The method of persistence this database uses.
     */
    PersistenceMethod getPersistenceMethod();

    /**
     * Used to validate if the DB file(s) has been created on disk. Can be used any time during this
     * object's lifecycle (before or after open() call). Typical use case: used before open() to
     * check if the open will need to create new files on the filesystem
     *
     * <p>Implementation note: can't just try to open a connection to see if it's successful since a
     * second open() on the same db filesystem resource will fail and the whole point of this
     * function is to reliably determine (almost like a static function) if the DB file with the
     * configured name and path have been created on disk
     *
     * @return {@code true} if DB file(s) exists at path configured on construction, {@code false}
     *     otherwise
     */
    boolean isCreatedOnDisk();

    /**
     * Returns size of the database in bytes.
     *
     * @return A {@code long} value representing the approximate size of DB. Returns -1 if the size
     *     cannot be computed or if the database is not persistent.
     * @throws RuntimeException if the data store is closed
     */
    long approximateSize();
}
