package com.evolution.hashira

import scala.collection.immutable.ArraySeq

class MigrationFileInvalidExceptionSpec extends BaseUnitSpec {

  private val example = MigrationFileInvalidException(
    fileRef = "/migrations/01_create_tables.cql",
    errors = ArraySeq(
      MigrationFileFormatError(lineNum = None, "file error 1"),
      MigrationFileFormatError(lineNum = None, "file error 2"),
      MigrationFileFormatError(lineNum = Some(4), "line error 1"),
      MigrationFileFormatError(lineNum = Some(10), "line error 2"),
    ),
  )

  "MigrationFileInvalidException" - {
    "must not allow empty error list in constructor" in {
      interceptOnly[IllegalArgumentException] {
        example.copy(errors = ArraySeq.empty)
      }.getMessage shouldEqual "requirement failed: error list must not be empty"
    }

    "should have nice single-line exception message" in {
      example.getMessage shouldEqual
        "in /migrations/01_create_tables.cql - " +
        "file error 1; " +
        "file error 2; " +
        "[line 4] line error 1; " +
        "[line 10] line error 2"
    }

    "should have nice multi-line string format" in {
      example.fmtMultiline shouldEqual """in /migrations/01_create_tables.cql:
                                         |	- file error 1
                                         |	- file error 2
                                         |	- [line 4] line error 1
                                         |	- [line 10] line error 2""".stripMargin
    }
  }
}
