# Hashira

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/hashira-logo-dark.png">
  <img src="docs/hashira-logo.png" alt="Hashira logo" width="150" align="right">
</picture>

Cassandra schema migrations library for Scala.

[![Build Status](https://github.com/evolution-gaming/hashira/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/evolution-gaming/hashira/actions/workflows/ci.yml?query=branch%3Amain)
[![Maven Central Version](https://img.shields.io/maven-central/v/com.evolution.hashira/core_3)](https://central.sonatype.com/namespace/com.evolution.hashira)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

1. [Why](#why)
2. [Getting Started](#getting-started)
3. [How it Works](#how-it-works)
4. [For Contributors](#for-contributors)

## Why

Hashira is meant to supersede [evolution-gaming/pillar](https://github.com/evolution-gaming/pillar).

Compared to Pillar:

- Supports both Cassandra driver v3 (`Cluster`) and driver v4 (`CqlSession`), via separate modules.
- Loads migrations from the classpath (e.g. from a JAR) or from a filesystem directory.
- Doesn't manage keyspaces - the migrated keyspace must already exist.
- Compatible with Pillar migration files and its `applied_migrations` table, so a keyspace migrated
  by Pillar can be switched to Hashira as is.
  Note that Hashira's parser is stricter (e.g. duplicate attributes are rejected), and only files
  with the `migrationFileExtension` (`cql` by default) are loaded, while Pillar reads every file in
  the directory.
- In the migration files for `-- authoredAt:`, additionally to the Pillar's number format,
  Hashira also supports ISO timestamp strings like `2026-10-01T10:00:00Z`
  ([Migration file format specification](docs/MIGRATION_FILE_FORMAT.md)).

## Getting Started

### Installation

Requirements: JDK 17+, Scala 2.13 or Scala 3 (built against 3.3 LTS).

Add dependencies matching your Cassandra driver version:

- Cassandra driver 4 - `org.apache.cassandra:java-driver-core`
    ```scala
    libraryDependencies ++= Seq(
      "com.evolution.hashira" %% "core" % "LATEST_VERSION",
      "com.evolution.hashira" %% "cassandra-driver4" % "LATEST_VERSION",
    )
    ```
- Cassandra driver v3 - `com.datastax.cassandra:cassandra-driver-core`
    ```scala
    libraryDependencies ++= Seq(
      "com.evolution.hashira" %% "core" % "LATEST_VERSION",
      "com.evolution.hashira" %% "cassandra-driver3" % "LATEST_VERSION",
    )
    ```

### Migration Files

Each migration is a `.cql` file with a few directive comment lines, e.g.
`src/main/resources/db/migrations/01_create_users.cql`:

```
-- description: creates users table
-- authoredAt: 2026-10-01T10:00:00Z

-- up:

-- stage: 1
CREATE TABLE users (id text PRIMARY KEY, name text)

-- stage: 2
INSERT INTO users (id, name) VALUES ('admin', 'Admin')

-- down:

DROP TABLE users
```

The `-- down:` section is optional - without it, the migration is irreversible.
Each stage, or a section without stages, must hold exactly one CQL statement: it's sent to Cassandra
as a single query, not split on `;`. See [Migration file format](docs/MIGRATION_FILE_FORMAT.md) for
the full specification.

### Running Migrations with Cassandra Driver v4

```scala
import com.datastax.oss.driver.api.core.CqlSession
import com.evolution.hashira.*
import com.evolution.hashira.driver4.* // brings MigratorDriverSupport[CqlSession]

val session: CqlSession = CqlSession.builder().build()
val params = MigratorParams.Default

val migrator = Migrator.make(
  client = session,
  migrationSetLoader = MigrationSetLoader.fromClasspath("db/migrations", params),
  params = params,
)

// applies all the pending migrations from the "db/migrations" classpath directory
migrator.run(migrationSetName = None, keyspace = "my_keyspace")
```

The session is not closed by Hashira. Statements are keyspace-scoped per request, which requires
native protocol v5 (Cassandra 4.0+).

### Running Migrations with Cassandra Driver v3

```scala
import com.datastax.driver.core.Cluster
import com.evolution.hashira.*
import com.evolution.hashira.driver3.* // brings MigratorDriverSupport[Cluster]

val cluster: Cluster = Cluster.builder().addContactPoint("127.0.0.1").build()
val params = MigratorParams.Default

val migrator = Migrator.make(
  client = cluster,
  migrationSetLoader = MigrationSetLoader.fromClasspath("db/migrations", params),
  params = params,
)

// applies all the pending migrations from the "db/migrations" classpath directory
migrator.run(migrationSetName = None, keyspace = "my_keyspace")
```

A temporary keyspace-scoped session is opened for each run. If you already have a keyspace-scoped
`Session`, pass `makeSessionCqlRunner(session, params)` as the `client` instead.

### Configuration

`MigratorParams` can be built with `withXxx` methods, or read from Typesafe Config with
`MigratorParams.readFromConfig(config)`:

| Parameter                               | Default              | Description                                                   |
|-----------------------------------------|----------------------|---------------------------------------------------------------|
| `appliedMigrationsTable`                | `applied_migrations` | applied migrations table name, in the migrated keyspace       |
| `consistencyLevel`                      | `QUORUM`             | consistency level of all the statements                       |
| `createAppliedMigrationsTableIfMissing` | `true`               | create the applied migrations table if it doesn't exist       |
| `ignoreMissingMigrations`               | `false`              | log, instead of failing on, applied migrations missing in set |
| `allowEmptyMigrationSet`                | `false`              | don't fail on an empty migration set                          |
| `migrationFileExtension`                | `cql`                | migration file extension filter                               |

## How it Works

A migration is identified by its `authoredAt` and `description` pair.
Changing either of them in an applied migration makes it a different migration, and the old one is
reported missing. `authoredAt` defines the order, so keep it unique - ties are ordered by file name.

On each `Migrator.run`, Hashira:

1. Loads the migration set and fails if it's empty or has a malformed file.
2. Creates the applied migrations table if it doesn't exist.
3. Reads the applied migrations and builds a plan for the `MigrationTarget`: `Latest` (default),
   `ToAuthoredAt(...)` (inclusive) or `Initial`.
   If irreversible migrations are needed to be reverted, or found applied migrations missing in the
   set, the run is aborted with an exception before any statement is executed.
4. Rolls back the applied migrations out of the target, newest first: runs the `down` statements,
   then deletes the record.
5. Applies the pending migrations in the target, oldest first: runs the `up` statements, then
   inserts the record. A pending migration is applied even if newer ones were applied before.

Any failure stops the run.

Migrations handled before tha failure stay applied or rolled back, while already executed statements
of the failed migration are neither reverted nor recorded, so the next run starts that migration
over from its first statement.

Migration contents are not checksummed, so changes to the statements of applied migrations are not
detected.

**WARNING**: Hashira doesn't coordinate runs.
Overlapping runs on the same keyspace might apply the same migration twice, and concurrent DDL
statements might corrupt the schema metadata on a multi-node cluster.
Protect the runs with an external mechanism, e.g. a distributed lock like
[cassandra-sync](https://github.com/evolution-gaming/cassandra-sync).

### Applied Migrations Table

```cql
CREATE TABLE IF NOT EXISTS applied_migrations
(
    authored_at timestamp,
    description text,
    applied_at  timestamp,
    PRIMARY KEY (authored_at, description)
)
```

The table is created in the migrated keyspace, its name is set by
`MigratorParams.appliedMigrationsTable`.
Each row is an applied migration: `authored_at` and `description` come from the migration file,
`applied_at` is the time of applying. Rows can be inserted or deleted by hand to mark migrations as
applied or pending.

## For Contributors

JDK 17 is required to build the project.

### Useful SBT Commands

To reformat code using scalafmt, run:

```shell
sbt fmt
```

To run the full build, run:

```shell
sbt build
```

Cassandra driver tests use [Testcontainers](https://testcontainers.com), so Docker is required to
run them.

### Compatibility

**DO NOT BREAK BINARY AND SOURCE COMPATIBILITY!**

Binary compatibility is checked on every build automatically,
but for the source compatibility you need to watch for it yourself.

### How to Make a Release

- Being on the up-to-date main branch, create a release tag:

```shell
git tag v1.2.3
```

- Push the tag:

```shell
git push origin v1.2.3
```

- This will trigger the release GitHub Action.
  If it succeeds, the release ends up on Maven Central with GitHub release notes generated
  automatically from PRs info.
- If the release GitHub Action fails, the tag will be deleted on remote.
  After deleting the tag locally, fix the main branch and do the process again:

```shell
git tag -d v1.2.3
```
