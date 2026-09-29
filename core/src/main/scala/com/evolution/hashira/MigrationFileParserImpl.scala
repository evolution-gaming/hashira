package com.evolution.hashira

import java.time.Instant
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.util.Try

/**
 * Recursive-descent-style implementation for [[MigrationFileParser]].
 *
 * Instantiate, call [[parse()]] once and discard the instance.
 *
 * Stateful, non-threadsafe.
 *
 * @param fileRef
 *   string indicating the origin of the file, e.g. file path on the file system; used for
 *   error reporting
 * @param linesIt
 *   file lines iterator
 */
private[hashira] final class MigrationFileParserImpl(
  fileRef: String,
  linesIt: Iterator[String],
) {
  import MigrationFileParserImpl.*

  private val errors: mutable.ArrayBuffer[MigrationFileFormatError] = mutable.ArrayBuffer.empty

  private var lineNum: Int = 0 // first pull will set it to 1, lines numeration starts with 1
  private var endOfInputReached: Boolean = false

  // for scalar attributes we use here (defined: Boolean, validValue: T) instead of Option[T]
  // this allows us not to report needless "X missing" errors when the value has already
  // been reported before as invalid

  private var authoredAtDefined: Boolean = false
  private var authoredAtValue: Long = -1 // fake initial value
  private var descriptionDefined: Boolean = false
  private var descriptionValue: String = "FAKE_UNREACHABLE_VALUE" // fake initial value

  private val curStatementLines = mutable.ArrayBuffer.empty[String]

  private var upStartLine: Int = 0 // invalid initial value
  private val upStatements = mutable.ArrayBuffer.empty[String]
  private var downSectionDefined: Boolean = false
  private val downStatements = mutable.ArrayBuffer.empty[String]

  /**
   * Parses the migration file.
   *
   * @throws MigrationFileInvalidException
   *   if the file format is invalid
   */
  def parse(): Migration = {
    pullPreamble()

    if (errors.nonEmpty) {
      throw MigrationFileInvalidException(
        fileRef = fileRef,
        // sort by line for nicer representation, file errors go first
        errors = errors.to(ArraySeq).sortBy(_.lineNum.getOrElse(0)),
      )
    }
    // all validations must have been performed already, just creating the migration
    if (downSectionDefined) {
      Migration.Reversible(
        id = MigrationId(
          authoredAt = MigrationAuthoredAt(authoredAtValue),
          description = descriptionValue,
        ),
        upStatements = upStatements.to(ArraySeq),
        downStatements = downStatements.to(ArraySeq),
      )
    } else {
      Migration.Irreversible(
        id = MigrationId(
          authoredAt = MigrationAuthoredAt(authoredAtValue),
          description = descriptionValue,
        ),
        upStatements = upStatements.to(ArraySeq),
      )
    }
  }

  @tailrec
  private def pullPreamble(): Unit = {
    nextToken() match {
      case _: Token.CqlLine =>
        // skip before up section
        pullPreamble()

      case Token.DirectiveLine(Directive.description, value) =>
        readDescription(value)
        pullPreamble()

      case Token.DirectiveLine(Directive.authoredAt, value) =>
        readAuthoredAt(value)
        pullPreamble()

      case Token.DirectiveLine(Directive.up, _) =>
        finalizePreamble()
        this.upStartLine = this.lineNum
        pullUpSection()

      case Token.EndOfInput =>
        // reporting all missing required parts
        finalizePreamble() // will report preamble attributes
        reportError("missing up statements section")

      case token: Token.DirectiveLine =>
        reportUnexpectedDirectiveInPreamble(token)
        pullPreamble() // continuing parsing to report all errors
    }
  }

  @tailrec
  private def pullUpSection(): Unit = {
    nextToken() match {
      case Token.DirectiveLine(Directive.stage, _) =>
        pullStageUpStatement(stageStartLine = this.lineNum)

      case Token.CqlLine(firstStatementLine) =>
        this.curStatementLines += firstStatementLine
        pullNakedUpStatement()

      case Token.DirectiveLine(Directive.down, _) =>
        // only blank lines in the up section, encountering down
        finalizeUpSection() // this should properly report the failure
        pullDownSection() // continuing parsing to report all errors

      case Token.EndOfInput =>
        // only blank lines in the up section, the file ended abruptly
        finalizeUpSection() // this should properly report the failure

      case token: Token.DirectiveLine =>
        reportUnexpectedDirectiveInUp(token)
        // continue pulling to report all errors
        pullUpSection()
    }
  }

  @tailrec
  private def pullNakedUpStatement(): Unit = {
    nextToken() match {
      case Token.CqlLine(statementLine) =>
        this.curStatementLines += statementLine
        pullNakedUpStatement()

      case Token.EndOfInput =>
        finalizeUpSection()

      case Token.DirectiveLine(Directive.down, _) =>
        finalizeUpSection()
        pullDownSection()

      case Token.DirectiveLine(Directive.stage, _) =>
        if (!curStatementLines.forall(isCqlFullLineComment)) {
          reportNakedStatementBeforeFirstStage()
          // recovering: the naked statement becomes an implicit first stage,
          // so the following stages are checked as usual and the error is not repeated for each of them
          flushStatementIfNonEmpty(where = upStatements)
        } else {
          // all CQL lines between up and stage are CQL comment lines - safe to discard
          curStatementLines.clear()
        }
        pullStageUpStatement(stageStartLine = this.lineNum)

      case token: Token.DirectiveLine =>
        reportUnexpectedDirectiveInUp(token)
        // continue pulling to report all errors
        pullNakedUpStatement()
    }
  }

  @tailrec
  private def pullStageUpStatement(stageStartLine: Int): Unit = {
    nextToken() match {
      case Token.CqlLine(statementLine) =>
        this.curStatementLines += statementLine
        pullStageUpStatement(stageStartLine = stageStartLine)

      case Token.DirectiveLine(Directive.stage, _) =>
        finalizeStage(stageStartLine = stageStartLine, flushWhere = upStatements)
        pullStageUpStatement(stageStartLine = this.lineNum)

      case Token.EndOfInput =>
        finalizeStage(stageStartLine = stageStartLine, flushWhere = upStatements)
        finalizeUpSection()

      case Token.DirectiveLine(Directive.down, _) =>
        finalizeStage(stageStartLine = stageStartLine, flushWhere = upStatements)
        finalizeUpSection()
        pullDownSection()

      case token: Token.DirectiveLine =>
        reportUnexpectedDirectiveInUp(token)
        // continue pulling to report all errors
        pullStageUpStatement(stageStartLine = stageStartLine)
    }
  }

  @tailrec
  private def pullDownSection(): Unit = {
    this.downSectionDefined = true

    nextToken() match {
      case Token.DirectiveLine(Directive.stage, _) =>
        pullStageDownStatement(stageStartLine = this.lineNum)

      case Token.CqlLine(firstStatementLine) =>
        this.curStatementLines += firstStatementLine
        pullNakedDownStatement()

      case Token.EndOfInput =>
        flushStatementIfNonEmpty(where = downStatements)

      case token: Token.DirectiveLine =>
        reportUnexpectedDirectiveInDown(token)
        // continue pulling to report all errors
        pullDownSection()
    }
  }

  @tailrec
  private def pullNakedDownStatement(): Unit = {
    nextToken() match {
      case Token.CqlLine(statementLine) =>
        this.curStatementLines += statementLine
        pullNakedDownStatement()

      case Token.EndOfInput =>
        flushStatementIfNonEmpty(where = downStatements)

      case Token.DirectiveLine(Directive.stage, _) =>
        if (!curStatementLines.forall(isCqlFullLineComment)) {
          reportNakedStatementBeforeFirstStage()
          // recovering: the naked statement becomes an implicit first stage,
          // so the following stages are checked as usual and the error is not repeated for each of them
          flushStatementIfNonEmpty(where = downStatements)
        } else {
          // all CQL lines between up and stage are CQL comment lines - safe to discard
          curStatementLines.clear()
        }

        pullStageDownStatement(stageStartLine = this.lineNum)

      case token: Token.DirectiveLine =>
        reportUnexpectedDirectiveInDown(token)
        // continue pulling to report all errors
        pullNakedDownStatement()
    }
  }

  @tailrec
  private def pullStageDownStatement(stageStartLine: Int): Unit = {
    nextToken() match {
      case Token.CqlLine(statementLine) =>
        this.curStatementLines += statementLine
        pullStageDownStatement(stageStartLine = stageStartLine)

      case Token.DirectiveLine(Directive.stage, _) =>
        finalizeStage(stageStartLine = stageStartLine, flushWhere = downStatements)
        pullStageDownStatement(stageStartLine = this.lineNum)

      case Token.EndOfInput =>
        finalizeStage(stageStartLine = stageStartLine, flushWhere = downStatements)

      case token: Token.DirectiveLine =>
        reportUnexpectedDirectiveInDown(token)
        // continue pulling to report all errors
        pullStageDownStatement(stageStartLine = stageStartLine)
    }
  }

  private def readDescription(value: String): Unit = {
    if (this.descriptionDefined) {
      reportError("description already defined")
    } else {
      this.descriptionDefined = true
      if (value.isEmpty) {
        reportError(s"description invalid value: must not be empty")
      } else {
        this.descriptionValue = value
      }
    }
  }

  private def readAuthoredAt(valueStr: String): Unit = {
    if (this.authoredAtDefined) {
      reportError("authoredAt already defined")
    } else {
      this.authoredAtDefined = true

      validateAuthoredAt(valueStr) match {
        case Right(authoredAt) =>
          this.authoredAtValue = authoredAt
        case Left(error) =>
          reportError(s"authoredAt invalid value: $error")
      }
    }
  }

  private def finalizePreamble(): Unit = {
    var missingAttrs: Vector[String] = Vector.empty
    if (!this.authoredAtDefined) {
      missingAttrs = missingAttrs :+ Directive.authoredAt.name
    }
    if (!this.descriptionDefined) {
      missingAttrs = missingAttrs :+ Directive.description.name
    }
    if (missingAttrs.nonEmpty) {
      reportError(
        s"missing required attributes before the up section: ${ missingAttrs.mkString(", ") }",
      )
    }
  }

  private def finalizeUpSection(): Unit = {
    flushStatementIfNonEmpty(where = upStatements)
    if (upStatements.isEmpty) {
      reportError("no CQL statements in the up section", line = this.upStartLine)
    }
  }

  private def finalizeStage(stageStartLine: Int, flushWhere: mutable.ArrayBuffer[String]): Unit = {
    if (curStatementLines.isEmpty) {
      reportError("no CQL statements in the stage", line = stageStartLine)
    }
    flushStatementIfNonEmpty(where = flushWhere)
  }

  private def flushStatementIfNonEmpty(where: mutable.ArrayBuffer[String]): Unit = {
    if (curStatementLines.nonEmpty) {
      where += curStatementLines.mkString("\n")
      curStatementLines.clear()
    }
  }

  private def reportError(description: String, line: Int = this.lineNum): Unit = {
    // the file is empty, or we are past the file end - line numbers won't be helpful here
    if (line == 0 || (this.endOfInputReached && line == this.lineNum)) {
      errors += MigrationFileFormatError(lineNum = None, description = description)
    } else {
      errors += MigrationFileFormatError(lineNum = Some(line), description = description)
    }
  }

  private def reportUnexpectedDirective(token: Token.DirectiveLine, where: String): Unit = {
    reportError(s"unexpected '${ fmtDirLinePrefix(token.tpe) }' $where")
  }

  private def reportUnexpectedDirectiveInPreamble(token: Token.DirectiveLine): Unit = {
    reportUnexpectedDirective(token, where = s"before '${ fmtDirLinePrefix(Directive.up) }'")
  }

  private def reportUnexpectedDirectiveInUp(token: Token.DirectiveLine): Unit = {
    reportUnexpectedDirective(token, where = s"after '${ fmtDirLinePrefix(Directive.up) }'")
  }

  private def reportUnexpectedDirectiveInDown(token: Token.DirectiveLine): Unit = {
    reportUnexpectedDirective(token, where = s"after '${ fmtDirLinePrefix(Directive.down) }'")
  }

  // a section has either only staged statements or a single naked one
  private def reportNakedStatementBeforeFirstStage(): Unit = {
    reportError("CQL statement before the first stage is not in a stage")
  }

  private def fmtDirLinePrefix(directive: Directive): String = {
    s"-- ${ directive.name }:"
  }

  @tailrec
  private def nextToken(): Token = {
    if (this.endOfInputReached) {
      Token.EndOfInput
    } else {
      this.lineNum += 1
      linesIt.nextOption() match {
        case Some(line) =>
          if (line.isBlank) {
            // skip blank lines
            nextToken()
          } else {
            Token.fromNonBlankLine(line)
          }
        case None =>
          this.endOfInputReached = true
          Token.EndOfInput
      }
    }
  }
}

