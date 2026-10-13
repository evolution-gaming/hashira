package com.evolution.hashira

import com.evolution.hashira.MigratorDriverSupport.CqlRunner
import com.typesafe.scalalogging.Logger

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import scala.util.Using
import scala.util.control.NonFatal

/**
 * Entry point of Hashira: runs Cassandra migrations on a keyspace, applying the
 * migrations which haven't been applied yet and rolling back the applied ones which are
 * out of the [[MigrationTarget]].
 *
 * Applied migrations are recorded in the [[MigratorParams.appliedMigrationsTable]] table
 * located in the migrated keyspace.
 *
 * One instance can be used for any number of runs, on different keyspaces and with
 * different migration sets (distinguished by `migrationSetName` passed to [[run]]).
 *
 * '''WARNING''': [[Migrator]] doesn't coordinate runs. Overlapping runs on the same
 * keyspace might apply the same migration twice. On a multi-node cluster, at least up to
 * Cassandra 5.0, concurrent DDL statements might also corrupt the keyspace schema
 * metadata. Protect the runs with an external coordination mechanism, e.g. a distributed
 * lock like [[https://github.com/evolution-gaming/cassandra-sync cassandra-sync]].
 *
 * Use [[Migrator.make]] or the constructor to create an instance.
 *
 * Example using Cassandra driver v4 support from the `cassandra-driver4` module:
 * {{{
 * import com.datastax.oss.driver.api.core.CqlSession
 * import com.evolution.hashira.*
 * import com.evolution.hashira.driver4.* // brings MigratorDriverSupport[CqlSession]
 *
 * val session: CqlSession = CqlSession.builder().build()
 * val params = MigratorParams.Default
 *
 * val migrator = Migrator.make(
 *   client = session,
 *   migrationSetLoader = MigrationSetLoader.fromClasspath("db/migrations", params),
 *   params = params,
 * )
 *
 * // applies all the migrations from the "db/migrations" classpath directory
 * migrator.run(migrationSetName = None, keyspace = "my_keyspace")
 * }}}
 *
 * @tparam C
 *   driver client type, requires a [[MigratorDriverSupport]] instance, e.g. one of the
 *   instances from the `cassandra-driver3` or `cassandra-driver4` modules
 * @param client
 *   driver client instance used to run the migrations
 * @param migrationSetLoader
 *   loads the migration set on each run, see [[MigrationSetLoader]]
 * @param params
 *   see [[MigratorParams]]
 *
 * @see
 *   [[Migrator.run]]
 */
