package com.evolution.hashira

import com.evolution.hashira.MigrationTarget.*
import org.scalacheck.Gen
import org.scalatest.Assertion

class MigrationPlanSpec extends BaseUnitSpec {

  import MigrationPlanSpec.*
  import TestData.*

  private val m1Reversible = mkReversible(mkId(1L, "m1"), up = Vector("up 1"), down = Vector("down 1"))
  private val m2Irreversible = mkIrreversible(mkId(2L, "m2"), up = Vector("up 2"))
  private val m3NoOpDown = mkReversible(mkId(3L, "m3"), up = Vector("up 3"))
  private val m4Reversible = mkReversible(mkId(4L, "m4"), up = Vector("up 4"), down = Vector("down 4"))

  // the load order does not matter as authoredAt values are unique
  private val set = MigrationSet(Vector(m4Reversible, m2Irreversible, m1Reversible, m3NoOpDown))

  private val emptyPlan = mkPlan()

  "MigrationTarget" - {
    val migrations = set.ascendingIterator.toVector

    "Initial" - {
      "has no migration in the target" in {
        migrations.map(Initial.isInTarget) shouldEqual Vector.fill(migrations.size)(false)
      }
    }

    "Latest" - {
      "has every migration in the target" in {
        migrations.map(Latest.isInTarget) shouldEqual Vector.fill(migrations.size)(true)
      }
    }

    "ToAuthoredAt" - {
      "has the migrations authored at or before the value in the target" in {
        migrations.map(toAuthoredAt(2L).isInTarget) shouldEqual Vector(true, true, false, false)
      }
    }
  }

