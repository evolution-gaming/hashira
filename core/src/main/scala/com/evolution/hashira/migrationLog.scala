package com.evolution.hashira

import com.typesafe.scalalogging.Logger

/**
 * Logger interface for [[Migrator]] and its components.
 *
 * The main purpose is to log notable events during a migration run.
 *
 * [[MigrationLog.forRun]] creates an implementation for a single migration run, backed by
 * Typesafe scala-logging slf4j wrapper ([[DefaultMigrationLog]]).
 *
 * There is also an option to extend [[DefaultMigrationLog]], adding additional migration
 * run prefixed logging methods (e.g. warn, debug, error) or overriding the message
 * prefix.
 */
trait MigrationLog {

  /**
   * Logs an event notable for a migration run, like important run stages, information
   * about applied and reverted migrations, etc.
   *
   * @param msg
   *   event message
   */
  def info(msg: => String): Unit
}

object MigrationLog {

  /**
   * Creates [[MigrationLog]] for a single migration run (implemented by
   * [[DefaultMigrationLog]]).
   *
   * Migration set name and migration run ID are used to prefix all the log messages.
   *
   * @param logger
   *   Typesafe scala-logging logger to log with
   * @param runId
   *   migration run ID string, create one using [[Migrator.newRunId]]
   * @param migrationSetName
   *   migration set name, if provided - supposed to match the value used in
   *   [[MigrationSetLoader.load]]
   */
  def forRun(
    logger: Logger,
    runId: String,
    migrationSetName: Option[String],
  ): DefaultMigrationLog = {
    new DefaultMigrationLog(
      logger = logger,
      runId = runId,
      migrationSetName = migrationSetName,
    )
  }
}

/**
 * Default [[MigrationLog]] implementation backed by Typesafe scala-logging slf4j wrapper.
 *
 * Provides an ability to prefix all the logged messages, e.g. with a migration run
 * context info - check out alternative constructors and [[MigrationLog.forRun]].
 *
 * The class can be extended adding additional migration run prefixed logging methods
 * (e.g. warn, debug, error) or overriding the message prefix.
 *
 * @param logger
 *   Typesafe scala-logging logger to log with
 * @param messagePrefix
 *   message prefix
 */
class DefaultMigrationLog(
  protected val logger: Logger,
  protected val messagePrefix: String = "",
) extends MigrationLog {

  def this(
    logger: Logger,
    runId: String,
    migrationSetName: Option[String],
  ) = {
    this(
      logger = logger,
      messagePrefix = migrationSetName.fold(
        s"[run=$runId] ",
      ) { migrationSetName =>
        s"[set=$migrationSetName,run=$runId] "
      },
    )
  }

  override def info(msg: => String): Unit = {
    logger.info(s"$messagePrefix$msg")
  }
}
