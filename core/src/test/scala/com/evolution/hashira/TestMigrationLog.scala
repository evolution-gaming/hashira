package com.evolution.hashira

import scala.collection.mutable

/**
 * [[MigrationLog]] capturing logged messages for the test assertions.
 *
 * Mutable, non-thread-safe.
 */
final class TestMigrationLog extends MigrationLog {
  val infoMessages: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty
  val warnMessages: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty

  override def info(msg: => String): Unit = {
    infoMessages += msg
  }

  override def warn(msg: => String): Unit = {
    warnMessages += msg
  }
}
