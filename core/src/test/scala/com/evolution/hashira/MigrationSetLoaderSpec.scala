package com.evolution.hashira

import java.io.ByteArrayOutputStream
import java.net.{URI, URL, URLClassLoader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, NoSuchFileException, Path, Paths}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.util.Using

class MigrationSetLoaderSpec extends BaseUnitSpec {
  import MigrationSetLoaderSpec.*
  import TestData.*

  "MigrationSetLoader" - {

    "constant" - {
      val set = MigrationSet(Vector(mkMigration(authoredAt = 1L, description = "a")))

      "returns the given set for the default set name" in {
        implicit val log: TestMigrationLog = new TestMigrationLog

        MigrationSetLoader.constant(set).load(name = None) should be theSameInstanceAs set
      }

      "fails on a named set" in {
        implicit val log: TestMigrationLog = new TestMigrationLog

        val e = interceptOnly[RuntimeException] {
          MigrationSetLoader.constant(set).load(name = Some("other"))
        }
        e.getMessage should include("doesn't support loading multiple named sets")
      }
    }

    "fromFileDirectory" - {
      def load(
        dir: Path,
        name: Option[String] = None,
        params: MigratorParams = MigratorParams.Default,
      ): MigrationSet = {
        implicit val log: TestMigrationLog = new TestMigrationLog
        MigrationSetLoader.fromFileDirectory(path = dir, params = params).load(name = name)
      }

      "loads the migration files in the file name order" in withTmpDir { dir =>
        // sharing authoredAt, so the load order is the execution order
        val b = writeMigrationFile(dir, "02_second.cql", mkMigration(authoredAt = 1L, description = "b"))
        val a = writeMigrationFile(dir, "01_first.cql", mkMigration(authoredAt = 1L, description = "a"))
        val c = writeMigrationFile(dir, "03_third.cql", mkMigration(authoredAt = 1L, description = "c"))

        load(dir) shouldEqual MigrationSet(Vector(a, b, c))
      }

      "loads only the files with the extension, not looking into subdirectories" in withTmpDir { dir =>
        val migration = writeMigrationFile(dir, "a.cql", mkMigration(authoredAt = 1L, description = "a"))
        writeNonMigrationFiles(dir)

        load(dir) shouldEqual MigrationSet(Vector(migration))
      }

      "uses the migration file extension from the params" in withTmpDir { dir =>
        writeMigrationFile(dir, "a.cql", mkMigration(authoredAt = 1L, description = "a"))
        val txt = writeMigrationFile(dir, "b.txt", mkMigration(authoredAt = 2L, description = "b"))

        load(dir, params = MigratorParams.Default.withMigrationFileExtension("txt")) shouldEqual
          MigrationSet(Vector(txt))
      }

      "loads a named set from the subdirectory with that name" in withTmpDir { dir =>
        writeMigrationFile(dir, "a.cql", mkMigration(authoredAt = 1L, description = "a"))
        val named = writeMigrationFile(
          dir.resolve("named"),
          "b.cql",
          mkMigration(authoredAt = 2L, description = "b"),
        )

        load(dir, name = Some("named")) shouldEqual MigrationSet(Vector(named))
      }

      "returns an empty set for a directory without migration files" in withTmpDir { dir =>
        writeNonMigrationFiles(dir)

        load(dir) shouldEqual MigrationSet.empty
      }

      "fails on a missing directory" in withTmpDir { dir =>
        interceptOnly[NoSuchFileException] {
          load(dir.resolve("missing"))
        }
      }

      "fails on an invalid migration file, referring to it by its path" in withTmpDir { dir =>
        writeMigrationFile(dir, "a.cql", mkMigration(authoredAt = 1L, description = "a"))
        val invalidFile = writeFile(dir, "b.cql", "CREATE TABLE no_directives (id uuid PRIMARY KEY)")

        interceptOnly[MigrationFileInvalidException] {
          load(dir)
        }.fileRef shouldEqual invalidFile.toString
      }

      "loads the valid migration file format examples" in {
        load(validFileExamplesDir()) shouldEqual validFileExamplesSet
      }
    }

    "fromClasspath" - {
      def load(
        classPathDir: String,
        name: Option[String] = None,
        params: MigratorParams = MigratorParams.Default,
      ): MigrationSet = {
        implicit val log: TestMigrationLog = new TestMigrationLog
        MigrationSetLoader
          .fromClasspath(classPathDir = classPathDir, params = params)
          .load(name = name)
      }

      "loads the migration files in the file name order" in withClassPathRoot { root =>
        val dir = root.resolve(MigrationsClassPathDir)
        // sharing authoredAt, so the load order is the execution order
        val b = writeMigrationFile(dir, "02_second.cql", mkMigration(authoredAt = 1L, description = "b"))
        val a = writeMigrationFile(dir, "01_first.cql", mkMigration(authoredAt = 1L, description = "a"))
        val c = writeMigrationFile(dir, "03_third.cql", mkMigration(authoredAt = 1L, description = "c"))

        load(MigrationsClassPathDir) shouldEqual MigrationSet(Vector(a, b, c))
      }

      "loads only the files with the extension, not looking into subdirectories" in withClassPathRoot {
        root =>
          val dir = root.resolve(MigrationsClassPathDir)
          val migration = writeMigrationFile(dir, "a.cql", mkMigration(authoredAt = 1L, description = "a"))
          writeNonMigrationFiles(dir)

          load(MigrationsClassPathDir) shouldEqual MigrationSet(Vector(migration))
      }

      "uses the migration file extension from the params" in withClassPathRoot { root =>
        val dir = root.resolve(MigrationsClassPathDir)
        writeMigrationFile(dir, "a.cql", mkMigration(authoredAt = 1L, description = "a"))
        val txt = writeMigrationFile(dir, "b.txt", mkMigration(authoredAt = 2L, description = "b"))

        load(
          MigrationsClassPathDir,
          params = MigratorParams.Default.withMigrationFileExtension("txt"),
        ) shouldEqual MigrationSet(Vector(txt))
      }

      "loads a named set from the subdirectory with that name" in withClassPathRoot { root =>
        val dir = root.resolve(MigrationsClassPathDir)
        writeMigrationFile(dir, "a.cql", mkMigration(authoredAt = 1L, description = "a"))
        val named = writeMigrationFile(
          dir.resolve("named"),
          "b.cql",
          mkMigration(authoredAt = 2L, description = "b"),
        )

        load(MigrationsClassPathDir, name = Some("named")) shouldEqual MigrationSet(Vector(named))
        load(s"$MigrationsClassPathDir/", name = Some("named")) shouldEqual MigrationSet(Vector(named))
      }

      // Migrator fails on an empty set unless MigratorParams.allowEmptyMigrationSet is enabled
      "returns an empty set for a missing classpath directory or set name" in withClassPathRoot { root =>
        writeMigrationFile(
          root.resolve(MigrationsClassPathDir),
          "a.cql",
          mkMigration(authoredAt = 1L, description = "a"),
        )

        load(s"$MigrationsClassPathDir/missing") shouldEqual MigrationSet.empty
        load(MigrationsClassPathDir, name = Some("missing")) shouldEqual MigrationSet.empty
      }

      "logs the classpath element the migration files are discovered in" in withClassPathRoot { root =>
        writeMigrationFile(
          root.resolve(MigrationsClassPathDir),
          "a.cql",
          mkMigration(authoredAt = 1L, description = "a"),
        )

        implicit val log: TestMigrationLog = new TestMigrationLog
        MigrationSetLoader
          .fromClasspath(classPathDir = MigrationsClassPathDir, params = MigratorParams.Default)
          .load(name = None)

        log.infoMessages.toVector shouldEqual Vector(
          s"discovered migration files in classpath: element=${ root.toRealPath().toUri }, path=$MigrationsClassPathDir",
        )
      }

      "fails if the migration files are found in several classpath elements" in withTmpDir { tempDir =>
        val root1 = tempDir.resolve("root1")
        val root2 = tempDir.resolve("root2")
        writeMigrationFile(
          root1.resolve(MigrationsClassPathDir),
          "a.cql",
          mkMigration(authoredAt = 1L, description = "a"),
        )
        writeMigrationFile(
          root2.resolve(MigrationsClassPathDir),
          "b.cql",
          mkMigration(authoredAt = 2L, description = "b"),
        )

        withClassPathRoots(root1, root2) {
          val e = interceptOnly[RuntimeException] {
            load(MigrationsClassPathDir)
          }
          e.getMessage should
            include("found migration files for one classpath directory in several classpath elements")
          e.getMessage should include(root1.toRealPath().toUri.toString)
          e.getMessage should include(root2.toRealPath().toUri.toString)
        }
      }

      "fails if the migration files are found in several elements of a Spring Boot JAR" in withTmpDir { dir =>
        val depJar = jarBytes(
          s"$MigrationsClassPathDir/b.cql" ->
            migrationFileBytes(mkMigration(authoredAt = 2L, description = "b")),
        )
        val appJar = writeJar(
          dir.resolve("app.jar"),
          s"BOOT-INF/classes/$MigrationsClassPathDir/a.cql" ->
            migrationFileBytes(mkMigration(authoredAt = 1L, description = "a")),
          "BOOT-INF/lib/dep.jar" -> depJar,
        )

        withSpringBootClassPath(appJar, depJarNames = "dep.jar") {
          val e = interceptOnly[RuntimeException] {
            load(MigrationsClassPathDir)
          }
          e.getMessage should
            include("found migration files for one classpath directory in several classpath elements")
          e.getMessage should include("app.jar!/BOOT-INF/classes")
          e.getMessage should include("app.jar!/BOOT-INF/lib/dep.jar")
        }
      }

      "fails on an invalid migration file, referring to it by its classpath" in withClassPathRoot { root =>
        val dir = root.resolve(MigrationsClassPathDir)
        writeMigrationFile(dir, "a.cql", mkMigration(authoredAt = 1L, description = "a"))
        writeFile(dir, "b.cql", "CREATE TABLE no_directives (id uuid PRIMARY KEY)")

        interceptOnly[MigrationFileInvalidException] {
          load(MigrationsClassPathDir)
        }.fileRef shouldEqual s"classpath://$MigrationsClassPathDir/b.cql"
      }

      "refers to an invalid migration file in a Spring Boot JAR file by its classpath" in withTmpDir { dir =>
        val appJar = writeJar(
          dir.resolve("app.jar"),
          s"BOOT-INF/classes/$MigrationsClassPathDir/b.cql" ->
            "CREATE TABLE no_directives (id uuid PRIMARY KEY)".getBytes(StandardCharsets.UTF_8),
        )

        withSpringBootClassPath(appJar) {
          interceptOnly[MigrationFileInvalidException] {
            load(MigrationsClassPathDir)
          }.fileRef shouldEqual s"classpath://$MigrationsClassPathDir/b.cql"
        }
      }

      "loads the valid migration file format examples from the test resources" in {
        load(ValidFileExamplesClassPathDir) shouldEqual validFileExamplesSet
      }

      // e.g. threads attached from native code
      "loads the migration files without a thread context class loader" in {
        withContextClassLoader(null) {
          load(ValidFileExamplesClassPathDir) shouldEqual validFileExamplesSet
        }
      }
    }
  }

