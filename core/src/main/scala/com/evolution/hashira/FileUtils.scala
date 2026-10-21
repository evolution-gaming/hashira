package com.evolution.hashira

import io.github.classgraph.{ClassGraph, Resource}

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
    classPath: String,
    withExtension: String,
  ): ArraySeq[ClassPathFile] = {
    Using.resource {
      new ClassGraph()
        .enableClasspath()
        .enableNonSystemModules()
        .acceptPathsNonRecursive(classPath)
        .scan()
    } { scanResult =>
      val resultBuilder = ArraySeq.newBuilder[ClassPathFile]

      scanResult.getResourcesWithExtension(withExtension)
        .forEachByteArray { (resource, contentBytes) =>
          val path = resource.getPath
          resultBuilder += ClassPathFile(
            classPathElementRef = getClassPathElementRef(resource),
            pathUnderClassPathElement = path,
            // assuming paths inside classpath should be delimited with /
            fileName = path.substring(path.lastIndexOf('/') + 1),
            contentBytes = contentBytes,
          )
        }

      resultBuilder.result()
    }
  }

  private def getClassPathElementRef(resource: Resource): String = {
    // unlike getClasspathElementFile, tells apart the elements inside the same JAR file,
    // e.g. BOOT-INF/classes and BOOT-INF/lib/dep.jar in a Spring Boot one
    try {
      resource.getClasspathElementURI.toString
    } catch {
      // thrown by getClasspathElementURI if the classpath element doesn't have a location,
      // e.g. a module defined in memory
      case _: IllegalStateException =>
        Option(resource.getModuleReference)
          .fold("UNKNOWN")(module => s"module:${ module.descriptor.name }")
    }
  }

  /**
   * Classpath resource file found by [[listClassPathDirFiles]].
   *
   * @param classPathElementRef
   *   string representation of the classpath element where the file resides, e.g. JAR
   *   file URI
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
