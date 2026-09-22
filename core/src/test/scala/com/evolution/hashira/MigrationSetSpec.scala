package com.evolution.hashira

import scala.collection.immutable.ArraySeq
import scala.collection.mutable

class MigrationSetSpec extends BaseUnitSpec {

  "MigrationSet" - {

    "empty" - {
      "has no migrations" in {
        MigrationSet.empty.isEmpty shouldEqual true
        MigrationSet.empty.size shouldEqual 0
        MigrationSet.empty.ids shouldBe empty
        MigrationSet.empty.ascendingIterator.toVector shouldBe empty
        MigrationSet.empty.descendingIterator.toVector shouldBe empty
      }

      "equals a set built from an empty collection" in {
        MigrationSet(Vector.empty) shouldEqual MigrationSet.empty
        MigrationSet(Vector.empty).hashCode() shouldEqual MigrationSet.empty.hashCode()
      }

      "has a readable toString" in {
        MigrationSet.empty.toString shouldEqual "MigrationSet()"
      }
    }

    "non-empty" - {

      "ordering" - {
        "sorts migrations by authoredAt ascending, regardless of the load order" in {
          val first = mkMigration(authoredAt = 1L, description = "first")
          val second = mkMigration(authoredAt = 2L, description = "second")
          val third = mkMigration(authoredAt = 3L, description = "third")

          val set = MigrationSet(Vector(third, first, second))

          set.ascendingIterator.toVector shouldEqual Vector(first, second, third)
        }

        "keeps the load order for migrations sharing the same authoredAt (stable sort)" in {
          val loadedFirst = mkMigration(authoredAt = 1L, description = "loaded first")
          val loadedSecond = mkMigration(authoredAt = 1L, description = "loaded second")
          val loadedThird = mkMigration(authoredAt = 1L, description = "loaded third")

          val set = MigrationSet(Vector(loadedFirst, loadedSecond, loadedThird))

          set.ascendingIterator.toVector shouldEqual Vector(loadedFirst, loadedSecond, loadedThird)
        }

        "handles negative authoredAt values" in {
          val beforeEpoch = mkMigration(authoredAt = -1L, description = "before epoch")
          val epoch = mkMigration(authoredAt = 0L, description = "epoch")

          MigrationSet(Vector(epoch, beforeEpoch)).ascendingIterator.toVector shouldEqual
            Vector(beforeEpoch, epoch)
        }

        "descendingIterator is the inverse of ascendingIterator" in {
          val set = MigrationSet(
            Vector(
              mkMigration(authoredAt = 2L, description = "b"),
              mkMigration(authoredAt = 1L, description = "a"),
              mkMigration(authoredAt = 1L, description = "a too"),
            ),
          )

          set.descendingIterator.toVector shouldEqual set.ascendingIterator.toVector.reverse
        }

        "is by authoredAt ascending and then by the load order, for any input" in {
          forAll { (authoredAtValues: Vector[Long]) =>
            // descriptions make the IDs unique even when authoredAt repeats
            val loadOrder = authoredAtValues
              .zipWithIndex
              .map { case (epochMillis, index) => mkMigration(epochMillis, s"migration $index") }

            val ascending = MigrationSet(loadOrder).ascendingIterator.toVector

            ascending.map(_.authoredAt.epochMillis) shouldEqual authoredAtValues.sorted

            ascending
              .groupBy(_.authoredAt)
              .foreach { case (authoredAt, sameAuthoredAt) =>
                sameAuthoredAt shouldEqual loadOrder.filter(_.authoredAt == authoredAt)
              }
          }
        }
      }

      "identity" - {
        "rejects migrations sharing an ID" in {
          val duplicated = mkMigration(authoredAt = 1L, description = "same")

          val error = the[IllegalArgumentException] thrownBy {
            MigrationSet(Vector(duplicated, mkMigration(authoredAt = 2L, description = "other"), duplicated))
          }

          error.getMessage shouldEqual
            "requirement failed: duplicate migration IDs: " +
            "{authoredAt: 1 (1970-01-01T00:00:00.001Z), description: \"same\"}"
        }

        "rejects migrations sharing an ID even if their statements differ" in {
          val id = MigrationId(MigrationAuthoredAt(1L), "same")

          an[IllegalArgumentException] should be thrownBy {
            MigrationSet(
              Vector(
                Migration.Irreversible(id = id, upStatements = ArraySeq("up")),
                Migration.Reversible(
                  id = id,
                  upStatements = ArraySeq("other up"),
                  downStatements = ArraySeq.empty,
                ),
              ),
            )
          }
        }

        "reports all the duplicated IDs, ordered by authoredAt" in {
          val early = mkMigration(authoredAt = 1L, description = "early")
          val late = mkMigration(authoredAt = 2L, description = "late")

          val error = the[IllegalArgumentException] thrownBy {
            MigrationSet(Vector(late, early, late, early))
          }

          error.getMessage shouldEqual
            "requirement failed: duplicate migration IDs: " +
            "{authoredAt: 1 (1970-01-01T00:00:00.001Z), description: \"early\"}, " +
            "{authoredAt: 2 (1970-01-01T00:00:00.002Z), description: \"late\"}"
        }

        "accepts migrations sharing authoredAt but not the description" in {
          val set = MigrationSet(
            Vector(
              mkMigration(authoredAt = 1L, description = "a"),
              mkMigration(authoredAt = 1L, description = "b"),
            ),
          )

          set.size shouldEqual 2
        }

        "accepts migrations sharing the description but not authoredAt" in {
          val set = MigrationSet(
            Vector(
              mkMigration(authoredAt = 1L, description = "same"),
              mkMigration(authoredAt = 2L, description = "same"),
            ),
          )

          set.size shouldEqual 2
        }
      }

      "lookup" - {
        val first = mkMigration(authoredAt = 2L, description = "first")
        val second = mkMigration(authoredAt = 1L, description = "second")
        val set = MigrationSet(Vector(first, second))

        "size and isEmpty reflect the migration count" in {
          set.size shouldEqual 2
          set.isEmpty shouldEqual false
        }

        "ids are listed in the execution order" in {
          set.ids.toVector shouldEqual Vector(second.id, first.id)
        }

        "getById finds a known migration" in {
          set.getById(first.id) shouldEqual Some(first)
          set.getById(second.id) shouldEqual Some(second)
        }

        "getById returns None for an unknown ID" in {
          set.getById(MigrationId(MigrationAuthoredAt(3L), "unknown")) shouldEqual None
          // same authoredAt, different description
          set.getById(MigrationId(MigrationAuthoredAt(2L), "second")) shouldEqual None
        }
      }

      "equality" - {
        val migrations = Vector(
          mkMigration(authoredAt = 1L, description = "a"),
          mkMigration(authoredAt = 2L, description = "b"),
        )

        "is reflexive" in {
          val set = MigrationSet(migrations)

          set shouldEqual set
        }

        "ignores the load order when it does not affect the execution order" in {
          MigrationSet(migrations) shouldEqual MigrationSet(migrations.reverse)
          MigrationSet(migrations).hashCode() shouldEqual MigrationSet(migrations.reverse).hashCode()
        }

        "ignores the type of the source collection" in {
          MigrationSet(migrations) shouldEqual MigrationSet(migrations.view)
          MigrationSet(migrations).hashCode() shouldEqual MigrationSet(migrations.view).hashCode()
        }

        "distinguishes different migrations" in {
          MigrationSet(migrations) should not equal MigrationSet(migrations.take(1))
          MigrationSet(migrations) should not equal MigrationSet.empty
        }

        "distinguishes migrations differing only in their statements" in {
          val id = MigrationId(MigrationAuthoredAt(1L), "a")

          MigrationSet(Vector(Migration.Irreversible(id, ArraySeq("up")))) should not equal
            MigrationSet(Vector(Migration.Irreversible(id, ArraySeq("other up"))))
        }

        "distinguishes the load order of migrations sharing authoredAt" in {
          val sameAuthoredAt = Vector(
            mkMigration(authoredAt = 1L, description = "a"),
            mkMigration(authoredAt = 1L, description = "b"),
          )

          MigrationSet(sameAuthoredAt) should not equal MigrationSet(sameAuthoredAt.reverse)
        }

        "is false for other types" in {
          MigrationSet(migrations) should not equal migrations
        }
      }

      "does not observe changes made to the source collection after construction" in {
        val source = mutable.ArrayBuffer(mkMigration(authoredAt = 1L, description = "a"))

        val set = MigrationSet(source)
        source += mkMigration(authoredAt = 2L, description = "b")

        set.size shouldEqual 1
        set shouldEqual MigrationSet(Vector(mkMigration(authoredAt = 1L, description = "a")))
      }

      "toString lists the migrations in the execution order" in {
        val set = MigrationSet(
          Vector(
            Migration.Irreversible(MigrationId(MigrationAuthoredAt(2L), "b"), ArraySeq("up b")),
            Migration.Reversible(
              MigrationId(MigrationAuthoredAt(1L), "a"),
              ArraySeq("up a"),
              ArraySeq("down a"),
            ),
          ),
        )

        set.toString shouldEqual
          "MigrationSet(" +
          "Migration.Reversible({authoredAt: 1 (1970-01-01T00:00:00.001Z), description: \"a\"}, " +
          "up: [\"up a\"], down: [\"down a\"]), " +
          "Migration.Irreversible({authoredAt: 2 (1970-01-01T00:00:00.002Z), description: \"b\"}, " +
          "up: [\"up b\"])" +
          ")"
      }
    }
  }

  private def mkMigration(authoredAt: Long, description: String): Migration = {
    Migration.Irreversible(
      id = MigrationId(MigrationAuthoredAt(authoredAt), description),
      upStatements = ArraySeq(s"up statement of '$description'"),
    )
  }
}
