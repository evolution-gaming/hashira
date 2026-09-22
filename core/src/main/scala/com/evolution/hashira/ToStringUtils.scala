package com.evolution.hashira

/**
 * Internal utilities for writing cool looking toString methods
 */
private[hashira] object ToStringUtils {

  /**
   * Formats a potentially multi-line string into a quoted single-line representation,
   * which is both human-readable and reversible.
   */
  def fmtMultilineEscaped(str: String): String = {
    // the JSON escaping logic, for simplicity - the goal is to have a readable, but reversible,
    // single-line representation

    val builder = new StringBuilder(str.length + 16) // slight buffer for escaping and quotes
    builder += '"'

    var i = 0
    while (i < str.length) {
      str.charAt(i) match {
        case '"' =>
          builder ++= "\\\""
        case '\\' =>
          builder ++= "\\\\"
        case '\n' =>
          builder ++= "\\n"
        case '\r' =>
          builder ++= "\\r"
        case '\t' =>
          builder ++= "\\t"
        case '\b' =>
          builder ++= "\\b"
        case '\f' =>
          builder ++= "\\f"
        case c if c < ' ' =>
          builder ++= f"\\u${ c.toInt }%04x"
        case c =>
          builder += c
      }
      i += 1
    }

    builder += '"'

    builder.toString()
  }
}
