package com.evolution.hashira

import java.nio.file.Path
import scala.collection.immutable.ArraySeq

/**
 * Loads named or unnamed [[MigrationSet]] for [[Migrator]].
 *
 * Existing implementations:
 *   - [[MigrationSetLoader.constant]]
 *   - [[MigrationSetLoader.fromClasspath]]
 *   - [[MigrationSetLoader.fromFileDirectory]]
 *
 * Obtain an instance using those factory methods and call [[load]] as many times as you
 * want.
 */
trait MigrationSetLoader {

  /**
   * Loads a named or unnamed [[MigrationSet]].
   *
   * Implementations might be blocking and might throw exceptions.
   *
   * Migration set name is needed for cases where you have a single [[Migrator]] /
   * [[MigrationSetLoader]] instance working with multiple migration sets in the same
   * application. E.g., different migration sets are used for different keyspaces. In this
   * case the loader implementation can use the set name to resolve a concrete set, for
   * example using the name as a subdirectory name in the classpath.
   *
   * @param name
   *   migration set name, `None` for an unnamed set
   * @param log
   *   [[MigrationLog]] instance to use for logging
   */
  def load(name: Option[String])(implicit log: MigrationLog): MigrationSet
}

object MigrationSetLoader {

  /**
   * [[MigrationSetLoader]] which always returns the same set value.
   *
   * Loading named sets is not supported and results in a failure.
   *
   * @param set
   *   [[MigrationSet]] value to return on load
   */
  def constant(set: MigrationSet): MigrationSetLoader = new BaseSingleSetLoader {
    override protected def loadSingle()(implicit log: MigrationLog): MigrationSet = set
  }

  /**
   * [[MigrationSetLoader]] which loads migration sets from classpath resources.
   *
   * Each set is loaded from a single directory, non-recursively: subdirectories and files
   * without the [[MigratorParams.migrationFileExtension]] extension are skipped.
   *
   * The [[MigrationSet]] load order is the file name order, which means in case of
   * `authoredAt` timestamp ties, the migrations are ordered lexicographically by their
   * file names (`01_aaa.cql` -> `02_aa.cql`).
   *
   * Unnamed sets are loaded directly from `classPathDir`, named sets - from
   * `classPathDir/name`.
   *
   * For one set all the migration files have to reside in the same classpath element
   * (e.g. one JAR file), otherwise the loading process is aborted with a failure. This is
   * a protection against accidentally loading wrong migrations coming from transitive
   * dependencies. If you truly need to load migration files from different JARs into a
   * single set, please implement a custom [[MigrationSetLoader]].
   *
   * A missing classpath directory can't be told apart from an empty one, so a
   * misconfigured `classPathDir` or an unknown set name gives an empty set rather than an
   * error. [[Migrator]] rejects empty sets unless
   * [[MigratorParams.allowEmptyMigrationSet]] is enabled.
   *
   * @param classPathDir
   *   base classpath directory path
   * @param params
   *   see [[MigratorParams]]
   * @param classLoader
   *   class loader used to find classpath resources; defaults to the one that loaded this
   *   library; pass a different one if the migrations are only visible to another class
   *   loader.
   */
  def fromClasspath(
    classPathDir: String,
    params: MigratorParams,
    classLoader: ClassLoader = this.getClass.getClassLoader,
  ): MigrationSetLoader = new MigrationSetLoader {
    override def load(name: Option[String])(implicit log: MigrationLog): MigrationSet = {
      val effectiveClassPathDir = (Vector(classPathDir.stripSuffix("/")) ++ name).mkString("/")
      loadFromClassPathDir(
        classPath = effectiveClassPathDir,
        fileExtension = params.migrationFileExtension,
        classLoader = classLoader,
      )
    }
  }

  /**
   * [[MigrationSetLoader]] which loads migration sets from filesystem directories.
   *
   * Each set is loaded from a single directory, non-recursively: subdirectories and files
   * without the [[MigratorParams.migrationFileExtension]] extension are skipped.
   *
   * The [[MigrationSet]] load order is the file name order, which means in case of
   * `authoredAt` timestamp ties, the migrations are ordered lexicographically by their
   * file names (`01_aaa.cql` -> `02_aa.cql`).
   *
   * Unnamed sets are loaded directly from `path`, named sets - from `path/name`.
   *
   * In case the migration set directory doesn't exist, an exception is thrown.
   *
   * If the directory does exist but has no migration files, an empty set is loaded.
   * [[Migrator]] rejects empty sets unless [[MigratorParams.allowEmptyMigrationSet]] is
   * enabled.
   *
   * @param path
   *   base directory path
   * @param params
   *   see [[MigratorParams]]
   */
  def fromFileDirectory(
    path: Path,
    params: MigratorParams,
  ): MigrationSetLoader = new MigrationSetLoader {
    override def load(name: Option[String])(implicit log: MigrationLog): MigrationSet = {
      val effectivePath = name.fold(path)(name => path.resolve(name))
      loadFromFileDir(path = effectivePath, fileExtension = params.migrationFileExtension)
    }
  }

  private def loadFromClassPathDir(
    classPath: String,
    fileExtension: String,
    classLoader: ClassLoader,
  )(implicit
    log: MigrationLog,
  ): MigrationSet = {
    val classPathFiles = FileUtils.listClassPathDirFiles(
      classLoader = classLoader,
      classPath = classPath,
      withExtension = fileExtension,
    ).sortBy(_.fileName) // defines migration set load order - by file name
    requireSingleClassPathElement(classPathFiles)

    classPathFiles.headOption.foreach { file =>
      log.info(
        s"discovered migration files in classpath: element=${ file.classPathElementRef }, path=$classPath",
      )
    }

    val migrations = classPathFiles.map { file =>
      MigrationFileParser.parse(
        fileRef = s"classpath://${ file.pathUnderClassPathElement }",
        fileContent = new String(file.contentBytes, MigrationFileParser.FileCharset),
      )
    }

    MigrationSet(migrations)
  }

  private def requireSingleClassPathElement(classPathFiles: ArraySeq[FileUtils.ClassPathFile]): Unit = {
    val elementToFiles = classPathFiles.groupBy(_.classPathElementRef)
    if (elementToFiles.size > 1) {
      val examplesStr = elementToFiles.view.map {
        case (classPathElemRef, files) =>
          s"$classPathElemRef (${ files.size } files, e.g. ${ files.head.pathUnderClassPathElement })"
      }.mkString(", ")
      sys.error(s"found migration files for one classpath directory in several classpath elements (i.e. JAR files): $examplesStr")
    }
  }

  private def loadFromFileDir(path: Path, fileExtension: String): MigrationSet = {
    val files = FileUtils.listFiles(dirPath = path, withExtension = fileExtension)
      .sortBy(_.getFileName.toString) // defines migration set load order - by file name
    val migrations = files.map(MigrationFileParser.parse)
    MigrationSet(migrations)
  }

  private sealed abstract class BaseSingleSetLoader extends MigrationSetLoader {
    override final def load(name: Option[String])(implicit log: MigrationLog): MigrationSet = {
      if (name.nonEmpty) {
        sys.error("this migration set loader doesn't support loading multiple named sets")
      }
      loadSingle()
    }

    protected def loadSingle()(implicit log: MigrationLog): MigrationSet
  }
}
