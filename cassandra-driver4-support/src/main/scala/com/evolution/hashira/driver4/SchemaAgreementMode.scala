package com.evolution.hashira.driver4

/**
 * Controls how schema agreement is checked after DDL statements.
 */
sealed trait SchemaAgreementMode

object SchemaAgreementMode {

  /**
   * Skip schema agreement check entirely.
   */
  case object Disabled extends SchemaAgreementMode

  /**
   * Check schema agreement and log a warning if nodes disagree.
   */
  case object Warn extends SchemaAgreementMode

  /**
   * Check schema agreement and throw an error if nodes disagree.
   */
  case object Fail extends SchemaAgreementMode

  def fromString(s: String): SchemaAgreementMode = s match {
    case "disabled" => Disabled
    case "warn" => Warn
    case "fail" => Fail
    case other => sys.error(s"Unknown schemaAgreementMode: $other. Must be one of: disabled, warn, fail")
  }
}
