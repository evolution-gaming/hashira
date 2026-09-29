package com.evolution.hashira

class MigrationFileParserSpec extends BaseUnitSpec {
  import MigrationFileFormatExamples.*

  "MigrationFileParser" - {
    "should be tested against examples" - {
      "valid" - {
        ValidFileExamples.foreach { example =>
          example.name in {
            val filePath = example.readFilePath()
            MigrationFileParser.parse(filePath) shouldEqual example.expected
          }
        }
      }

      "invalid" - {
        InvalidFileExamples.foreach { example =>
          example.name in {
            val fileContent = example.readFile()
            val fileRef = fileContent.rawFilePath.toString

            val exception = interceptOnly[MigrationFileInvalidException] {
              MigrationFileParser.parse(
                fileRef = fileRef,
                fileContent = fileContent.contentToParse,
              )
            }

            exception.fileRef shouldEqual fileRef

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
}