private object MigrationFileParserImpl {
  import MigrationFileParser.{MaxAuthoredAt, MinAuthoredAt}

  private sealed trait Token
  private object Token {
    case object EndOfInput extends Token
    final case class CqlLine(line: String) extends Token
    final case class DirectiveLine(tpe: Directive, value: String) extends Token

    def fromNonBlankLine(line: String): Token = {
      val directiveOpt = line match {
        case s"-- $directiveName:$rawValue" =>
          Directive.findByName(directiveName).map { directive =>
            DirectiveLine(directive, value = rawValue.strip())
          }
        case _ =>
          None
      }

      directiveOpt.getOrElse(CqlLine(line))
    }
  }

  private sealed trait Directive extends Product {
    final def name: String = productPrefix
  }
  private object Directive {
    private lazy val values: ArraySeq[Directive] = ArraySeq(
      authoredAt,
      description,
      up,
      down,
      stage,
    )
    private lazy val byName: Map[String, Directive] = values.map(v => v.name -> v).toMap

    def findByName(name: String): Option[Directive] = byName.get(name)

    case object authoredAt extends Directive
    case object description extends Directive
    case object up extends Directive
    case object down extends Directive
    case object stage extends Directive
  }

  private object IsoTimestampStr {
    def unapply(str: String): Option[Instant] = {
      Try {
        Instant.parse(str)
      }.toOption
    }
  }

