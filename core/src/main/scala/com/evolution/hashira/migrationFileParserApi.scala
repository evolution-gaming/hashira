package com.evolution.hashira

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{Files, Path}
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.*

/**
 * Migration file format parsing error.
 *
 * @param lineNum
 *   if known, 1-based number of the line in the file for each error that was found;
 *   file-level errors have no line number assigned
 * @param description
 *   error description
 *
 * @throws java.lang.IllegalArgumentException
 *   if line number is < 1
 *
 * @see
 *   [[MigrationFileParser]]
 */
final case class MigrationFileFormatError(
  lineNum: Option[Int],
  description: String,
) {
  require(lineNum.forall(_ >= 1), "line number must be >= 1")

  override def toString: String = {
    lineNum.fold(description) { lineNum =>
      s"[line $lineNum] $description"
    }
  }
}

/**
 * [[MigrationFileParser]] parsing failed due to the file content violating the format.
 *
 * Error order: current [[MigrationFileParser]] always sorts them by line number, with
 * file-level errors coming first.
 *
 * @param fileRef
 *   string indicating the origin of the file, e.g. file path on the file system;
 * @param errors
 *   per-line error list, must not be empty
 *
 * @throws java.lang.IllegalArgumentException
 *   if error list is empty
 */
final case class MigrationFileInvalidException(
  fileRef: String,
  errors: ArraySeq[MigrationFileFormatError],
) extends RuntimeException(
  s"in $fileRef - ${ errors.mkString("; ") }",
) {
  require(errors.nonEmpty, "error list must not be empty")

  /**
   * Format as a multiline human-readable error message, with each individual error on a
   * separate line.
   */
  def fmtMultiline: String = {
    val errorLines = errors.map(e => s"\t- $e")
    s"in $fileRef:\n${ errorLines.mkString("\n") }"
  }
}

/**
 * Migration file parser.
 *
 * See
 * [[https://github.com/evolution-gaming/hashira/blob/main/docs/MIGRATION_FILE_FORMAT.md docs/MIGRATION_FILE_FORMAT.md]]
 * for the format details.
 *
 * All `parse` methods throw [[MigrationFileInvalidException]] if the file content is
 * invalid.
 *
 * Errors inside [[MigrationFileInvalidException]] are sorted by line number, with
 * file-level errors coming first.
 */
object MigrationFileParser {

  val FileCharset: Charset = StandardCharsets.UTF_8

  /**
   * Minimal valid authoredAt value: 1.
   */
  val MinAuthoredAt: MigrationAuthoredAt = MigrationAuthoredAt(epochMillis = 1L)

  /**
   * Maximal valid authoredAt value: `2^63 - 1`
   */
  val MaxAuthoredAt: MigrationAuthoredAt = MigrationAuthoredAt(epochMillis = Long.MaxValue)

  /**
   * Parses a migration file from String content.
   *
   * See [[MigrationFileParser]] for more details.
   *
   * @param fileRef
   *   string indicating the origin of the file, e.g. file path on the file system; used
   *   for error reporting
   * @param fileContent
   *   migration file content as string
   * @return
   *   parsed valid [[Migration]]
   * @throws MigrationFileInvalidException
   *   if the file content is invalid
   */
  def parse(fileRef: String, fileContent: String): Migration = {
    val parser = new MigrationFileParserImpl(
      fileRef = fileRef,
      linesIt = fileContent.lines().iterator().asScala,
    )
    parser.parse()
  }

  /**
   * Parses a migration file reading it by the given path.
   *
   * See [[MigrationFileParser]] for more details.
   *
   * Potentially blocking method, might fail on IO.
   *
   * @param filePath
   *   migration file path
   * @return
   *   parsed valid [[Migration]]
   * @throws MigrationFileInvalidException
   *   if the file content is invalid
   */
  def parse(filePath: Path): Migration = {
    val fileContent = Files.readString(filePath, FileCharset)
    val fileRef = filePath.toString

    parse(
      fileRef = fileRef,
      fileContent = fileContent,
    )
  }
}
