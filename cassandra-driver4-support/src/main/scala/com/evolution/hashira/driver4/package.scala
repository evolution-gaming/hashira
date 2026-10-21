package com.evolution.hashira

import com.datastax.oss.driver.api.core.*

package object driver4 {

  /**
   * [[MigratorDriverSupport]] instance for Cassandra driver v4 [[CqlSession]].
   */
  implicit object MigratorDriver4Support extends MigratorDriverSupport[CqlSession] {
    override def makeRunner(
      driverClient: CqlSession,
      keyspace: String,
      params: MigratorParams,
    ): MigratorDriverSupport.CqlRunner = {
      makeCqlRunner(session = driverClient, keyspace = keyspace, params = params)
    }
  }

  /**
   * Creates [[MigratorDriverSupport.CqlRunner]] implementation for Cassandra driver v4.
   *
   * See [[MigratorDriverSupport.makeRunner()]] for the information about guarantees
   * method arguments.
   */
  def makeCqlRunner(session: CqlSession, keyspace: String, params: MigratorParams): Driver4CqlRunner = {
    new Driver4CqlRunner(
      session = session,
      keyspace = keyspace,
      appliedMigrationsTable = params.appliedMigrationsTable,
      consistencyLevel = DefaultConsistencyLevel.valueOf(params.consistencyLevel),
    )
  }
}
