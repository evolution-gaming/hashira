package com.evolution.hashira.driver3

import com.datastax.driver.core.*
import com.evolution.hashira.*
import com.evolution.hashira.MigratorDriverSupport.*

import java.time.Instant
import java.util.Date
import scala.jdk.CollectionConverters.*
import scala.reflect.{ClassTag, classTag}

/**
 * [[MigratorDriverSupport.CqlRunner]] implementation for Cassandra driver v3 [[Session]].
 *
 * @see
 *   [[MigratorDriver3Support]] [[makeSessionCqlRunner()]]
 */
final class Driver3SessionCqlRunner(
  session: Session,
  appliedMigrationsTable: String,
  consistencyLevel: ConsistencyLevel,
) extends MigratorDriverSupport.CqlRunner {
  private val appliedMigrationsStatements = new AppliedMigrationsCql.Statements(
    appliedMigrationsTable = appliedMigrationsTable,
  )

  override def close(): Unit = ()

  override def createMigrationsTableIfNotExists(): Unit = {
    session.execute(buildStatement(appliedMigrationsStatements.CreateTableIfNotExists))
    ()
  }

  override def runMigrationStatement(cql: String): Unit = {
    session.execute(buildStatement(cql))
    ()
  }

  override def selectAppliedMigrations(): Set[MigrationId] = {
    val rs = session.execute(buildStatement(appliedMigrationsStatements.SelectAll))
    rs.all().asScala.view.map(parseAppliedMigration).toSet
  }

  override def insertAppliedMigration(id: MigrationId, appliedAt: Instant): Unit = {
    val statement = buildStatement(
      appliedMigrationsStatements.Insert,
      values = Vector(
        Date.from(id.authoredAt.toInstant),
        id.description,
        Date.from(appliedAt),
      ),
    )
    session.execute(statement)
    ()
  }

  override def deleteAppliedMigration(id: MigrationId): Unit = {
    val statement = buildStatement(
      appliedMigrationsStatements.DeleteOne,
      values = Vector(
        Date.from(id.authoredAt.toInstant),
        id.description,
      ),
    )
    session.execute(statement)
    ()
  }

  private def buildStatement(cql: String, values: Vector[AnyRef] = Vector.empty): Statement = {
    new SimpleStatement(cql, values*)
      .setConsistencyLevel(consistencyLevel)
      .setFetchSize(Int.MaxValue) // disable paging
  }

  private def parseAppliedMigration(row: Row): MigrationId = {
    import AppliedMigrationsCql.Columns

    MigrationId(
      authoredAt = MigrationAuthoredAt(
        epochMillis = getRequiredCol[java.util.Date](row, Columns.AuthoredAt).getTime,
      ),
      description = getRequiredCol[String](row, Columns.Description),
    )
  }

  private def getRequiredCol[T: ClassTag](row: Row, name: String): T = {
    Option(row.get(name, classTag[T].runtimeClass)).getOrElse(
      sys.error(s"row missing required column $name: $row"),
    ).asInstanceOf[T]
  }
}
