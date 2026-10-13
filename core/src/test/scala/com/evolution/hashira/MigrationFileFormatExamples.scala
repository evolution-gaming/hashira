package com.evolution.hashira

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.*

/**
 * Migration file examples for testing [[MigrationFileParser]].
 *
 * The example files are located in classpath.
 *
 * To add a valid example, check out [[ValidFileExamples]].
 *
 * Invalid examples are automatically discovered on the classpath - see
 * [[InvalidFileExample]] and [[InvalidFileExamples]].
 */
object MigrationFileFormatExamples {
  import TestData.*

  private val baseClassPath = "/hashira/migration_file_format_examples"

  private def getRequiredTestResourcePath(subPath: String): Path = {
    val effectivePath = s"$baseClassPath/$subPath"
    val url = Option(getClass.getResource(effectivePath)).getOrElse(
      sys.error(s"not found: $effectivePath"),
    )
    Paths.get(url.toURI)
  }

  sealed trait FileExample {
    def name: String

    protected def subDir: String

    protected final def readRawFilePath(): Path = getRequiredTestResourcePath(s"$subDir/$name")
  }

  final case class ValidFileExample(
    name: String,
    expected: Migration,
  ) extends FileExample {
    override protected def subDir: String = "valid"

    def readFilePath(): Path = readRawFilePath()
  }

  /**
   * The expected errors are marked in the file itself with `-- TEST` lines:
   *   - `-- TEST ↑LINE_ERROR <error description>` right under the line the error is for
   *   - `-- TEST FILE_ERROR <error description>` at the beginning of the file for an
   *     error reported without a line number
   *
   * All `-- TEST` lines are removed from the content passed to the parser.
   */
  final case class InvalidFileExample(
    name: String,
  ) extends FileExample {
    override protected def subDir: String = InvalidFileExample.subDir

    def readFile(): InvalidFileExample.Content = {
      val rawFilePath = readRawFilePath()
      val rawFileStr = Files.readString(rawFilePath, StandardCharsets.UTF_8)

      // preserve original separators, just in case
      val contentToParse = rawFileStr.linesWithSeparators
        .filter(!_.startsWith("-- TEST"))
        .mkString("")

      InvalidFileExample.Content(
        rawFilePath = rawFilePath,
        contentToParse = contentToParse,
        expectedErrors = extractErrors(rawFileStr),
      )
    }

    private def extractErrors(rawFileStr: String): ArraySeq[MigrationFileFormatError] = {
      val resultBuilder = ArraySeq.newBuilder[MigrationFileFormatError]
      var curNonTestLineNum: Int = 0

      rawFileStr.lines().iterator().asScala.foreach {
        case s"-- TEST FILE_ERROR $descr" =>
          resultBuilder += MigrationFileFormatError(lineNum = None, description = descr)

        case s"-- TEST ↑LINE_ERROR $descr" =>
          resultBuilder += MigrationFileFormatError(lineNum = Some(curNonTestLineNum), description = descr)

        case unexpectedTestDirectiveLine if unexpectedTestDirectiveLine.startsWith("-- TEST") =>
          sys.error(s"unexpected test directive line: $unexpectedTestDirectiveLine")

        case _ =>
          // non-test line
          curNonTestLineNum += 1
      }

      val result = resultBuilder.result()
      if (result.isEmpty) {
        sys.error("not expected test errors found in file")
      }
      result
    }
  }

  object InvalidFileExample {
    private val subDir: String = "invalid"

    final case class Content(
      rawFilePath: Path,
      contentToParse: String,
      expectedErrors: ArraySeq[MigrationFileFormatError],
    )

    private[MigrationFileFormatExamples] def discoverOnClasspath(): ArraySeq[InvalidFileExample] = {
      val dirPath = getRequiredTestResourcePath(subDir)

      val files = Files.list(dirPath).iterator().asScala
        .filter(_.getFileName.toString.endsWith(".cql"))
        .map { filePath =>
          InvalidFileExample(
            name = filePath.getFileName.toString,
          )
        }.to(ArraySeq)

      if (files.isEmpty) {
        sys.error(s"no invalid file examples found in $dirPath")
      }

      // to have test cases in nice order
      files.sortBy(_.name)
    }
  }

