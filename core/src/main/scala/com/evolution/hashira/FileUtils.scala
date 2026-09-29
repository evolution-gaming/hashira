package com.evolution.hashira

import io.github.classgraph.{ClassGraph, Resource}

import java.nio.ByteBuffer
import java.nio.file.*
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * Internal file utils
 */
private[hashira] object FileUtils {

  /**
   * Lists the files of a directory, non-recursively, skipping subdirectories there and
   * filtering for the provided file extension.
   *
   * The call fails if the directory doesn't exist.
   *
   * @param dirPath
   *   directory path
   * @param withExtension
   *   file extension without the dot, e.g. `cql`
   */
  def listFiles(dirPath: Path, withExtension: String): ArraySeq[Path] = {
    // case-insensitive to match the io.github.classgraph.ScanResult.getResourcesWithExtension
    // behavior
    val extensionMatcher = fileExtensionMatcherCaseInsensitive(withExtension)

    Using.resource(Files.list(dirPath)) { filesStream =>
      filesStream.iterator().asScala
        .filter(!Files.isDirectory(_))
        .filter(extensionMatcher)
        .to(ArraySeq)
    }
  }

  private def fileExtensionMatcherCaseInsensitive(extension: String): Path => Boolean = {
    val dotExt = s".$extension".toLowerCase

    path => path.getFileName.toString.toLowerCase.endsWith(dotExt)
  }

  /**
   * Like [[listFiles]], but for a classpath directory.
   *
   * The files might come from several classpath elements (e.g. different JAR files), see
   * [[ClassPathFile.classPathElementRef]].
   *
   * Returns an empty list for a missing directory, as it can't be told apart from an
   * empty one.
   */
  def listClassPathDirFiles(
    classLoader: ClassLoader,
    classPath: String,
    withExtension: String,
  ): ArraySeq[ClassPathFile] = {
    Using.resource {
      new ClassGraph()
        .addClassLoader(classLoader)
        .acceptPathsNonRecursive(classPath)
        .scan()
    } { scanResult =>
      val resultBuilder = ArraySeq.newBuilder[ClassPathFile]

      scanResult.getResourcesWithExtension(withExtension)
        .forEachByteBufferThrowingIOException { (resource, contentByteBuf) =>
          resultBuilder += ClassPathFile(
            // getClasspathElementFile can return null if the classpath element
            // doesn't have a location, e.g. loaded from memory
            classPathElementRef =
              Option(resource.getClasspathElementFile).map(_.toString).getOrElse("UNKNOWN"),
            pathUnderClassPathElement = resource.getPathRelativeToClasspathElement,
            fileName = getFileName(resource),
            contentBytes = copyToArray(contentByteBuf),
          )
        }

      resultBuilder.result()
    }
  }

  private def getFileName(resource: Resource): String = {
    // assuming paths inside classpath should be delimited with /
    resource.getPathRelativeToClasspathElement.split('/').last
  }

  private def copyToArray(byteBuf: ByteBuffer): Array[Byte] = {
    val result = new Array[Byte](byteBuf.remaining())
    byteBuf.get(result)
    result
  }

  /**
   * Classpath resource file found by [[listClassPathDirFiles]].
   *
   * @param classPathElementRef
   *   string representation of the classpath element where the file resides, e.g. JAR
   *   file path
   * @param pathUnderClassPathElement
   *   file path relative to the classpath element root
   * @param fileName
   *   file name
   * @param contentBytes
   *   read file content, byte array
   */
  final case class ClassPathFile(
    classPathElementRef: String,
    pathUnderClassPathElement: String,
    fileName: String,
    contentBytes: Array[Byte],
  )
}
