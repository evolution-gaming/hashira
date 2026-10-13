package com.evolution.hashira

import scala.collection.immutable.ArraySeq

object TestData {
  def mkId(authoredAt: Long, descr: String): MigrationId = {
    MigrationId(
      authoredAt = MigrationAuthoredAt(authoredAt),
      description = descr,
    )
  }

  def mkReversible(
    id: MigrationId,
    up: IterableOnce[String],
    down: IterableOnce[String] = Vector.empty,
  ): Migration.Reversible = {
    Migration.Reversible(
      id = id,
      upStatements = ArraySeq.from(up),
      downStatements = ArraySeq.from(down),
    )
  }

  def mkIrreversible(
    id: MigrationId,
    up: IterableOnce[String],
  ): Migration.Irreversible = {
    Migration.Irreversible(
      id = id,
      upStatements = ArraySeq.from(up),
    )
  }

  def mkPlan(
    rollback: IterableOnce[Migration.Reversible] = Vector.empty,
    apply: IterableOnce[Migration] = Vector.empty,
    ignoredMissing: IterableOnce[MigrationId] = Vector.empty,
  ): MigrationPlan = {
    MigrationPlan(
      migrationsToRollback = ArraySeq.from(rollback),
      migrationsToApply = ArraySeq.from(apply),
      ignoredMissingMigrations = Set.from(ignoredMissing),
    )
  }
}
