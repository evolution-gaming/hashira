package com.evolution.hashira

import java.time.Instant

class MigrationAuthoredAtSpec extends BaseUnitSpec {

  "AuthoredAt" - {
    "should not throw exceptions for every possible epochMillis value" in {
      forAll { (epochMillis: Long) =>
        val value = MigrationAuthoredAt(epochMillis)

        value.toInstant
        value.toString
      }
    }

    "ordering should match the Instant ordering" in {
      forAll { (epochMillis1: Long, epochMillis2: Long) =>
        val value1 = MigrationAuthoredAt(epochMillis1)
        val value2 = MigrationAuthoredAt(epochMillis2)

        compareNormalized[MigrationAuthoredAt](value1, value2) shouldEqual
          compareNormalized[Instant](value1.toInstant, value2.toInstant)
      }
    }

    "toString should print epochMillis value with the ISO timestamp hint" in {
      MigrationAuthoredAt(0L).toString shouldEqual "0 (1970-01-01T00:00:00Z)"

      MigrationAuthoredAt(-1L).toString shouldEqual "-1 (1969-12-31T23:59:59.999Z)"

      MigrationAuthoredAt(1788784886099L).toString shouldEqual "1788784886099 (2026-09-07T12:41:26.099Z)"
    }
  }

  private def compareNormalized[T: Ordering](value1: T, value2: T): Int = {
    math.signum(Ordering[T].compare(value1, value2))
  }
}
