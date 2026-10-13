package com.evolution.hashira

import com.evolution.hashira.MigrationTarget.Latest

import scala.collection.immutable.ArraySeq

/**
 * What a migration run has to do to reach its [[MigrationTarget]].
 *
 * The rollbacks have to be executed before the applications, in the order as they are
 * listed.
 *
 * Use [[MigrationPlan.build]] to build a valid migration plan.
 *
 * @param migrationsToRollback
 *   applied migrations which are not in the target and have to be rolled back, in the
 *   rollback order (see [[MigrationSet]] `descendingIterator`)
 * @param migrationsToApply
 *   migrations which are in the target but haven't been applied yet, in the execution
 *   order (see [[MigrationSet]] `ascendingIterator`)
 * @param ignoredMissingMigrations
 *   IDs of the already applied migrations which are missing from the target migration set
 *   and have been ignored. See [[AppliedMigrationsMissingException]] for details about
 *   the case. This field is filled out only if `ignoreMissingMigrations` is set during
 *   plan building.
 */
final case class MigrationPlan(
  migrationsToRollback: ArraySeq[Migration.Reversible],
  migrationsToApply: ArraySeq[Migration],
  ignoredMissingMigrations: Set[MigrationId],
)

object MigrationPlan {

  /**
   * Builds [[MigrationPlan]] given the [[MigrationSet]], currently known applied
   * migrations and [[MigrationTarget]].
   *
   * An applied migration is rolled back when it is not in the target. A migration is
   * applied when it is in the target and not applied yet, even if newer migrations were
   * already applied.
   *
   * @param migrationSet
   *   [[MigrationSet]] to apply
   * @param appliedMigrations
   *   IDs of the already applied migrations
   * @param target
   *   [[MigrationTarget]] to reach
   * @param ignoreMissingMigrations
   *   if true, suppresses [[AppliedMigrationsMissingException]], with missing migration
   *   IDs returned in the plan for the caller to log.
   *
   * @throws MigrationPlanException
   *   if a valid plan can't be built due to unsatisfiable constraints
   */
  def build(
    migrationSet: MigrationSet,
    appliedMigrations: Set[MigrationId],
    target: MigrationTarget = Latest,
    ignoreMissingMigrations: Boolean = false,
  ): MigrationPlan = {
    val missingIds = appliedMigrations.diff(migrationSet.ids)

    if (missingIds.nonEmpty && !ignoreMissingMigrations) {
      throw AppliedMigrationsMissingException(missingIds)
    }

    val outOfTargetRollbackOrder = migrationSet
      .descendingIterator
      .filter(migration => appliedMigrations.contains(migration.id))
      .filter(!target.isInTarget(_))
      .to(ArraySeq)

    val irreversibleOutOfTargetIds = outOfTargetRollbackOrder.collect {
      case m: Migration.Irreversible => m.id
    }.toSet
    if (irreversibleOutOfTargetIds.nonEmpty) {
      throw IrreversibleRollbackException(irreversibleOutOfTargetIds)
    }

    MigrationPlan(
      migrationsToRollback = outOfTargetRollbackOrder.collect {
        case m: Migration.Reversible => m
      },
      migrationsToApply = migrationSet
        .ascendingIterator
        .filter(migration => !appliedMigrations.contains(migration.id))
        .filter(target.isInTarget)
        .to(ArraySeq),
      ignoredMissingMigrations = missingIds,
    )
  }
}

/**
 * Defines the desired target state for a migration run.
 *
 *   - out of target applied migrations are rolled back
 *   - in-target migrations which haven't been applied, are applied
 *
 * @see
 *   [[MigrationPlan]]
 */
sealed trait MigrationTarget {

  /**
   * Whether a migration is in target for a migration run.
   */
  def isInTarget(migration: Migration): Boolean
}

object MigrationTarget {

  /**
   * Brings the store to the initial state - all applied migrations have to be rolled
   * back.
   */
  case object Initial extends MigrationTarget {
    override def isInTarget(migration: Migration): Boolean = false
  }

  /**
   * Brings the store to the target [[MigrationAuthoredAt]] (inclusive).
   *
   * Migrations authored at or before the cut-off value are in the target.
   *
   * Migrations authored after the cut-off value have to be rolled back, if already
   * applied before.
   *
   * @param value
   *   the latest `authoredAt` in the target
   */
  final case class ToAuthoredAt(value: MigrationAuthoredAt) extends MigrationTarget {
    override def isInTarget(migration: Migration): Boolean = {
      migration.authoredAt <= value
    }
  }

  /**
   * Brings the store to the latest state - all yet not applied migrations should be
   * applied, no migration is rolled back.
   */
  case object Latest extends MigrationTarget {
    override def isInTarget(migration: Migration): Boolean = true
  }
}

/**
 * Migration plan can't be built due to unsatisfiable constraints.
 *
 * @see
 *   [[AppliedMigrationsMissingException]] [[IrreversibleRollbackException]]
 */
sealed abstract class MigrationPlanException(message: String) extends RuntimeException(message)

/**
 * Migration plan can't be built due to some already applied migrations missing from the
 * target migration set.
 *
 * Potential cause: existing migration ID attributes were edited, or some migrations were
 * deleted entirely from the migration set, after they have already been applied to the
 * Cassandra store.
 *
 * @param ids
 *   missing migration ID set, non-empty
 * @throws java.lang.IllegalArgumentException
 *   if the migration ID set is empty
 *
 * @see
 *   [[MigrationPlanException]]
 */
final case class AppliedMigrationsMissingException(
  ids: Set[MigrationId],
) extends MigrationPlanException(
  s"applied migrations missing from the migration set: ${ ToStringUtils.fmtNonEmptyIdSet(ids) }",
) {
  require(ids.nonEmpty, "ID set must not be empty")
}

/**
 * Migration plan can be built due to some irreversible migrations requiring a rollback
 * with the given migration target.
 *
 * @param ids
 *   offending irreversible migrations ID set, non-empty
 * @throws java.lang.IllegalArgumentException
 *   if the migration ID set is empty
 *
 * @see
 *   [[MigrationPlanException]]
 */
final case class IrreversibleRollbackException(
  ids: Set[MigrationId],
) extends MigrationPlanException(
  s"irreversible migrations during rollback: ${ ToStringUtils.fmtNonEmptyIdSet(ids) }",
) {
  require(ids.nonEmpty, "ID set must not be empty")
}
