package com.evolution.hashira

import com.datastax.driver.core.*

package object driver3 {

  /**
   * [[MigratorDriverSupport]] instance for Cassandra driver v3 [[Cluster]].
   *
   * To implement keyspace-scoping, the implementation creates a temporary session, which
   * is destroyed on CQL runner close.
   *
   * If you already happen to have a keyspace-scoped [[Session]] instance, you might use
   * [[makeSessionCqlRunner()]].
   */
  implicit object MigratorDriver3Support extends MigratorDriverSupport[Cluster] {
    override def makeRunner(
      driverClient: Cluster,
      keyspace: String,
      params: MigratorParams,
    ): MigratorDriverSupport.CqlRunner = {
      makeCqlRunner(cluster = driverClient, keyspace = keyspace, params = params)
    }
  }

  /**
   * Creates [[MigratorDriverSupport.CqlRunner]] implementation using Cassandra driver v3
   * [[Cluster]].
   *
   * Check out [[MigratorDriver3Support]] doc for the information about keyspace-scoping
   * implementation.
   *
   * See [[MigratorDriverSupport.makeRunner()]] for the information about guarantees
   * method arguments.
   */
  def makeCqlRunner(
    cluster: Cluster,
    keyspace: String,
    params: MigratorParams,
  ): Driver3ClusterCqlRunner = {
    new Driver3ClusterCqlRunner(
      cluster = cluster,
      keyspace = keyspace,
      appliedMigrationsTable = params.appliedMigrationsTable,
      consistencyLevel = ConsistencyLevel.valueOf(params.consistencyLevel),
    )
  }

  /**
   * Creates [[MigratorDriverSupport.CqlRunner]] implementation using Cassandra driver v3
   * [[Session]].
   *
   * The given session instance must be keyspace-scoped, otherwise migration CQL
   * statements will fail.
   *
   * If you don't have a session instance dedicated to a particular keyspace, use
   * [[Cluster]] based integration methods: [[makeCqlRunner()]] and
   * [[MigratorDriver3Support]].
   *
   * The runner can be passed to [[Migrator]] directly, see
   * [[MigratorDriverSupport.cqlRunnerDriverSupportInstance]].
   */
  def makeSessionCqlRunner(session: Session, params: MigratorParams): Driver3SessionCqlRunner = {
    new Driver3SessionCqlRunner(
      session = session,
      appliedMigrationsTable = params.appliedMigrationsTable,
      consistencyLevel = ConsistencyLevel.valueOf(params.consistencyLevel),
    )
  }
}
