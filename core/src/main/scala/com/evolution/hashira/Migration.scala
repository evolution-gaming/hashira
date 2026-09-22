package com.evolution.hashira

import scala.collection.immutable

/**
 * Migration - logical unit of schema change.
 *
 * Identified by [[MigrationId]] - authoredAt timestamp + description line.
 *
 * All statements in a single migration are checkpointed in the applied migrations table
 * together with a single row write.
 *
 * Migrations can be:
 *   - [[Migration.Irreversible]] - reverting such migration is impossible and results in
 *     a failure
 *   - [[Migration.Reversible]] - possible to revert, though the revert can be a no-op
 *     (empty list of statements)
 *
 * @param id
 *   see [[MigrationId]]
 * @param upStatements
 *   list of statements to be executed in the supplied order when the migration is
 *   applied, non-empty
 * @throws java.lang.IllegalArgumentException
 *   if arguments are invalid
 */
sealed abstract class Migration(val id: MigrationId, val upStatements: immutable.ArraySeq[String]) {

  require(upStatements.nonEmpty, "up statements list must not be empty")

  final def authoredAt: MigrationAuthoredAt = id.authoredAt

  final def description: String = id.description
}

object Migration {

  /**
   * See [[Migration]] doc.
   *
   * @param id
   *   see [[MigrationId]]
   * @param upStatements
   *   list of statements to be executed in the supplied order when the migration is
   *   applied, non-empty
   * @param downStatements
   *   list of statements to be executed in the supplied order when the migration is
   *   reverted; can be empty - means a no-op revert
   * @throws java.lang.IllegalArgumentException
   *   if arguments are invalid
   */
  final case class Reversible(
    override val id: MigrationId,
    override val upStatements: immutable.ArraySeq[String],
    downStatements: immutable.ArraySeq[String],
  ) extends Migration(id, upStatements = upStatements) {
    override def toString: String = {
      s"Migration.Reversible($id, up: ${ fmtStatements(upStatements) }, down: ${ fmtStatements(downStatements) })"
    }
  }

  /**
   * See [[Migration]] doc.
   *
   * @param id
   *   see [[MigrationId]]
   * @param upStatements
   *   list of statements to be executed in the supplied order when the migration is
   *   applied, non-empty
   * @throws java.lang.IllegalArgumentException
   *   if arguments are invalid
   */
  final case class Irreversible(
    override val id: MigrationId,
    override val upStatements: immutable.ArraySeq[String],
  ) extends Migration(id, upStatements = upStatements) {
    override def toString: String = {
      s"Migration.Irreversible($id, up: ${ fmtStatements(upStatements) })"
    }
  }

  private def fmtStatements(statements: immutable.ArraySeq[String]): String = {
    statements.map(ToStringUtils.fmtMultilineEscaped).mkString("[", ", ", "]")
  }
}
