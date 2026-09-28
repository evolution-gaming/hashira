package com.evolution.hashira

/**
 * A migration's identity - a [[MigrationAuthoredAt]] and a description text.
 */
final case class MigrationId(authoredAt: MigrationAuthoredAt, description: String) {

  /**
   * Nicer textual representation.
   */
  override def toString: String =
    s"{authoredAt: $authoredAt, description: ${ ToStringUtils.fmtMultilineEscaped(description) }}"
}
