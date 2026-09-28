package com.evolution.hashira

import org.scalatest.Assertion

class MigrationSpec extends BaseUnitSpec {
  import TestData.*

  "Migration" - {

    "Irreversible" - {

      "should have a nice toString" in {
        mkIrreversible(
          mkId(1001L, descr = "migration 1"),
          up = Vector("statement 1"),
        ).toString shouldEqual
          "Migration.Irreversible(" +
          "{authoredAt: 1001 (1970-01-01T00:00:01.001Z), description: \"migration 1\"}, " +
          "up: [\"statement 1\"]" +
          ")"
      }

      "should not allow empty up statements in constructor" in {
        val id = mkId(1001L, descr = "migration 1")

        testUpStatementsEmptyCase {
          mkIrreversible(
            id,
            up = Vector.empty,
          )
        }
      }

      "should be constructible on any valid input" in {
        forAll {
          (
            authoredAt: Long,
            descr: String,
            upHead: String,
            upTail: Vector[String],
          ) =>
            mkIrreversible(
              id = mkId(authoredAt = authoredAt, descr = descr),
              up = upHead +: upTail,
            )
        }
      }
    }

    "Reversible" - {

      "should have a nice toString" in {
        mkReversible(
          mkId(1001L, descr = "migration 1"),
          up = Vector("statement 1"),
        ).toString shouldEqual
          "Migration.Reversible(" +
          "{authoredAt: 1001 (1970-01-01T00:00:01.001Z), description: \"migration 1\"}, " +
          "up: [\"statement 1\"], " +
          "down: []" +
          ")"
      }

      "should not allow empty up statements in constructor" in {
        val id = mkId(1001L, descr = "migration 1")

        testUpStatementsEmptyCase {
          mkReversible(
            id,
            up = Vector.empty,
          )
        }
      }

      "should be constructible on any valid input" in {
        forAll {
          (
            authoredAt: Long,
            descr: String,
            upHead: String,
            upTail: Vector[String],
            down: Vector[String],
          ) =>
            mkReversible(
              id = mkId(authoredAt = authoredAt, descr = descr),
              up = upHead +: upTail,
              down = down,
            )
        }
      }
    }
  }

  private def testUpStatementsEmptyCase(body: => Migration): Assertion = {
    val error = the[IllegalArgumentException] thrownBy {
      body
    }
    error.getMessage shouldEqual "requirement failed: up statements list must not be empty"
  }
}
