package com.evolution.hashira.driver3

import com.datastax.driver.core.*
import com.evolution.hashira.*
import com.evolution.hashira.MigratorDriverSupport.*

import java.time.Instant
import scala.util.Try
import scala.util.control.NonFatal

/**
 * [[MigratorDriverSupport.CqlRunner]] implementation for Cassandra driver v3 [[Cluster]].
 *
 * @see
 *   [[MigratorDriver3Support]]
 */
final class Driver3ClusterCqlRunner(
  cluster: Cluster,
  keyspace: String,
  appliedMigrationsTable: String,
  consistencyLevel: ConsistencyLevel,
) extends CqlRunner {

  private val (session, sessionRunner) = {
    val session = cluster.connect(keyspace)
    try {
      val runner = new Driver3SessionCqlRunner(
        session = session,
        appliedMigrationsTable = appliedMigrationsTable,
        consistencyLevel = consistencyLevel,
      )
      session -> runner
    } catch {
      case NonFatal(t) =>
        Try(session.close()) // eat the errors here
        throw t
    }
  }

  override def close(): Unit = {
    session.close()
  }

  override def createMigrationsTableIfNotExists(): Unit = {
    sessionRunner.createMigrationsTableIfNotExists()
  }

  override def runMigrationStatement(cql: String): Unit = {
    sessionRunner.runMigrationStatement(cql)
  }

  override def selectAppliedMigrations(): Set[MigrationId] = {
    sessionRunner.selectAppliedMigrations()
  }

  override def insertAppliedMigration(id: MigrationId, appliedAt: Instant): Unit = {
    sessionRunner.insertAppliedMigration(id, appliedAt = appliedAt)
  }

  override def deleteAppliedMigration(id: MigrationId): Unit = {
    sessionRunner.deleteAppliedMigration(id)
  }
}
