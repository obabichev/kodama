package com.obabichev.kodama.entity

/**
 * Exception thrown when EntitySession operations fail.
 *
 * This exception indicates that an operation (typically flush()) failed,
 * and the session may be in an inconsistent state.
 *
 * **Recommended Actions:**
 * 1. Rollback the database transaction
 * 2. Discard the EntitySession (don't reuse it)
 * 3. Create a new EntitySession for retry (if applicable)
 *
 * Example:
 * ```kotlin
 * connection.autoCommit = false
 * try {
 *     EntitySession(connection).use { session ->
 *         session.save(entity1)
 *         session.save(entity2)
 *         session.flush()  // May throw SessionException
 *     }
 *     connection.commit()
 * } catch (e: SessionException) {
 *     connection.rollback()  // ← Rollback transaction
 *     // Don't reuse the session - create new one for retry
 *     throw e
 * }
 * ```
 *
 * @param message Description of the failure
 * @param cause The underlying exception that caused the failure
 */
class SessionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
