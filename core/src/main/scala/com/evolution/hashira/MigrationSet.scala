package com.evolution.hashira

import scala.collection.immutable.ArraySeq
import scala.collection.mutable

/**
 * Set of migrations to be applied in one migration run.
 *
 * Example: a folder with migration files designates a migration set, with each migration
 * file denoting a migration.
 *
 * Element identity: all migrations in a set have unique [[MigrationId]].
 *
 * Ordering: this set defines the migration execution order, which is
 *   - by `authoredAt` ascending (from the oldest to the newest)
 *   - and then in the load order (the order in which the migrations were given to the
 *     constructor of the set).
 *
 * Equality: two sets are equal only when they contain the same elements in the same
 * order. This means that the load order is important for equality.
 *
 * @param migrations
 *   migrations in the load order
 * @throws java.lang.IllegalArgumentException
 *   if two migrations share an identity
 * @see
 *   [[Migration]]
 */
final class MigrationSet(
  migrations: scala.collection.Iterable[Migration],
) {

  private val migrationsAsc: ArraySeq[Migration] = ArraySeq.from(migrations)
    // the sort is stable so if authoredAt is repeated, the given order is preserved
    .sortBy(_.authoredAt)

  {
    val duplicateIds = migrationsAsc
      .groupMapReduce(_.id)(_ => 1)(_ + _)
      .collect { case (id, repeatedCount) if repeatedCount > 1 => id }
      .toArray
      .sortBy(_.authoredAt)

    require(
      duplicateIds.isEmpty,
      s"duplicate migration IDs: ${ duplicateIds.mkString(", ") }",
    )
  }

  private val byIdMapAsc: mutable.LinkedHashMap[MigrationId, Migration] =
    mutable.LinkedHashMap.from(migrationsAsc.view.map(m => m.id -> m))

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: MigrationSet =>
        (this eq other) || (this.migrationsAsc == other.migrationsAsc)
      case _ =>
        false
    }
  }

  override def hashCode(): Int = {
    migrationsAsc.hashCode()
  }

  override def toString: String = {
    migrationsAsc.iterator.map(_.toString).mkString(
      start = "MigrationSet(",
      sep = ", ",
      end = ")",
    )
  }

  /**
   * View migration IDs as a set, with the iteration order matching the [[MigrationSet]]
   * order.
   */
  def ids: scala.collection.Set[MigrationId] = byIdMapAsc.keySet

  def getById(id: MigrationId): Option[Migration] = byIdMapAsc.get(id)

  def isEmpty: Boolean = migrationsAsc.isEmpty

  def size: Int = migrationsAsc.size

  /**
   * Iterates migrations in the ascending order (which is the execution order).
   *
   * See [[MigrationSet]] for more info about ordering.
   */
  def ascendingIterator: Iterator[Migration] = migrationsAsc.iterator

  /**
   * Iterates migrations in the descending order (which is the revert order).
   *
   * Inverse of [[ascendingIterator]].
   *
   * See [[MigrationSet]] for more info about ordering.
   */
  def descendingIterator: Iterator[Migration] = migrationsAsc.reverseIterator
}

object MigrationSet {

  val empty: MigrationSet = new MigrationSet(Vector.empty)

  /**
   * @see
   *   [[MigrationSet]]
   *
   * @param migrations
   *   migrations in the load order
   * @throws java.lang.IllegalArgumentException
   *   if two migrations share an identity
   */
  def apply(migrations: scala.collection.Iterable[Migration]): MigrationSet = {
    new MigrationSet(migrations)
  }
}