  private def mkMigration(authoredAt: Long, description: String): Migration.Irreversible = {
    mkIrreversible(
      id = mkId(authoredAt, description),
      up = Vector(s"CREATE TABLE t$authoredAt (id uuid PRIMARY KEY)"),
    )
  }
}

object MigrationSetLoaderSpec {

  /**
   * Classpath directory for the migration files the tests put on the classpath; nested to
   * avoid clashing with the other resources there.
   */
  private val MigrationsClassPathDir: String = "hashira/migration_set_loader_spec/migrations"

  private val ValidFileExamplesClassPathDir: String = "hashira/migration_file_format_examples/valid"

  private lazy val validFileExamplesSet: MigrationSet = MigrationSet(
    MigrationFileFormatExamples.ValidFileExamples
      // the loaders filter by the extension
      .filter(_.name.endsWith(".cql"))
      .map(_.expected),
  )

  private def validFileExamplesDir(): Path = {
    Paths.get(getClass.getResource(s"/$ValidFileExamplesClassPathDir").toURI)
  }

  private def writeMigrationFile(
    dir: Path,
    fileName: String,
    migration: Migration.Irreversible,
  ): Migration = {
    writeFile(dir, fileName, migrationFileContent(migration))
    migration
  }

  private def migrationFileContent(migration: Migration.Irreversible): String = {
    s"""-- description: ${ migration.id.description }
       |-- authoredAt: ${ migration.id.authoredAt.epochMillis }
       |-- up:
       |${ migration.upStatements.mkString }
       |""".stripMargin
  }

