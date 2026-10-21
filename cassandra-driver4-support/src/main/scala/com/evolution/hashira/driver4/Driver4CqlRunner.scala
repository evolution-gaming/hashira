package com.evolution.hashira.driver4

import com.datastax.oss.driver.api.core.*
import com.datastax.oss.driver.api.core.cql.*
import com.evolution.hashira.*
import com.evolution.hashira.MigratorDriverSupport.*

import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.reflect.{ClassTag, classTag}

/**
 * [[MigratorDriverSupport.CqlRunner]] implementation for Cassandra driver v4.
 */
final class Driver4CqlRunner(
  session: CqlSession,
  keyspace: String,
  appliedMigrationsTable: String,
  consistencyLevel: ConsistencyLevel,
) extends MigratorDriverSupport.CqlRunner {

  private val appliedMigrationsStatements = new AppliedMigrationsCql.Statements(
    appliedMigrationsTable = appliedMigrationsTable,
  )

  override def close(): Unit = ()

  override def createMigrationsTableIfNotExists(): Unit = {
    runPlainStatement(appliedMigrationsStatements.CreateTableIfNotExists)
    ()
  }

  override def runMigrationStatement(cql: String): Unit = {
    runPlainStatement(cql)
    ()
  }

  override def selectAppliedMigrations(): Set[MigrationId] = {
    val rs = runPlainStatement(appliedMigrationsStatements.SelectAll)
    rs.all().asScala.view.map(parseAppliedMigration).toSet
  }

  override def insertAppliedMigration(id: MigrationId, appliedAt: Instant): Unit = {
    val statement = prebuildStatement(appliedMigrationsStatements.Insert)
      .addPositionalValue(id.authoredAt.toInstant)
      .addPositionalValue(id.description)
      .addPositionalValue(appliedAt)
      .build()

    session.execute(statement)
    ()
  }

  override def deleteAppliedMigration(id: MigrationId): Unit = {
    val statement = prebuildStatement(appliedMigrationsStatements.DeleteOne)
      .addPositionalValue(id.authoredAt.toInstant)
      .addPositionalValue(id.description)
      .build()

    session.execute(statement)
    ()
  }

  private def prebuildStatement(cql: String): SimpleStatementBuilder = {
    SimpleStatement.builder(cql)
      .setKeyspace(keyspace)
      .setConsistencyLevel(consistencyLevel)
      .setPageSize(Int.MaxValue) // disable paging
  }

  private def runPlainStatement(cql: String): ResultSet = {
    val statement = prebuildStatement(cql).build()
    session.execute(statement)
  }

  private def parseAppliedMigration(row: Row): MigrationId = {
    import AppliedMigrationsCql.Columns

    MigrationId(
      authoredAt = MigrationAuthoredAt(
        epochMillis = getRequiredCol[Instant](row, Columns.AuthoredAt).toEpochMilli,
      ),
      description = getRequiredCol[String](row, Columns.Description),
    )
  }

  private def getRequiredCol[T <: AnyRef: ClassTag](row: Row, name: String): T = {
    Option(row.get(name, classTag[T].runtimeClass)).getOrElse(
      sys.error(s"row missing required column $name: $row"),
    ).asInstanceOf[T]
  }
}
