package com.evolution.hashira.driver_tck

import com.evolution.hashira.*
import com.evolution.hashira.MigratorDriverSupport.{AppliedMigrationsCql, CqlRunner}
import com.evolution.hashira.MigratorParams.Default as DefaultParams
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.cassandra.CassandraContainer

import java.net.InetSocketAddress
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Using

/**
 * Integration test suite for [[MigratorDriverSupport]] implementations, running against a
 * Cassandra testcontainer, so it requires Docker.
 *
 * Extend it providing the [[MigratorDriverSupport]] instance and the driver-specific
 * hooks: [[makeClient]], [[closeClient]] and [[makeTestClient]].
 *
 * The tests share one Cassandra container and one driver client; each test uses its own
 * new keyspace.
 */
abstract class BaseMigratorDriverSupportTckSpec[C: MigratorDriverSupport]
extends AnyFreeSpec with Matchers with BeforeAndAfterAll {
  import BaseMigratorDriverSupportTckSpec.*

  // -- Concrete test suite extension points

  /**
   * Creates a driver client connected to the test container.
   *
   * @param contactPoint
   *   CQL native transport address of the Cassandra test container
   * @param localDatacenter
   *   Cassandra test container node local DC name
   */
  protected def makeClient(contactPoint: InetSocketAddress, localDatacenter: String): C

  /**
   * Closes a driver client created by [[makeClient]].
   */
  protected def closeClient(client: C): Unit

  /**
   * Creates a [[TestClient]] for the assertions. It's closed before `client`.
   */
  protected def makeTestClient(client: C): TestClient

  /**
   * Cassandra Docker image to run the tests against
   */
  protected def cassandraDockerImage: String = DefaultCassandraDockerImage

  // --

  private implicit val clientReleasable: Using.Releasable[C] =
    (resource: C) => closeClient(resource)

  private val nextKeyspaceNum = new AtomicInteger(0)

  private val resourceEnv: ResourceEnv[Resources[C]] = ResourceEnv()

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    this.resourceEnv.initialize { arena =>
      // registered before start, so a container failing to start is still stopped
      val cassandra = arena(new CassandraContainer(cassandraDockerImage))
      cassandra.start()

      val client = arena(makeClient(
        contactPoint = cassandra.getContactPoint,
        localDatacenter = cassandra.getLocalDatacenter,
      ))
      val testClient = arena(makeTestClient(client))

      Resources(
        client = client,
        testClient = testClient,
      )
    }
  }

  override protected def afterAll(): Unit = {
    resourceEnv.close()
    super.afterAll()
  }

  "CqlRunner" - {
    "createMigrationsTableIfNotExists" - {
      "creates the applied migrations table in the target keyspace" in {
        val keyspace = createNewKeyspace()
        val otherKeyspace = createNewKeyspace()

        withRunner(keyspace) { runner =>
          runner.createMigrationsTableIfNotExists()
        }

        selectTableNames(keyspace) shouldBe Set(DefaultParams.appliedMigrationsTable)
        selectTableNames(otherKeyspace) shouldBe empty

        selectColumns(keyspace, DefaultParams.appliedMigrationsTable) shouldBe Set(
          TestRow(AppliedMigrationsCql.Columns.AuthoredAt, "partition_key", "timestamp"),
          TestRow(AppliedMigrationsCql.Columns.Description, "clustering", "text"),
          TestRow(AppliedMigrationsCql.Columns.AppliedAt, "regular", "timestamp"),
        )
      }

      "uses the configured table name" in {
        val keyspace = createNewKeyspace()

        withRunner(keyspace, CustomTableParams) { runner =>
          runner.createMigrationsTableIfNotExists()
        }

        selectTableNames(keyspace) shouldBe Set(CustomTableParams.appliedMigrationsTable)
      }

      "is idempotent and keeps the existing records" in {
        val keyspace = createNewKeyspace()

        withRunner(keyspace) { runner =>
          runner.createMigrationsTableIfNotExists()
          runner.insertAppliedMigration(Id1, appliedAt = AppliedAt)
          runner.createMigrationsTableIfNotExists()

          runner.selectAppliedMigrations() shouldBe Set(Id1)
        }
      }
    }

    "selectAppliedMigrations" - {
      "returns nothing for an empty applied migrations table" in {
        val keyspace = createNewKeyspaceWithMigrationsTable()

        withRunner(keyspace) { runner =>
          runner.selectAppliedMigrations() shouldBe empty
        }
      }

      "returns all the migration IDs recorded by insertAppliedMigration exactly" in {
        val keyspace = createNewKeyspaceWithMigrationsTable()
        val ids = Set(
          mkId(MigrationFileParser.MinAuthoredAt.epochMillis, "min authoredAt"),
          mkId(MigrationFileParser.MaxAuthoredAt.epochMillis, "max authoredAt"),
          mkId(1370026062999L, "millisecond precision authoredAt"),
          mkId(1370026062999L, "same authoredAt, another description"),
          mkId(1370026063000L, "  surrounding whitespace is kept  "),
          mkId(
            1370026063001L,
            "multi-line\ndescription with 'quotes', \"double quotes\" and unicode: ü, ✓, 日本",
          ),
        )

        withRunner(keyspace) { runner =>
          ids.foreach(runner.insertAppliedMigration(_, appliedAt = AppliedAt))
        }

        // a new runner instance - the records are read back from Cassandra
        withRunner(keyspace) { runner =>
          runner.selectAppliedMigrations() shouldBe ids
        }
      }
    }

    "insertAppliedMigration" - {
      "records appliedAt with millisecond precision" in {
        val keyspace = createNewKeyspaceWithMigrationsTable()
        // Migrator uses Instant.now() which might have sub-millisecond precision
        val appliedAt = Instant.parse("2026-10-06T10:15:30.123456789Z")

        withRunner(keyspace) { runner =>
          runner.insertAppliedMigration(Id1, appliedAt = appliedAt)
        }

        selectAppliedAtMillis(keyspace) shouldBe Vector(appliedAt.truncatedTo(ChronoUnit.MILLIS).toEpochMilli)
      }

      "overwrites the record of an already recorded migration" in {
        val keyspace = createNewKeyspaceWithMigrationsTable()
        val laterAppliedAt = AppliedAt.plusSeconds(60)

        withRunner(keyspace) { runner =>
          runner.insertAppliedMigration(Id1, appliedAt = AppliedAt)
          runner.insertAppliedMigration(Id1, appliedAt = laterAppliedAt)

          runner.selectAppliedMigrations() shouldBe Set(Id1)
        }

        selectAppliedAtMillis(keyspace) shouldBe Vector(laterAppliedAt.toEpochMilli)
      }
    }

    "deleteAppliedMigration" - {
      "deletes only the record of the given migration" in {
        val keyspace = createNewKeyspaceWithMigrationsTable()

        withRunner(keyspace) { runner =>
          Seq(Id1, Id2, Id2SameAuthoredAt).foreach(runner.insertAppliedMigration(_, appliedAt = AppliedAt))

          runner.deleteAppliedMigration(Id2)

          runner.selectAppliedMigrations() shouldBe Set(Id1, Id2SameAuthoredAt)
        }
      }

      "does nothing for a migration which is not recorded" in {
        val keyspace = createNewKeyspaceWithMigrationsTable()

        withRunner(keyspace) { runner =>
          runner.insertAppliedMigration(Id1, appliedAt = AppliedAt)

          runner.deleteAppliedMigration(Id2)

          runner.selectAppliedMigrations() shouldBe Set(Id1)
        }
      }
    }

    "runMigrationStatement" - {
      "runs a statement in the target keyspace" in {
        val keyspace = createNewKeyspace()
        val otherKeyspace = createNewKeyspace()

        withRunner(keyspace) { runner =>
          runner.runMigrationStatement(
            """CREATE TABLE users (
              |  -- comment lines are passed to Cassandra as a part of the statement
              |  id text PRIMARY KEY,
              |  name text
              |)""".stripMargin,
          )
          runner.runMigrationStatement("INSERT INTO users (id, name) VALUES ('u1', 'User One')")
        }

        selectTableNames(keyspace) shouldBe Set("users")
        selectTableNames(otherKeyspace) shouldBe empty
        selectRows(s"SELECT id, name FROM $keyspace.users") shouldBe Vector(TestRow("u1", "User One"))
      }

      "propagates the errors" in {
        val keyspace = createNewKeyspace()

        withRunner(keyspace) { runner =>
          runner.runMigrationStatement("CREATE TABLE users (id text PRIMARY KEY)")

          an[Exception] should be thrownBy {
            runner.runMigrationStatement("CREATE TABLE users (id text PRIMARY KEY)")
          }
          an[Exception] should be thrownBy {
            runner.runMigrationStatement("NOT A CQL STATEMENT")
          }
        }
      }
    }

    "keeps applied migration records separate per keyspace and per table" in {
      val keyspace = createNewKeyspaceWithMigrationsTable()
      val otherKeyspace = createNewKeyspaceWithMigrationsTable()
      withRunner(keyspace, CustomTableParams) { runner =>
        runner.createMigrationsTableIfNotExists()
      }

      withRunner(keyspace) { runner =>
        runner.insertAppliedMigration(Id1, appliedAt = AppliedAt)
      }
      withRunner(otherKeyspace) { runner =>
        runner.insertAppliedMigration(Id2, appliedAt = AppliedAt)
      }
      withRunner(keyspace, CustomTableParams) { runner =>
        runner.insertAppliedMigration(Id2SameAuthoredAt, appliedAt = AppliedAt)
      }

      withRunner(keyspace) { runner =>
        runner.selectAppliedMigrations() shouldBe Set(Id1)
      }
      withRunner(otherKeyspace) { runner =>
        runner.selectAppliedMigrations() shouldBe Set(Id2)
      }
      withRunner(keyspace, CustomTableParams) { runner =>
        runner.selectAppliedMigrations() shouldBe Set(Id2SameAuthoredAt)
      }
    }

    "uses the configured consistency level" in {
      val keyspace = createNewKeyspaceWithMigrationsTable()
      // the TCK keyspaces have replication factor 1, so consistency level THREE is unachievable
      val unachievableParams = DefaultParams.withConsistencyLevel("THREE")
      val achievableParams = DefaultParams.withConsistencyLevel("ONE")

      withRunner(keyspace, unachievableParams) { runner =>
        val insertError = the[Exception] thrownBy {
          runner.insertAppliedMigration(Id1, appliedAt = AppliedAt)
        }
        allMessages(insertError) should include("THREE")

        val selectError = the[Exception] thrownBy {
          runner.selectAppliedMigrations()
        }
        allMessages(selectError) should include("THREE")
      }

      withRunner(keyspace, achievableParams) { runner =>
        runner.insertAppliedMigration(Id1, appliedAt = AppliedAt)
        runner.selectAppliedMigrations() shouldBe Set(Id1)
      }
    }
  }

  "Migrator" - {
    "applies and rolls back migrations loaded from a classpath directory" in {
      val keyspace = createNewKeyspace()

      val migrator = Migrator.make(requireClient(), TckMigrationSetLoader, DefaultParams)

      def migrate(target: MigrationTarget): Unit = {
        migrator.run(migrationSetName = None, keyspace = keyspace, target = target)
      }

      def selectAppliedMigrations(): Set[MigrationId] = {
        withRunner(keyspace)(_.selectAppliedMigrations())
      }

      def selectUsersColumnNames(): Set[String] = {
        selectColumns(keyspace, "users").map(_.columnValues.head)
      }

      def selectUsers(): Vector[TestRow] = {
        selectRows(s"SELECT id, name, email FROM $keyspace.users")
      }

      migrate(MigrationTarget.Latest)

      selectAppliedMigrations() shouldBe Set(CreateUsersId, AddUserEmailsId)
      selectUsers() shouldBe Vector(TestRow("u1", "User One", "u1@example.com"))

      // nothing to do - would fail on re-applying the migrations otherwise
      migrate(MigrationTarget.Latest)

      selectAppliedMigrations() shouldBe Set(CreateUsersId, AddUserEmailsId)

      migrate(MigrationTarget.ToAuthoredAt(CreateUsersId.authoredAt))

      selectAppliedMigrations() shouldBe Set(CreateUsersId)
      selectUsersColumnNames() shouldBe Set("id", "name")
      selectRows(s"SELECT id FROM $keyspace.users") shouldBe empty

      migrate(MigrationTarget.Initial)

      selectAppliedMigrations() shouldBe empty
      selectTableNames(keyspace) shouldBe Set(DefaultParams.appliedMigrationsTable)

      migrate(MigrationTarget.Latest)

      selectAppliedMigrations() shouldBe Set(CreateUsersId, AddUserEmailsId)
      selectUsers() shouldBe Vector(TestRow("u1", "User One", "u1@example.com"))
    }

    "fails without changes if the applied migrations table is missing and is not to be created" in {
      val keyspace = createNewKeyspace()

      val migrator = Migrator.make(
        requireClient(),
        TckMigrationSetLoader,
        DefaultParams.withCreateAppliedMigrationsTableIfMissing(false),
      )

      // selectAppliedMigrations must fail on the missing table, otherwise Migrator would run
      // the migration statements before failing to record the applied migrations
      val error = the[Exception] thrownBy {
        migrator.run(migrationSetName = None, keyspace = keyspace)
      }
      // makes sure the run fails on the missing table, not before reaching Cassandra
      allMessages(error) should include(DefaultParams.appliedMigrationsTable)

      selectTableNames(keyspace) shouldBe empty
    }
  }

  private def runCql(cql: String): Vector[TestRow] = {
    resourceEnv.requireInitialized().testClient.runCql(cql)
  }

  private def requireClient(): C = {
    resourceEnv.requireInitialized().client
  }

  private def withRunner[A](
    keyspace: String,
    params: MigratorParams = MigratorParams.Default,
  )(
    f: CqlRunner => A,
  ): A = {
    Using.resource(
      MigratorDriverSupport[C].makeRunner(
        driverClient = requireClient(),
        keyspace = keyspace,
        params = params,
      ),
    )(f)
  }

  // alias for better readability
  private def selectRows(cql: String): Vector[TestRow] = runCql(cql)

  private def createNewKeyspace(): String = {
    val keyspace = s"hashira_tck_${ nextKeyspaceNum.getAndIncrement() }"
    runCql(
      s"CREATE KEYSPACE $keyspace WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}",
    )
    keyspace
  }

  private def createNewKeyspaceWithMigrationsTable(): String = {
    val keyspace = createNewKeyspace()
    val statements = new AppliedMigrationsCql.Statements(
      appliedMigrationsTable = s"$keyspace.${ DefaultParams.appliedMigrationsTable }",
    )
    runCql(statements.CreateTableIfNotExists)
    keyspace
  }

  private def selectTableNames(keyspace: String): Set[String] = {
    selectRows(s"SELECT table_name FROM system_schema.tables WHERE keyspace_name = '$keyspace'")
      .map(_.columnValues.head)
      .toSet
  }

  /**
   * Selects table column descriptions: name, kind, type.
   */
  private def selectColumns(keyspace: String, table: String): Set[TestRow] = {
    selectRows(
      "SELECT column_name, kind, type FROM system_schema.columns " +
        s"WHERE keyspace_name = '$keyspace' AND table_name = '$table'",
    ).toSet
  }

  private def selectAppliedAtMillis(keyspace: String): Vector[Long] = {
    import AppliedMigrationsCql.Columns
    selectRows(
      s"SELECT CAST(toUnixTimestamp(${ Columns.AppliedAt }) AS text) " +
        s"FROM $keyspace.${ DefaultParams.appliedMigrationsTable }",
    ).map(_.columnValues.head.toLong)
  }
}