  private def migrationFileBytes(migration: Migration.Irreversible): Array[Byte] = {
    migrationFileContent(migration).getBytes(StandardCharsets.UTF_8)
  }

  private def jarBytes(entries: (String, Array[Byte])*): Array[Byte] = {
    val bytesOut = new ByteArrayOutputStream()
    Using.resource(new ZipOutputStream(bytesOut)) { zipOut =>
      entries.foreach {
        case (path, content) =>
          zipOut.putNextEntry(new ZipEntry(path))
          zipOut.write(content)
          zipOut.closeEntry()
      }
    }
    bytesOut.toByteArray
  }

  private def writeJar(path: Path, entries: (String, Array[Byte])*): Path = {
    Files.write(path, jarBytes(entries*))
  }

  /**
   * Files and directories a loader must ignore by the default `cql` extension.
   */
  private def writeNonMigrationFiles(dir: Path): Unit = {
    val invalidContent = "not a migration file"
    writeFile(dir, "b.txt", invalidContent)
    writeFile(dir, "c.cql.bak", invalidContent)
    writeFile(dir, "dcql", invalidContent)
    writeFile(dir.resolve("nested"), "e.cql", invalidContent)
    writeFile(dir.resolve("dir.cql"), "f.txt", invalidContent)
    ()
  }

