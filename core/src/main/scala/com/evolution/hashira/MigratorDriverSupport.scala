package com.evolution.hashira

import java.time.Instant

/**
 * Cassandra driver support for [[Migrator]] - an abstraction over particular Cassandra
 * client implementation.
 *
 * @tparam C
 *   driver client instance type
 */
trait MigratorDriverSupport[C] {

  /**
   * Creates a [[MigratorDriverSupport.CqlRunner]] instances to perform migrations on a
   * given keyspace using the give Cassandra driver client instance.
   *
   * Might be blocking and throw exceptions.
   *
   * Created CQL runner instances must always be closed after use.
   *
   * @param driverClient
   *   driver client instance to use
   * @param keyspace
   *   keyspace name to perform migration against
   * @param params
   *   see [[MigratorParams]]
   */
  def makeRunner(driverClient: C, keyspace: String, params: MigratorParams): MigratorDriverSupport.CqlRunner
}

object MigratorDriverSupport {

  /**
   * [[MigratorDriverSupport]] instance for a ready-made [[CqlRunner]], allowing to use it
   * as the [[Migrator]] driver client directly. E.g., a runner created with
   * `driver3.makeSessionCqlRunner` for an existing keyspace-scoped session, or a custom
   * [[CqlRunner]] implementation.
   *
   * The instance returns the given runner as is, so:
   *   - the `keyspace` passed to [[Migrator.run]] is ignored - the runner defines the
   *     migrated keyspace
   *   - [[MigratorParams.appliedMigrationsTable]] and [[MigratorParams.consistencyLevel]]
   *     are ignored - create the runner with the matching values
   *   - [[Migrator]] closes the runner at the end of each run - if the [[Migrator]]
   *     instance is run more than once, the runner must stay usable after `close()`
   */
  implicit def cqlRunnerDriverSupportInstance[R <: CqlRunner]: MigratorDriverSupport[R] =
    (driverClient: R, _: String, _: MigratorParams) => {
      driverClient
    }

  /**
   * Summons the implicit instance.
   */
  def apply[C: MigratorDriverSupport]: MigratorDriverSupport[C] = implicitly

  /**
   * Keyspace-scoped CQL runner for [[MigratorDriverSupport]].
   *
   * All methods are blocking and might throw exceptions.
   *
   * Always close the instances after use.
   */
  trait CqlRunner extends AutoCloseable {

    /**
     * Creates the applied migrations table if it doesn't exist.
     */
    def createMigrationsTableIfNotExists(): Unit

    /**
     * Executes an individual [[Migration]] statement.
     */
    def runMigrationStatement(cql: String): Unit

    /**
     * Selects all recorded applied migration IDs.
     */
    def selectAppliedMigrations(): Set[MigrationId]

    /**
     * Inserts the applied migration record, overwriting an existing one.
     *
     * @param id
     *   migration ID
     * @param appliedAt
     *   timestamp indicating when the migration has been applied
     */
    def insertAppliedMigration(id: MigrationId, appliedAt: Instant): Unit

    /**
     * Deletes the applied migration record, if any.
     */
    def deleteAppliedMigration(id: MigrationId): Unit
  }

  /**
   * Applied migrations table CQL helpers - to make writing [[CqlRunner]] implementations
   * easier.
   */
  object AppliedMigrationsCql {

    /**
     * Applied migrations table column names.
     */
    object Columns {
      val AuthoredAt: String = "authored_at"
      val Description: String = "description"
      val AppliedAt: String = "applied_at"
    }

    /**
     * Applied migrations table statements.
     *
     * @param appliedMigrationsTable
     *   table name
     */
    final class Statements(appliedMigrationsTable: String) {
      val CreateTableIfNotExists: String =
        s"""
          | CREATE TABLE IF NOT EXISTS $appliedMigrationsTable (
          |   ${ Columns.AuthoredAt } timestamp,
          |   ${ Columns.Description } text,
          |   ${ Columns.AppliedAt } timestamp,
          |   PRIMARY KEY (${ Columns.AuthoredAt }, ${ Columns.Description })
          | )
          |""".stripMargin

      val SelectAll: String =
        s"SELECT ${ Columns.AuthoredAt }, ${ Columns.Description } FROM $appliedMigrationsTable"

      val Insert: String =
        s"INSERT INTO $appliedMigrationsTable (" +
          s"${ Columns.AuthoredAt }, " +
          s"${ Columns.Description }, " +
          s"${ Columns.AppliedAt }" +
          s") VALUES (?, ?, ?)"

      val DeleteOne: String =
        s"DELETE FROM $appliedMigrationsTable WHERE " +
          s"${ Columns.AuthoredAt } = ? AND " +
          s"${ Columns.Description } = ?"
    }
  }
}
