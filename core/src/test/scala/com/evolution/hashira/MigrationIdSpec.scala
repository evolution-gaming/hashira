package com.evolution.hashira

class MigrationIdSpec extends BaseUnitSpec {
  "MigrationId" - {
    "should have a nice toString" in {
      MigrationId(MigrationAuthoredAt(5L), description = "my description").toString shouldEqual
        "{authoredAt: 5 (1970-01-01T00:00:00.005Z), description: \"my description\"}"
    }

    "should be constructible for any input" in {
      forAll { (authoredAtMillis: Long, description: String) =>
        MigrationId(MigrationAuthoredAt(authoredAtMillis), description = description)
      }
    }
  }
}
