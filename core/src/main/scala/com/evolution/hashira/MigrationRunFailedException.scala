package com.evolution.hashira

/**
 * [[Migrator.run]] failed - wraps the actual cause to have a nicer log message in case
 * the run exception is logged as is.
 *
 * @param cause
 *   migration run failure cause
 */
final case class MigrationRunFailedException(cause: Throwable)
extends RuntimeException(s"migration run failed: ${ cause.getMessage }", cause)