  // AI generated - here be dragons!
  "MigrationPlan" - {
    "build" - {
      "defaults to the Latest target, without ignoring missing migrations" in {
        MigrationPlan.build(set, appliedMigrations = ids(m1Reversible)) shouldEqual
          build(set, applied = ids(m1Reversible), Latest)

        interceptOnly[AppliedMigrationsMissingException] {
          MigrationPlan.build(set, appliedMigrations = Set(mkId(10L, "missing")))
        }
      }

      "Latest" - {
        "applies every unapplied migration in the execution order" in {
          build(set, applied = ids(), Latest) shouldEqual
            mkPlan(apply = Vector(m1Reversible, m2Irreversible, m3NoOpDown, m4Reversible))
        }

        "never rolls back, even applied irreversible migrations" in {
          build(set, applied = ids(m1Reversible, m2Irreversible), Latest) shouldEqual
            mkPlan(apply = Vector(m3NoOpDown, m4Reversible))
        }

        "applies an unapplied migration older than the applied ones" in {
          build(set, applied = ids(m1Reversible, m3NoOpDown, m4Reversible), Latest) shouldEqual
            mkPlan(apply = Vector(m2Irreversible))
        }

        "plans nothing when everything is applied" in {
          build(
            set,
            applied = ids(m1Reversible, m2Irreversible, m3NoOpDown, m4Reversible),
            Latest,
          ) shouldEqual emptyPlan
        }

        "plans nothing for an empty migration set" in {
          build(MigrationSet.empty, applied = ids(), Latest) shouldEqual emptyPlan
        }
      }

      "Initial" - {
        "rolls back every applied migration in the rollback order, including a no-op down" in {
          build(
            MigrationSet(Vector(m1Reversible, m3NoOpDown, m4Reversible)),
            applied = ids(m1Reversible, m3NoOpDown, m4Reversible),
            Initial,
          ) shouldEqual
            mkPlan(rollback = Vector(m4Reversible, m3NoOpDown, m1Reversible))
        }

        "applies nothing, and does not mind unapplied irreversible migrations" in {
          build(set, applied = ids(m1Reversible, m4Reversible), Initial) shouldEqual
            mkPlan(rollback = Vector(m4Reversible, m1Reversible))
        }

        "plans nothing when nothing is applied" in {
          build(set, applied = ids(), Initial) shouldEqual emptyPlan
        }

        "fails naming every applied irreversible migration" in {
          val m5 = mkIrreversible(mkId(5L, "m5"), up = Vector("up 5"))

          val error = interceptOnly[IrreversibleRollbackException] {
            build(
              MigrationSet(Vector(m1Reversible, m2Irreversible, m3NoOpDown, m4Reversible, m5)),
              applied = ids(m1Reversible, m2Irreversible, m4Reversible, m5),
              Initial,
            )
          }

          error.ids shouldEqual ids(m2Irreversible, m5)
          error.getMessage shouldEqual
            "irreversible migrations during rollback: " +
            "{authoredAt: 2 (1970-01-01T00:00:00.002Z), description: \"m2\"}, " +
            "{authoredAt: 5 (1970-01-01T00:00:00.005Z), description: \"m5\"}"
        }
      }

      "ToAuthoredAt" - {
        "applies a migration authored exactly at the target" in {
          build(set, applied = ids(m1Reversible), toAuthoredAt(2L)) shouldEqual
            mkPlan(apply = Vector(m2Irreversible))
        }

        "does not roll back a migration authored exactly at the target" in {
          build(set, applied = ids(m1Reversible, m2Irreversible, m3NoOpDown), toAuthoredAt(3L)) shouldEqual
            emptyPlan
        }

        "rolls back the applied migrations after the target and applies the unapplied ones up to it" in {
          build(set, applied = ids(m3NoOpDown, m4Reversible), toAuthoredAt(1L)) shouldEqual
            mkPlan(rollback = Vector(m4Reversible, m3NoOpDown), apply = Vector(m1Reversible))

          build(set, applied = ids(m2Irreversible, m4Reversible), toAuthoredAt(3L)) shouldEqual
            mkPlan(rollback = Vector(m4Reversible), apply = Vector(m1Reversible, m3NoOpDown))
        }

        "does not mind irreversible migrations which are in the target or unapplied" in {
          // exactly at the target
          build(set, applied = ids(m1Reversible, m2Irreversible), toAuthoredAt(2L)) shouldEqual emptyPlan
          // before the target
          build(set, applied = ids(m1Reversible, m2Irreversible, m4Reversible), toAuthoredAt(3L)) shouldEqual
            mkPlan(rollback = Vector(m4Reversible), apply = Vector(m3NoOpDown))
          // after the target, but unapplied
          build(set, applied = ids(m1Reversible, m4Reversible), toAuthoredAt(1L)) shouldEqual
            mkPlan(rollback = Vector(m4Reversible))
        }

        "fails on an applied irreversible migration after the target" in {
          interceptOnly[IrreversibleRollbackException] {
            build(
              set,
              applied = ids(m1Reversible, m2Irreversible, m3NoOpDown, m4Reversible),
              toAuthoredAt(1L),
            )
          }.ids shouldEqual ids(m2Irreversible)
        }

        "applies migrations sharing authoredAt in the load order, and rolls them back in the reverse one" in {
          val loadedFirst = mkReversible(mkId(1L, "loaded first"), up = Vector("up"))
          val loadedSecond = mkReversible(mkId(1L, "loaded second"), up = Vector("up"))
          val loadedThird = mkReversible(mkId(1L, "loaded third"), up = Vector("up"))
          val tiedSet = MigrationSet(Vector(loadedFirst, loadedSecond, loadedThird))

          build(tiedSet, applied = ids(), toAuthoredAt(1L)) shouldEqual
            mkPlan(apply = Vector(loadedFirst, loadedSecond, loadedThird))

          build(tiedSet, applied = ids(loadedFirst, loadedSecond, loadedThird), toAuthoredAt(0L)) shouldEqual
            mkPlan(rollback = Vector(loadedThird, loadedSecond, loadedFirst))
        }
      }

      "missing applied migrations" - {
        val missingA = mkId(3L, "missing a")
        val missingB = mkId(3L, "missing b")
        val missingC = mkId(10L, "missing c")
        val missing = Set(missingA, missingB, missingC)

        val targets = Vector(Initial, toAuthoredAt(2L), Latest)

        "fail the build for every target, naming all of them" in {
          targets.foreach { target =>
            interceptOnly[AppliedMigrationsMissingException] {
              build(set, applied = ids(m1Reversible) ++ missing, target)
            }.ids shouldEqual missing
          }
        }

        "fail the build with a readable message, listing them by authoredAt" in {
          interceptOnly[AppliedMigrationsMissingException] {
            build(set, applied = Set(missingC, missingA), Latest)
          }.getMessage shouldEqual
            "applied migrations missing from the migration set: " +
            "{authoredAt: 3 (1970-01-01T00:00:00.003Z), description: \"missing a\"}, " +
            "{authoredAt: 10 (1970-01-01T00:00:00.010Z), description: \"missing c\"}"
        }

        "fail the build ahead of an irreversible rollback" in {
          interceptOnly[AppliedMigrationsMissingException] {
            build(set, applied = ids(m2Irreversible) + missingA, Initial)
          }.ids shouldEqual Set(missingA)
        }

        "when ignored" - {
          "are listed in the plan, which is otherwise unaffected, for every target" in {
            targets.foreach { target =>
              build(
                set,
                applied = ids(m1Reversible, m3NoOpDown) ++ missing,
                target,
                ignoreMissing = true,
              ) shouldEqual
                build(set, applied = ids(m1Reversible, m3NoOpDown), target).copy(ignoredMissingMigrations =
                  missing,
                )
            }
          }

          "are not listed when there are none" in {
            build(set, applied = ids(m1Reversible), Latest, ignoreMissing = true) shouldEqual
              mkPlan(apply = Vector(m2Irreversible, m3NoOpDown, m4Reversible))
          }

          "do not prevent an irreversible rollback failure" in {
            interceptOnly[IrreversibleRollbackException] {
              build(set, applied = ids(m2Irreversible) + missingA, Initial, ignoreMissing = true)
            }.ids shouldEqual ids(m2Irreversible)
          }
        }
      }

      "for any input" - {
        "rolls back only applied migrations, in the rollback order" in {
          forAllSuccessfulPlans { case SuccessfulPlan(migrationSet, applied, _, plan) =>
            val rollbackIds = plan.migrationsToRollback.map(_.id)

            rollbackIds.toSet.diff(applied) shouldBe empty
            rollbackIds shouldEqual
              migrationSet.descendingIterator.map(_.id).filter(rollbackIds.toSet).toVector
          }
        }

        "applies only unapplied migrations, in the execution order" in {
          forAllSuccessfulPlans { case SuccessfulPlan(migrationSet, applied, _, plan) =>
            val applyIds = plan.migrationsToApply.map(_.id)

            applyIds.toSet.intersect(applied) shouldBe empty
            applyIds shouldEqual migrationSet.ascendingIterator.map(_.id).filter(applyIds.toSet).toVector
          }
        }

        "reaches the target, keeping the ignored missing migrations applied" in {
          forAllSuccessfulPlans { case SuccessfulPlan(migrationSet, applied, target, plan) =>
            val appliedAfter = runPlan(applied, plan)

            appliedAfter.intersect(migrationSet.ids) shouldEqual
              migrationSet.ascendingIterator.filter(target.isInTarget).map(_.id).toSet
            appliedAfter.diff(migrationSet.ids) shouldEqual plan.ignoredMissingMigrations
            plan.ignoredMissingMigrations shouldEqual applied.diff(migrationSet.ids)
          }
        }

        "plans as if the ignored missing migrations were not applied, listing them" in {
          forAllSuccessfulPlans { case SuccessfulPlan(migrationSet, applied, target, plan) =>
            val missing = applied.diff(migrationSet.ids)

            plan shouldEqual
              build(migrationSet, applied.intersect(migrationSet.ids), target)
                .copy(ignoredMissingMigrations = missing)
          }
        }

        "plans nothing more once the plan is done" in {
          forAllSuccessfulPlans { case SuccessfulPlan(migrationSet, applied, target, plan) =>
            val appliedAfter = runPlan(applied, plan)

            build(migrationSet, appliedAfter, target, ignoreMissing = true) shouldEqual
              emptyPlan.copy(ignoredMissingMigrations = plan.ignoredMissingMigrations)
          }
        }

        "fails exactly on unignored missing, or else out-of-target irreversible, applied migrations" in {
          forAll(genInput, Gen.oneOf(false, true)) { case ((migrationSet, applied, target), ignoreMissing) =>
            val missing = applied.diff(migrationSet.ids)
            val irreversible = irreversibleOutOfTarget(migrationSet, applied, target)

            def buildPlan(): MigrationPlan = build(migrationSet, applied, target, ignoreMissing)

            if (missing.nonEmpty && !ignoreMissing) {
              interceptOnly[AppliedMigrationsMissingException] {
                buildPlan()
              }.ids shouldEqual missing
            } else if (irreversible.nonEmpty) {
              interceptOnly[IrreversibleRollbackException] {
                buildPlan()
              }.ids shouldEqual irreversible
            } else {
              noException should be thrownBy buildPlan()
            }
          }
        }
      }
    }
  }

