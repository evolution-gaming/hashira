package com.evolution.hashira.driver3

import com.datastax.driver.core.*
import com.evolution.hashira.driver_tck.*

import java.net.InetSocketAddress
import scala.jdk.CollectionConverters.*

class Driver3SupportTckSpec extends BaseMigratorDriverSupportTckSpec[Cluster] {

  override protected def makeClient(contactPoint: InetSocketAddress, localDatacenter: String): Cluster = {
    Cluster.builder()
      .addContactPointsWithPorts(contactPoint)
      .withoutJMXReporting()
      .build()
  }

  override protected def closeClient(client: Cluster): Unit = {
    client.close()
  }

  override protected def makeTestClient(client: Cluster): TestClient = {
    val session = client.connect()

    new TestClient {

      override def runCql(cql: String): Vector[TestRow] = {
        val rs = session.execute(cql)
        val columnCount = rs.getColumnDefinitions.size()
        rs.all().asScala.view.map(row => TestRow(Vector.tabulate(columnCount)(row.getString))).toVector
      }

      override def close(): Unit = {
        session.close()
      }
    }
  }
}
