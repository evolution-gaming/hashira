package com.evolution.hashira

class MigrationFileParserSpec extends BaseUnitSpec {
  import MigrationFileFormatExamples.*

  "MigrationFileParser" - {
    "should be tested against examples" - {
      "valid" - {
        ValidFileExamples.foreach { example =>
          example.name in {
            parse(example.readFile()) shouldEqual example.expected
          }
        }
      }

      "invalid" - {
        InvalidFileExamples.foreach { example =>
          example.name in {
            val fileContent = example.readFile()

            val exception = interceptOnly[MigrationFileInvalidException] {
              parse(fileContent.contentToParse)
            }

            // this also verifies that the errors are reported in the file order:
            // - file-level errors coming first
            // - line-level errors in line order
            exception.errors shouldEqual
              fileContent.expectedErrors.sortBy(_.lineNum.getOrElse(0))
          }
        }
      }
    }
  }

  private def parse(fileContent: String): Migration = {
    MigrationFileParser.parse(fileContent)
  }
}