  val ValidFileExamples: ArraySeq[ValidFileExample] = ArraySeq(
    ValidFileExample(
      name = "001_irreversible_single_stage.cql",
      expected = mkIrreversible(
        id = mkId(
          authoredAt = 1370023262L,
          descr = "creates events table",
        ),
        up = Vector(
          """CREATE TABLE events (
            |  batch_id text,
            |  occurred_at uuid,
            |  event_type text,
            |  payload blob,
            |  PRIMARY KEY (batch_id, occurred_at, event_type)
            |)""".stripMargin,
        ),
      ),
    ),
    ValidFileExample(
      name = "002_reversible_single_stage.cql",
      expected = mkReversible(
        id = mkId(
          authoredAt = 1370028263L,
          descr = "creates views table",
        ),
        up = Vector(
          """CREATE TABLE views (
            |  id uuid PRIMARY KEY,
            |  url text
            |)""".stripMargin,
        ),
        down = Vector("DROP TABLE views"),
      ),
    ),
    ValidFileExample(
      name = "003_reversible_with_noop_down.cql",
      expected = mkReversible(
        id = mkId(
          authoredAt = 1370028264L,
          descr = "adds user_agent to views table",
        ),
        up = Vector(
          """ALTER TABLE views
            |ADD user_agent text""".stripMargin,
        ),
        // down block is present but empty - reverting is a no-op
        down = Vector.empty,
      ),
    ),
    ValidFileExample(
      name = "004_down_with_only_blank_lines_is_a_noop.cql",
      expected = mkReversible(
        id = mkId(
          authoredAt = 1370028274L,
          descr = "adds referrer to views table",
        ),
        up = Vector(
          """ALTER TABLE views
            |ADD referrer text""".stripMargin,
        ),
        // a down block with blank lines only is the same as an empty one
        down = Vector.empty,
      ),
    ),
    ValidFileExample(
      name = "005_attributes_are_trimmed.cql",
      expected = mkIrreversible(
        id = mkId(
          authoredAt = 1370028269L,
          descr = "surrounding whitespace is trimmed",
        ),
        up = Vector("CREATE TABLE trimmed (id uuid PRIMARY KEY)"),
      ),
    ),
    ValidFileExample(
      name = "006_attributes_in_any_order_after_preamble.cql",
      expected = mkReversible(
        id = mkId(
          // the file defines authoredAt before description, both after a discarded preamble
          authoredAt = 1370028265L,
          descr = "adds index on views url",
        ),
        up = Vector("CREATE INDEX views_url_idx ON views (url)"),
        down = Vector("DROP INDEX views_url_idx"),
      ),
    ),
    ValidFileExample(
      name = "007_authored_at_min.cql",
      expected = mkIrreversible(
        id = mkId(
          authoredAt = 1L,
          descr = "smallest accepted authoredAt",
        ),
        up = Vector("CREATE TABLE epoch_plus_one_milli (id uuid PRIMARY KEY)"),
      ),
    ),
    ValidFileExample(
      name = "008_authored_at_max.cql",
      expected = mkReversible(
        id = mkId(
          authoredAt = Long.MaxValue,
          descr = "largest accepted authoredAt",
        ),
        up = Vector("CREATE TABLE end_of_time (id uuid PRIMARY KEY)"),
        down = Vector("DROP TABLE end_of_time"),
      ),
    ),
    ValidFileExample(
      name = "009_authored_at_leading_plus_and_zeros.cql",
      expected = mkIrreversible(
        id = mkId(
          // written in the file as "+01370028268"
          authoredAt = 1370028268L,
          descr = "drops api_keys secret column",
        ),
        up = Vector(
          """ALTER TABLE api_keys
            |DROP secret""".stripMargin,
        ),
      ),
    ),
    ValidFileExample(
      name = "010_authored_at_iso_ts_second_precision.cql",
      expected = mkIrreversible(
        id = mkId(
          // written in the file as "2013-05-31T18:47:42Z"
          authoredAt = Instant.parse("2013-05-31T18:47:42Z").toEpochMilli,
          descr = "authoredAt is an ISO timestamp with seconds precision",
        ),
        up = Vector(
          """ALTER TABLE api_keys
            |DROP secret""".stripMargin,
        ),
      ),
    ),
    ValidFileExample(
      name = "011_authored_at_iso_ts_ms_precision.cql",
      expected = mkIrreversible(
        id = mkId(
          // written in the file as "2013-05-31T18:47:42.999Z"
          authoredAt = Instant.parse("2013-05-31T18:47:42.999Z").toEpochMilli,
          descr = "authoredAt is an ISO timestamp with millisecond precision",
        ),
        up = Vector(
          """ALTER TABLE api_keys
            |DROP secret""".stripMargin,
        ),
      ),
    ),
    ValidFileExample(
      name = "012_blank_lines_are_discarded.cql",
      expected = mkReversible(
        id = mkId(
          authoredAt = 1370028272L,
          descr = "creates audit table",
        ),
        up = Vector(
          """CREATE TABLE audit (
            |  id uuid PRIMARY KEY,
            |  actor text
            |)""".stripMargin,
        ),
        down = Vector("DROP TABLE audit"),
      ),
    ),
    ValidFileExample(
      name = "013_whitespace_only_lines_are_discarded.cql",
      expected = mkIrreversible(
        id = mkId(
          authoredAt = 1370028273L,
          descr = "creates whitespace tolerant table",
        ),
        up = Vector(
          """CREATE TABLE tolerant (
            |  id uuid PRIMARY KEY
            |)""".stripMargin,
        ),
      ),
    ),
    ValidFileExample(
      name = "014_near_miss_directives_are_cql.cql",
      expected = mkIrreversible(
        id = mkId(
          authoredAt = 1370028270L,
          descr = "drops legacy events table",
        ),
        // none of the comment lines is a directive, so all of them are part of the statement
        up = Vector(
          """-- see ticket ABC-1 before running this
            |--description: no space after the dashes, so not a directive
            |--  authoredAt: 42
            |  -- down: indented, so not a directive
            |-- DOWN: wrong case, so not a directive
            |DROP TABLE legacy_events""".stripMargin,
        ),
      ),
    ),
    ValidFileExample(
      // the file has no extension at all, the name is never interpreted
      name = "015_file_name_is_irrelevant",
      expected = mkReversible(
        id = mkId(
          authoredAt = 1370028266L,
          descr = "creates api_keys table",
        ),
        up = Vector("CREATE TABLE api_keys (id uuid PRIMARY KEY, secret text)"),
        down = Vector("DROP TABLE api_keys"),
      ),
    ),
    ValidFileExample(
      name = "016_multi_stage_up_only.cql",
      expected = mkIrreversible(
        id = mkId(
          authoredAt = 1469630067000L,
          descr = "backfills sessions from logins",
        ),
        up = Vector(
          """CREATE TABLE sessions_by_user (
            |  user_id uuid,
            |  started_at timeuuid,
            |  PRIMARY KEY (user_id, started_at)
            |)""".stripMargin,
          """ALTER TABLE sessions_by_user
            |ADD ip inet""".stripMargin,
          "CREATE INDEX sessions_by_user_ip_idx ON sessions_by_user (ip)",
        ),
      ),
    ),
    ValidFileExample(
      name = "017_multi_stage_up_and_down.cql",
      expected = mkReversible(
        id = mkId(
          authoredAt = 1469630066000L,
          descr = "creates users and groups tables",
        ),
        up = Vector(
          "CREATE TABLE groups (id uuid, name text, PRIMARY KEY (id))",
          "CREATE TABLE users (id uuid, group_id uuid, PRIMARY KEY (id))",
        ),
        // down stages are written in reversal order, they are not auto-reversed
        down = Vector(
          "DROP TABLE users",
          "DROP TABLE groups",
        ),
      ),
    ),
    ValidFileExample(
      name = "018_down_stages_are_in_reversal_order.cql",
      expected = mkReversible(
        id = mkId(
          authoredAt = 1469630068000L,
          descr = "splits payments into two tables",
        ),
        up = Vector(
          "CREATE TABLE payments (id uuid PRIMARY KEY, amount decimal)",
          """CREATE TABLE payments_by_account (
            |  account_id uuid,
            |  payment_id uuid,
            |  PRIMARY KEY (account_id, payment_id)
            |)""".stripMargin,
        ),
        down = Vector(
          "DROP TABLE payments_by_account",
          "DROP TABLE payments",
        ),
      ),
    ),
    ValidFileExample(
      name = "019_stage_labels_are_ignored.cql",
      expected = mkIrreversible(
        id = mkId(
          authoredAt = 1370028271L,
          descr = "rotates counters tables",
        ),
        // stages are in file order, the labels "2", free text and empty have no effect
        up = Vector(
          "CREATE TABLE counters_v2 (id uuid PRIMARY KEY, value counter)",
          "DROP TABLE counters_v1",
          "CREATE INDEX counters_v2_value_idx ON counters_v2 (value)",
        ),
      ),
    ),
    ValidFileExample(
      name = "020_allow_cql_line_comments_before_first_stage.cql",
      expected = mkReversible(
        id = mkId(
          authoredAt = 1713131542000L,
          descr = "allow CQL line comments between up/down and its first stage",
        ),
        up = Vector(
          "ALTER TABLE IF EXISTS session WITH default_time_to_live = 31536000;",
        ),
        down = Vector(
          "ALTER TABLE IF EXISTS session WITH default_time_to_live = 15768000;",
        ),
      ),
    ),
  )

  // lazy to get classpath scan errors on first access
  lazy val InvalidFileExamples: ArraySeq[InvalidFileExample] = InvalidFileExample.discoverOnClasspath()
}
