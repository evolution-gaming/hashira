package com.evolution.hashira.driver4

import com.datastax.oss.driver.api.core.CqlSession
import com.evolution.hashira.driver_tck.*

import java.net.InetSocketAddress
import scala.jdk.CollectionConverters.*

class Driver4SupportTckSpec extends BaseMigratorDriverSupportTckSpec[CqlSession] {

  override protected def makeClient(contactPoint: InetSocketAddress, localDatacenter: String): CqlSession = {
    CqlSession.builder()
      .addContactPoint(contactPoint)
      .withLocalDatacenter(localDatacenter)
      .build()
  }

  override protected def closeClient(client: CqlSession): Unit = {
    client.close()
  }

  override protected def makeTestClient(client: CqlSession): TestClient = {
    new TestClient {
      override def runCql(cql: String): Vector[TestRow] = {
        val rs = client.execute(cql)
        val columnCount = rs.getColumnDefinitions.size()
        rs.all().asScala.view.map(row => TestRow(Vector.tabulate(columnCount)(row.getString))).toVector
      }

      override def close(): Unit = ()
    }
  }
}
