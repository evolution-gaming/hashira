package com.evolution.hashira.driver_tck

/**
 * Driver-agnostic CQL client for the [[BaseMigratorDriverSupportTckSpec]] assertions.
 */
trait TestClient extends AutoCloseable {

  /**
   * Executes a CQL statement.
   *
   * Returns
   *   - selected rows for a select statement
   *   - empty vector for an "update-like" statement
   *
   * CQL statement is expected to select columns only of `text` type.
   */
  def runCql(cql: String): Vector[TestRow]
}

/**
 * Test Cassandra query result row implementation for [[TestClient]].
 *
 * All column values are expected to be strings.
 *
 * @see
 *   [[BaseMigratorDriverSupportTckSpec]]
 */
final case class TestRow(columnValues: Vector[String]) {
  override def toString: String = columnValues.mkString("TestRow(", ",", ")")
}
object TestRow {
  def apply(values: String*): TestRow = TestRow(columnValues = values.toVector)
}
