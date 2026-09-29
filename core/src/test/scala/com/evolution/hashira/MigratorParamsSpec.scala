package com.evolution.hashira

import com.typesafe.config.{ConfigException, ConfigFactory}

class MigratorParamsSpec extends BaseUnitSpec {
  "MigratorParams" - {
    "should have default values" in {
      val params = MigratorParams.Default

      params.appliedMigrationsTable shouldEqual "applied_migrations"
      params.consistencyLevel shouldEqual "QUORUM"
      params.ignoreMissingMigrations shouldEqual false
      params.createAppliedMigrationsTableIfMissing shouldEqual true
      params.migrationFileExtension shouldEqual "cql"
      params.allowEmptyMigrationSet shouldEqual false

      MigratorParams() should be theSameInstanceAs MigratorParams.Default
    }

    "should change only the given field with withers" in {
      val params = MigratorParams.Default
        .withAppliedMigrationsTable("custom_table")
        .withConsistencyLevel("ONE")
        .withIgnoreMissingMigrations(true)
        .withCreateAppliedMigrationsTableIfMissing(false)
        .withMigrationFileExtension("txt")
        .withAllowEmptyMigrationSet(true)

      params.appliedMigrationsTable shouldEqual "custom_table"
      params.consistencyLevel shouldEqual "ONE"
      params.ignoreMissingMigrations shouldEqual true
      params.createAppliedMigrationsTableIfMissing shouldEqual false
      params.migrationFileExtension shouldEqual "txt"
      params.allowEmptyMigrationSet shouldEqual true

      MigratorParams.Default.withConsistencyLevel("ONE").appliedMigrationsTable shouldEqual
        "applied_migrations"
    }

    "should have value-based equals and hashCode" in {
      val params1 = MigratorParams.Default.withConsistencyLevel("ONE")
      val params2 = MigratorParams.Default.withConsistencyLevel("ONE")

      params1 shouldEqual params2
      params1.hashCode() shouldEqual params2.hashCode()
      params1.withConsistencyLevel("QUORUM") shouldEqual MigratorParams.Default

      params1 should not equal MigratorParams.Default
      params1 should not equal params1.withIgnoreMissingMigrations(true)
      (params1: Any) should not equal "not params"
    }

    "should have toString with field names" in {
      MigratorParams.Default.toString shouldEqual
        "MigratorParams(" +
        "appliedMigrationsTable = \"applied_migrations\", " +
        "consistencyLevel = \"QUORUM\", " +
        "ignoreMissingMigrations = false, " +
        "createAppliedMigrationsTableIfMissing = true, " +
        "migrationFileExtension = \"cql\", " +
        "allowEmptyMigrationSet = false" +
        ")"
    }

    "should quote and escape strings in toString" in {
      MigratorParams.Default.withAppliedMigrationsTable("a \"b\"\n\\c\u0001").toString should include(
        "appliedMigrationsTable = \"a \\\"b\\\"\\n\\\\c\\u0001\", ",
      )
    }

    "should render the fields on separate lines with renderMultiline" in {
      MigratorParams.Default.renderMultiline shouldEqual
        """MigratorParams:
          |- appliedMigrationsTable = "applied_migrations"
          |- consistencyLevel = "QUORUM"
          |- ignoreMissingMigrations = false
          |- createAppliedMigrationsTableIfMissing = true
          |- migrationFileExtension = "cql"
          |- allowEmptyMigrationSet = false""".stripMargin
    }

    "should quote and escape strings in renderMultiline" in {
      MigratorParams.Default.withAppliedMigrationsTable("a \"b\"\nc").renderMultiline should include(
        "\n- appliedMigrationsTable = \"a \\\"b\\\"\\nc\"\n",
      )
    }

    "readFromConfig" - {
      val customParams = MigratorParams.Default
        .withAppliedMigrationsTable("custom_table")
        .withConsistencyLevel("ONE")
        .withIgnoreMissingMigrations(true)
        .withCreateAppliedMigrationsTableIfMissing(false)
        .withMigrationFileExtension("txt")
        .withAllowEmptyMigrationSet(true)

      def read(hocon: String): MigratorParams = {
        MigratorParams.readFromConfig(ConfigFactory.parseString(hocon))
      }

      "should return the default values for an empty config" in {
        MigratorParams.readFromConfig(ConfigFactory.empty()) shouldEqual MigratorParams.Default
      }

      "should read the keys named as the fields" in {
        read(
          """appliedMigrationsTable = custom_table
            |consistencyLevel = ONE
            |ignoreMissingMigrations = true
            |createAppliedMigrationsTableIfMissing = false
            |migrationFileExtension = txt
            |allowEmptyMigrationSet = true
            |""".stripMargin,
        ) shouldEqual customParams
      }

      "should read the keys in kebab-case" in {
        read(
          """applied-migrations-table = custom_table
            |consistency-level = ONE
            |ignore-missing-migrations = true
            |create-applied-migrations-table-if-missing = false
            |migration-file-extension = txt
            |allow-empty-migration-set = true
            |""".stripMargin,
        ) shouldEqual customParams
      }

      "should use the default values for the missing keys" in {
        read("consistency-level = ONE") shouldEqual MigratorParams.Default.withConsistencyLevel("ONE")
      }

      "should prefer the field name key if both key notations are set" in {
        read("consistencyLevel = ONE\nconsistency-level = TWO") shouldEqual
          MigratorParams.Default.withConsistencyLevel("ONE")
      }

      "should ignore other keys" in {
        read("consistency-level = ONE\nother-key = TWO\nother { nested = true }") shouldEqual
          MigratorParams.Default.withConsistencyLevel("ONE")
      }

      "should fail on a value of a wrong type" in {
        interceptOnly[ConfigException.WrongType] {
          read("ignore-missing-migrations = maybe")
        }
      }
    }
  }
}