  "MigrationPlanException" - {
    "AppliedMigrationsMissingException" - {
      "does not allow an empty ID set" in {
        interceptOnly[IllegalArgumentException] {
          AppliedMigrationsMissingException(Set.empty)
        }.getMessage shouldEqual "requirement failed: ID set must not be empty"
      }
    }

    "IrreversibleRollbackException" - {
      "does not allow an empty ID set" in {
        interceptOnly[IllegalArgumentException] {
          IrreversibleRollbackException(Set.empty)
        }.getMessage shouldEqual "requirement failed: ID set must not be empty"
      }
    }
  }

  private def build(
    migrationSet: MigrationSet,
    applied: Set[MigrationId],
    target: MigrationTarget,
    ignoreMissing: Boolean = false,
  ): MigrationPlan = {
    MigrationPlan.build(
      migrationSet = migrationSet,
      appliedMigrations = applied,
      target = target,
      ignoreMissingMigrations = ignoreMissing,
    )
  }

  private def ids(migrations: Migration*): Set[MigrationId] = migrations.map(_.id).toSet

  private def toAuthoredAt(epochMillis: Long): MigrationTarget = {
    ToAuthoredAt(MigrationAuthoredAt(epochMillis))
  }

  /**
   * Applied migrations after the plan is executed.
   */
  private def runPlan(applied: Set[MigrationId], plan: MigrationPlan): Set[MigrationId] = {
    applied -- plan.migrationsToRollback.map(_.id) ++ plan.migrationsToApply.map(_.id)
  }