object BaseMigratorDriverSupportTckSpec {

  /**
   * Default Cassandra Docker image to run the TCK against.
   */
  val DefaultCassandraDockerImage: String = "cassandra:4.1.12"

  /**
   * Classpath directory with the TCK migration set files.
   */
  private val MigrationsClassPathDir: String = "hashira/driver_tck/migrations"

  private val TckMigrationSetLoader: MigrationSetLoader =
    MigrationSetLoader.fromClasspath(classPathDir = MigrationsClassPathDir, params = DefaultParams)

  private val CreateUsersId: MigrationId = mkId(1790848800000L, "create users table") // 2026-10-01T10:00:00Z
  private val AddUserEmailsId: MigrationId = mkId(1790935200000L, "add user emails") // 2026-10-02T10:00:00Z

  private val CustomTableParams: MigratorParams =
    DefaultParams.withAppliedMigrationsTable("custom_applied_migrations")

  private val AppliedAt: Instant = Instant.parse("2026-10-06T10:15:30.123Z")

  private val Id1: MigrationId = mkId(1370026062000L, "migration 1")
  private val Id2: MigrationId = mkId(1370026063000L, "migration 2")
  private val Id2SameAuthoredAt: MigrationId = mkId(1370026063000L, "migration 2 with the same authoredAt")

  private def mkId(authoredAt: Long, description: String): MigrationId = {
    MigrationId(authoredAt = MigrationAuthoredAt(authoredAt), description = description)
  }

  /**
   * Messages of the exception, its causes and suppressed exceptions - drivers tend to
   * wrap Cassandra errors.
   */
  private def allMessages(e: Throwable): String = {
    val nested = Option(e.getCause).toVector ++ e.getSuppressed.toVector
    (Option(e.getMessage).toVector ++ nested.map(allMessages)).mkString("\n")
  }

  private final case class Resources[C](
    client: C,
    testClient: TestClient,
  )
}
