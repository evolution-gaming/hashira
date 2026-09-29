package com.evolution.hashira

import com.evolution.hashira.MigratorDriverSupport.CqlRunner

import java.time.Instant
import scala.collection.mutable

class MigratorSpec extends BaseUnitSpec {
  import MigratorSpec.*
  import TestData.*

  "Migrator" - {
    "run" - {
      val migration = mkIrreversible(
        id = mkId(authoredAt = 1L, descr = "creates t1"),
        up = Vector("CREATE TABLE t1 (id uuid PRIMARY KEY)"),
      )
      val loader = MigrationSetLoader.constant(MigrationSet(Vector(migration)))

      def makeMigrator(
        runner: InMemoryCqlRunner,
        loader: MigrationSetLoader,
        params: MigratorParams = MigratorParams.Default,
      ): Migrator[InMemoryCqlRunner] = {
        Migrator.make(runner, loader, params)
      }

      "applies the loaded migrations" in {
        val runner = new InMemoryCqlRunner
        implicit val log: TestMigrationLog = new TestMigrationLog

        makeMigrator(runner, loader).run(migrationSetName = None, keyspace = "ks")

        runner.appliedMigrations.toSet shouldEqual Set(migration.id)
        runner.statements.toVector shouldEqual migration.upStatements
        runner.closed shouldEqual true
      }

      "wraps a migration set loading failure" in {
        val runner = new InMemoryCqlRunner
        implicit val log: TestMigrationLog = new TestMigrationLog
        val failure = new RuntimeException("loading failed")
        val failingLoader = new MigrationSetLoader {
          override def load(name: Option[String])(implicit log: MigrationLog): MigrationSet = throw failure
        }

        val e = interceptOnly[MigrationRunFailedException] {
          makeMigrator(runner, failingLoader).run(migrationSetName = None, keyspace = "ks")
        }

        e.getCause should be theSameInstanceAs failure
        e.getMessage shouldEqual "migration run failed: loading failed"
        runner.statements shouldBe empty
      }

      "wraps a CQL runner failure, closing the runner" in {
        val failure = new RuntimeException("select failed")
        val runner = new InMemoryCqlRunner {
          override def selectAppliedMigrations(): Set[MigrationId] = throw failure
        }
        implicit val log: TestMigrationLog = new TestMigrationLog

        val e = interceptOnly[MigrationRunFailedException] {
          makeMigrator(runner, loader).run(migrationSetName = None, keyspace = "ks")
        }

        e.getCause should be theSameInstanceAs failure
        runner.statements shouldBe empty
        runner.closed shouldEqual true
      }

      "wraps a migration plan failure" in {
        val runner = new InMemoryCqlRunner
        val missingId = mkId(authoredAt = 2L, descr = "missing")
        runner.appliedMigrations += missingId
        implicit val log: TestMigrationLog = new TestMigrationLog

        val e = interceptOnly[MigrationRunFailedException] {
          makeMigrator(runner, loader).run(migrationSetName = None, keyspace = "ks")
        }

        e.getCause shouldEqual AppliedMigrationsMissingException(Set(missingId))
        runner.statements shouldBe empty
      }

      "empty migration set" - {
        val emptyLoader = new MigrationSetLoader {
          override def load(
            name: Option[String],
          )(implicit
            log: MigrationLog,
          ): MigrationSet = MigrationSet.empty
        }

        "fails the run before using the CQL runner" in {
          val runner = new InMemoryCqlRunner
          implicit val log: TestMigrationLog = new TestMigrationLog

          val e = interceptOnly[MigrationRunFailedException] {
            makeMigrator(runner, emptyLoader).run(migrationSetName = Some("named"), keyspace = "ks")
          }

          e.getCause.getMessage should include("migration set is empty")
          e.getCause.getMessage should include("allowEmptyMigrationSet")
          // the runner is the client itself, closed after use - not closed means not used
          runner.closed shouldEqual false
        }

        "is allowed with allowEmptyMigrationSet" in {
          val runner = new InMemoryCqlRunner
          implicit val log: TestMigrationLog = new TestMigrationLog

          makeMigrator(runner, emptyLoader, MigratorParams.Default.withAllowEmptyMigrationSet(true))
            .run(migrationSetName = None, keyspace = "ks")

          runner.statements shouldBe empty
          runner.closed shouldEqual true
        }
      }
    }
  }
}

object MigratorSpec {

  private class InMemoryCqlRunner extends CqlRunner {
    val appliedMigrations: mutable.Set[MigrationId] = mutable.Set.empty
    val statements: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty
    var closed: Boolean = false

    override def createMigrationsTableIfNotExists(): Unit = ()

    override def runMigrationStatement(cql: String): Unit = {
      statements += cql
    }

    override def selectAppliedMigrations(): Set[MigrationId] = appliedMigrations.toSet

    override def insertAppliedMigration(id: MigrationId, appliedAt: Instant): Unit = {
      appliedMigrations += id
    }

    override def deleteAppliedMigration(id: MigrationId): Unit = {
      appliedMigrations -= id
    }

    override def close(): Unit = {
      closed = true
    }
  }
}
