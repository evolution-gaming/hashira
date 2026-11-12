package com.evolution.hashira.driver4

import com.datastax.oss.driver.api.core.*
import com.datastax.oss.driver.api.core.cql.*
import com.datastax.oss.driver.api.core.metadata.Node
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
  runStatementsOnFirstNode: Boolean,
  schemaAgreementMode: SchemaAgreementMode,
)(implicit
  log: MigrationLog,
) extends MigratorDriverSupport.CqlRunner {

  private val appliedMigrationsStatements = new AppliedMigrationsCql.Statements(
    appliedMigrationsTable = appliedMigrationsTable,
  )

  private lazy val firstNode: Option[Node] =
    if (runStatementsOnFirstNode)
      session.getMetadata.getNodes.values.asScala.headOption
    else
      None

  override def close(): Unit = ()

  override def createMigrationsTableIfNotExists(): Unit = {
    runDdlStatement(appliedMigrationsStatements.CreateTableIfNotExists)
    ()
  }

  override def runMigrationStatement(cql: String): Unit = {
    runDdlStatement(cql)
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
    val builder = SimpleStatement.builder(cql)
      .setKeyspace(keyspace)
      .setConsistencyLevel(consistencyLevel)
      .setPageSize(Int.MaxValue) // disable paging
    if (runStatementsOnFirstNode)
      builder.setNode(
        firstNode.getOrElse(sys.error("runStatementsOnFirstNode is enabled but the session metadata contains no nodes")),
      )
    else builder
  }

  private def runPlainStatement(cql: String): ResultSet = {
    val statement = prebuildStatement(cql).build()
    session.execute(statement)
  }

  private def runDdlStatement(cql: String): ResultSet = {
    val rs = runPlainStatement(cql)
    doCheckSchemaAgreement()
    rs
  }

  private def doCheckSchemaAgreement(): Unit = {
    schemaAgreementMode match {
      case SchemaAgreementMode.Disabled => ()
      case mode =>
        val agreed = session.checkSchemaAgreement()
        if (!agreed) {
          mode match {
            case SchemaAgreementMode.Warn =>
              log.warn(schemaAgreementError)
            case SchemaAgreementMode.Fail =>
              sys.error(schemaAgreementError)
            case SchemaAgreementMode.Disabled => ()
          }
        }
    }
  }

  private val schemaAgreementError =
    "Schema agreement check failed or timed out after DDL statement: not all nodes agree on the schema"

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