final class Migrator[C: MigratorDriverSupport](
  client: C,
  migrationSetLoader: MigrationSetLoader,
  params: MigratorParams,
) {

  /**
   * Performs a migration run on a keyspace, bringing it to the `target` state.
   *
   * Run steps:
   *   - loads the migration set using [[MigrationSetLoader.load]]
   *   - fails if the set is empty, unless [[MigratorParams.allowEmptyMigrationSet]] is
   *     enabled
   *   - creates the applied migrations table if it doesn't exist and
   *     [[MigratorParams.createAppliedMigrationsTableIfMissing]] is enabled
   *   - builds a [[MigrationPlan]] using the applied migrations recorded in the table
   *   - reverts the out of target applied migrations: runs their down statements and
   *     deletes their applied migration records
   *   - applies the in-target migrations which haven't been applied yet: runs their up
   *     statements and inserts their applied migration records
   *
   * The method is blocking and throws exceptions on failure.
   *
   * A failure at any of the steps stops the run. Migrations handled before that stay
   * applied or rolled back. Statements of the failed migration which have already been
   * executed are not reverted, and the migration isn't recorded.
   *
   * @param migrationSetName
   *   migration set name passed to [[MigrationSetLoader.load]], `None` for an unnamed set
   * @param keyspace
   *   name of the keyspace to migrate; it must exist, [[Migrator]] doesn't create
   *   keyspaces
   * @param target
   *   [[MigrationTarget]] to reach, [[MigrationTarget.Latest]] by default
   * @param log
   *   [[MigrationLog]] to log the run events with; if no implicit instance is in scope, a
   *   new one is created with [[Migrator.newRunLog]]
   * @throws MigrationRunFailedException
   *   if the run fails for any non-fatal reason, wrapping the cause. E.g., a
   *   [[MigrationPlanException]] if the plan can't be built - in this case no migration
   *   statement is executed.
   */
  def run(
    migrationSetName: Option[String],
    keyspace: String,
    target: MigrationTarget = MigrationTarget.Latest,
  )(implicit
    log: MigrationLog = Migrator.newRunLog(migrationSetName = migrationSetName),
  ): Unit = {
    log.info(s"starting migration run on keyspace $keyspace with target $target")
    try {
      val migrationSet = migrationSetLoader.load(name = migrationSetName)
      validateMigrationSet(migrationSet, params)
      log.info(s"loaded migrations: ${ migrationSet.size }")
      Using.resource(
        MigratorDriverSupport[C].makeRunner(
          driverClient = client,
          keyspace = keyspace,
          params = params,
        ),
      ) { cqlRunner =>
        migrate(
          migrationSet = migrationSet,
          cqlRunner = cqlRunner,
          target = target,
        )
      }
    } catch {
      case NonFatal(t) =>
        throw MigrationRunFailedException(t)
    }
  }

  private def validateMigrationSet(set: MigrationSet, params: MigratorParams): Unit = {
    if (set.isEmpty && !params.allowEmptyMigrationSet) {
      sys.error(
        "loaded migration set is empty - add the first migration, " +
          "fix the migration set loader configuration, or set allowEmptyMigrationSet to allow it",
      )
    }
  }

  private def migrate(
    migrationSet: MigrationSet,
    cqlRunner: CqlRunner,
    target: MigrationTarget,
  )(implicit
    log: MigrationLog,
  ): Unit = {
    if (params.createAppliedMigrationsTableIfMissing) {
      log.info(s"creating migrations table if not exists: ${ params.appliedMigrationsTable }")
      cqlRunner.createMigrationsTableIfNotExists()
    }
    val appliedMigrationIds = cqlRunner.selectAppliedMigrations()
    val plan = MigrationPlan.build(
      migrationSet = migrationSet,
      appliedMigrations = appliedMigrationIds,
      target = target,
      ignoreMissingMigrations = params.ignoreMissingMigrations,
    )

    plan.ignoredMissingMigrations.foreach { missingMigrationId =>
      log.info(s"found applied migration which does not exist in the target migration set: $missingMigrationId")
    }

    executePlan(cqlRunner, plan)
  }

  private def executePlan(cqlRunner: CqlRunner, plan: MigrationPlan)(implicit log: MigrationLog): Unit = {
    if (plan.migrationsToRollback.isEmpty && plan.migrationsToApply.isEmpty) {
      log.info("migration run success: everything up to date")
    } else {
      plan.migrationsToRollback.foreach { m =>
        log.info(s"reverting migration: ${ m.id }")
        m.downStatements.foreach(cqlRunner.runMigrationStatement)
        cqlRunner.deleteAppliedMigration(m.id)
      }

      plan.migrationsToApply.foreach { m =>
        log.info(s"applying migration: ${ m.id }")
        m.upStatements.foreach(cqlRunner.runMigrationStatement)
        cqlRunner.insertAppliedMigration(m.id, appliedAt = Instant.now())
      }

      val appliedCount = plan.migrationsToApply.size
      val rolledBackCount = plan.migrationsToRollback.size

      log.info(s"migration run success: $appliedCount applied, $rolledBackCount reverted")
    }
  }
}

object Migrator {

  private val logger: Logger = Logger[Migrator[?]]

  private[this] val nextRunId = new AtomicLong(1)

  /**
   * Creates a [[MigrationLog]] for a new migration run, the default log values for
   * [[Migrator.run]] invocation.
   *
   * Messages are logged with the [[Migrator]] class logger and prefixed with the
   * migration set name and a new run ID from [[newRunId]], see [[MigrationLog.forRun]].
   *
   * @param migrationSetName
   *   migration set name, supposed to match the value passed to [[Migrator.run]]
   */
  def newRunLog(migrationSetName: Option[String]): MigrationLog = MigrationLog.forRun(
    Migrator.logger,
    runId = Migrator.newRunId(),
    migrationSetName = migrationSetName,
  )

  /**
   * Generates a new migration run ID to tell the runs apart in the logs.
   *
   * The generated values are short and human-friendly but not unique across app restarts.
   */
  def newRunId(): String = nextRunId.getAndIncrement().toString

  /**
   * Creates a [[Migrator]] instance, see the class doc for the details and a usage
   * example.
   *
   * @tparam C
   *   Cassandra driver client type, requires a [[MigratorDriverSupport]] instance
   * @param client
   *   driver client instance used to run the migrations
   * @param migrationSetLoader
   *   loads the migration set for migration run, see [[MigrationSetLoader]]
   * @param params
   *   see [[MigratorParams]]
   */
  def make[C: MigratorDriverSupport](
    client: C,
    migrationSetLoader: MigrationSetLoader,
    params: MigratorParams,
  ): Migrator[C] = new Migrator[C](client, migrationSetLoader, params)
}