  /**
   * Applied irreversible migrations which are not in the target, and so would have to be
   * rolled back.
   */
  private def irreversibleOutOfTarget(
    migrationSet: MigrationSet,
    applied: Set[MigrationId],
    target: MigrationTarget,
  ): Set[MigrationId] = {
    migrationSet
      .ascendingIterator
      .collect {
        case migration: Migration.Irreversible
            if applied.contains(migration.id) && !target.isInTarget(migration) =>
          migration.id
      }
      .toSet
  }

  // a narrow authoredAt range to have many migrations sharing it
  private val genAuthoredAt: Gen[Long] = Gen.choose(1L, 6L)

  // A migration set does not shrink, so it grows with the scalacheck size to have the first
  // failing inputs small. The empty migration set is left to the example tests.
  private val genMigrationSet: Gen[MigrationSet] = for {
    size <- Gen.sized(maxSize => Gen.choose(1, maxSize.max(1).min(12)))
    migrations <- Gen.listOfN(
      size,
      Gen.zip(genAuthoredAt, Gen.prob(0.85)),
    )
  } yield MigrationSet(
    migrations.zipWithIndex.map { case ((authoredAt, reversible), index) =>
      val id = mkId(authoredAt, s"migration $index")
      if (reversible) {
        mkReversible(id, up = Vector(s"up $index"), down = Vector(s"down $index"))
      } else {
        mkIrreversible(id, up = Vector(s"up $index"))
      }
    },
  )

  private def genAppliedMigrations(migrationSet: MigrationSet): Gen[Set[MigrationId]] = for {
    known <- Gen.someOf(migrationSet.ids)
    missingCount <- Gen.choose(0, 3)
    missingAuthoredAts <- Gen.listOfN(missingCount, genAuthoredAt)
  } yield known.toSet ++ missingAuthoredAts.zipWithIndex.map { case (authoredAt, index) =>
    mkId(authoredAt, s"missing $index")
  }

  // ToAuthoredAt is weighted higher as the only target which can both roll back and apply
  private val genTarget: Gen[MigrationTarget] = Gen.frequency(
    1 -> Gen.const(Initial),
    2 -> Gen.choose(0L, 7L).map(toAuthoredAt),
    1 -> Gen.const(Latest),
  )

  private val genInput: Gen[(MigrationSet, Set[MigrationId], MigrationTarget)] = for {
    migrationSet <- genMigrationSet
    applied <- genAppliedMigrations(migrationSet)
    target <- genTarget
  } yield (migrationSet, applied, target)

  // Drops the applied irreversible migrations out of the target instead of discarding the input,
  // not to skew the distribution. Shrinking only drops applied migrations, keeping the input valid.
  private val genSuccessfulInput: Gen[(MigrationSet, Set[MigrationId], MigrationTarget)] =
    genInput.map { case (migrationSet, applied, target) =>
      (migrationSet, applied.diff(irreversibleOutOfTarget(migrationSet, applied, target)), target)
    }

  /**
   * Checks plans for generated inputs not requiring an irreversible rollback, ignoring
   * the missing migrations.
   */
  private def forAllSuccessfulPlans(check: SuccessfulPlan => Assertion): Assertion = {
    forAll(genSuccessfulInput) { case (migrationSet, applied, target) =>
      check(SuccessfulPlan(
        migrationSet,
        applied,
        target,
        build(migrationSet, applied, target, ignoreMissing = true),
      ))
    }
  }
}

private object MigrationPlanSpec {

  private final case class SuccessfulPlan(
    migrationSet: MigrationSet,
    applied: Set[MigrationId],
    target: MigrationTarget,
    plan: MigrationPlan,
  )
}
