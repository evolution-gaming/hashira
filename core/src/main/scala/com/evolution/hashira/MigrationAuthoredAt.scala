package com.evolution.hashira

import java.time.Instant

/**
 * When a migration was authored: milliseconds since the Unix epoch.
 *
 * Part of [[MigrationId]].
 */
final case class MigrationAuthoredAt(epochMillis: Long) extends Ordered[MigrationAuthoredAt] {
  def toInstant: Instant = Instant.ofEpochMilli(epochMillis)

  /**
   * Since this is a part of [[MigrationId]], toString prints both the precise numerical
   * value of epoch millisecond and a human-readable timestamp representation.
   *
   * Output example: `1370026062000 (2013-05-31T18:47:42Z)`.
   */
  override def toString: String = s"$epochMillis ($toInstant)"

  override def compare(that: MigrationAuthoredAt): Int = {
    this.epochMillis.compareTo(that.epochMillis)
  }
}