  private object IntegerStr {
    // BigInt instead of Long, so that an out of range number is reported as such, not as a non-number
    def unapply(str: String): Option[BigInt] = {
      Try {
        BigInt(str)
      }.toOption
    }
  }

  private def hasSubMillisPrecision(instant: Instant): Boolean = {
    (instant.getNano % 1000000) != 0
  }

  private def validateAuthoredAt(str: String): Either[String, Long] = {
    str match {
      case IsoTimestampStr(timestamp) =>
        if (hasSubMillisPrecision(timestamp)) {
          Left("ISO timestamp string must have millisecond precision, sub-millisecond values not supported")
        } else {
          validateAuthoredAt(epochMillis = getEpochMillisNoOverflow(timestamp))
        }
      case IntegerStr(millis) =>
        validateAuthoredAt(epochMillis = millis)
      case _ =>
        Left("must be a number of milliseconds since UNIX epoch or an ISO timestamp string such as 2007-12-03T10:15:30.00Z")
    }
  }

  private def validateAuthoredAt(epochMillis: BigInt): Either[String, Long] = {
    if (epochMillis < BigInt(MinAuthoredAt.epochMillis)) {
      // MigrationAuthoredAt toString gives us here both the numerical value and the ISO timestamp
      Left(s"must be >= $MinAuthoredAt")
    } else if (epochMillis > BigInt(MaxAuthoredAt.epochMillis)) {
      Left(s"must be <= $renderMaxAuthoredAt")
    } else {
      Right(epochMillis.toLong)
    }
  }

  // custom rendering of the max value - using 2^63-1 instead of 9223372036854775807
  private def renderMaxAuthoredAt: String = s"2^63-1 (${ MaxAuthoredAt.toInstant })"

  private def getEpochMillisNoOverflow(ts: Instant): BigInt = {
    BigInt(ts.getEpochSecond) * 1000 + ts.getNano / 1_000_000
  }

  private def isCqlFullLineComment(line: String): Boolean = {
    line.stripLeading().startsWith("-- ")
  }
}