  private def writeFile(dir: Path, fileName: String, content: String): Path = {
    Files.createDirectories(dir)
    Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8)
  }

  private def withTmpDir[A](body: Path => A): A = {
    val dir = Files.createTempDirectory(classOf[MigrationSetLoaderSpec].getSimpleName)
    dir.toFile.deleteOnExit() // just in case manual deletion fails
    try {
      body(dir)
    } finally {
      org.apache.commons.io.FileUtils.deleteDirectory(dir.toFile)
    }
  }

  /**
   * Puts the URLs on the classpath found in the environment, using a thread context class
   * loader that is a child of the one that loaded the tests, like sbt, Play dev mode or
   * servlet containers do for the application code.
   */
  private def withClassPath[A](urls: Seq[URL])(body: => A): A = {
    Using.resource(new URLClassLoader(urls.toArray, getClass.getClassLoader)) { classLoader =>
      withContextClassLoader(classLoader)(body)
    }
  }

  private def withClassPathRoots[A](roots: Path*)(body: => A): A = {
    withClassPath(roots.map(_.toUri.toURL))(body)
  }

  private def withClassPathRoot[A](body: Path => A): A = {
    withTmpDir(root => withClassPathRoots(root)(body(root)))
  }

  /**
   * Puts a Spring Boot JAR file on the classpath, as its launcher sets it up: the classes
   * directory and the dependency JAR files nested in the JAR file.
   */
  private def withSpringBootClassPath[A](appJar: Path, depJarNames: String*)(body: => A): A = {
    val appJarUri = appJar.toRealPath().toUri
    val urls = ("BOOT-INF/classes" +: depJarNames.map(name => s"BOOT-INF/lib/$name"))
      .map(nestedPath => URI.create(s"jar:$appJarUri!/$nestedPath!/").toURL)
    withClassPath(urls)(body)
  }

  private def withContextClassLoader[A](classLoader: ClassLoader)(body: => A): A = {
    val thread = Thread.currentThread()
    val prevClassLoader = thread.getContextClassLoader
    thread.setContextClassLoader(classLoader)
    try {
      body
    } finally {
      thread.setContextClassLoader(prevClassLoader)
    }
  }
}
