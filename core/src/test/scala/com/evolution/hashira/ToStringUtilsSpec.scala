package com.evolution.hashira

import org.apache.commons.text.StringEscapeUtils

class ToStringUtilsSpec extends BaseUnitSpec {

  "fmtMultilineEscaped" - {

    "should work with an empty string" in {
      ToStringUtils.fmtMultilineEscaped("") shouldEqual "\"\""
    }

    "should yield a quoted single line output for any input" in {
      forAll { (str: String) =>
        val result = ToStringUtils.fmtMultilineEscaped(str)

        result should not(contain('\n'))
        result.head shouldEqual '"'
        result.last shouldEqual '"'
        result.length >= 2
      }
    }

    "should match JSON escaping for any input" in {
      forAll { (str: String) =>
        val result = ToStringUtils.fmtMultilineEscaped(str)

        val unquotedResult = result.drop(1).dropRight(1)

        StringEscapeUtils.unescapeJson(unquotedResult) shouldEqual str
      }
    }
  }
}
