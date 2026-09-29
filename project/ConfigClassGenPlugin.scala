import sbt.*
import sbt.Keys.*
import sbt.nio.Keys.*
import sbt.plugins.JvmPlugin

// AI generated - here be dragons!
/**
 * Build-local code generator for simple config classes, similar in spirit to
 * sbt-contraband.
 *
 * The main goal is keeping binary compatibility when new fields are added.
 *
 * Every `*.conf` HOCON file in
 * [[ConfigClassGenPlugin.autoImport.configClassGenSourceDirectory]]
 * (`src/main/config-classes` by default) describes a single class named after the file,
 * e.g. `MyParams.conf`:
 * {{{
 * doc = "scaladoc text"
 * imports = ["scala.concurrent.duration.*"]
 * fields {
 *   timeout {
 *     type = FiniteDuration
 *     default = 5.seconds
 *     doc = """
 *       scaladoc text,
 *       which can span multiple lines
 *     """
 *   }
 *   name {
 *     type = String
 *     default = """"a b""""
 *     doc = "scaladoc text"
 *   }
 * }
 * }}}
 *
 * All the classes are generated in the
 * [[ConfigClassGenPlugin.autoImport.configClassGenPackage]] package, which must be set in
 * the project settings.
 *
 * Only a flat structure is supported: `type` is a Scala type and `default` is a Scala
 * expression, so the compiler checks them. Every field must have a default value. Values
 * with HOCON special characters, like `"`, `[` or `,`, must be quoted.
 *
 * The class and every field must have a non-empty `doc`. Multi-line `doc` strings have
 * their common indentation stripped.
 *
 * HOCON objects don't keep the key order, so the fields are ordered by their line numbers
 * and each field must start on a separate line. Note that HOCON merges duplicate keys
 * instead of reporting them.
 *
 * The generated class has a private constructor and private `copy`. It has no `unapply`
 * and doesn't extend `Product`. The public API is:
 *   - a `val` for each field
 *   - a `withXxx` method for each field
 *   - `equals`, `hashCode`, and `toString` with field names
 *   - `renderMultiline`, rendering the field values on separate lines
 *   - a companion object with `Default`, `apply()` returning `Default`, and
 *     `readFromConfig` reading an instance from a Typesafe Config object
 *
 * `readFromConfig` accepts the keys either as the field names or in kebab-case,
 * preferring the field names if both are set, uses default values for missing keys and
 * ignores other keys. It supports the field types `String`, `Boolean`, `Int`, `Long`,
 * `Double`, `java.time.Duration` and `FiniteDuration`, other types are rejected. The
 * project must depend on `com.typesafe:config`.
 *
 * Bincompat rules:
 *   - adding a field at any position is backward binary compatible
 *   - changing a default value or doc is binary compatible, but it's a behavior change
 *   - removing, renaming or changing the type of a field breaks binary compatibility
 */
object ConfigClassGenPlugin extends AutoPlugin {
  override def requires: Plugins = JvmPlugin
  override def trigger: PluginTrigger = noTrigger

  object autoImport {
    val configClassGenPackage: SettingKey[String] =
      settingKey[String]("Package for the generated config classes, has no default value")
    val configClassGenSourceDirectory: SettingKey[File] =
      settingKey[File]("Directory with config class HOCON schema files")
    val configClassGenerate: TaskKey[Seq[File]] =
      taskKey[Seq[File]]("Generates Scala sources for config class HOCON schema files")
  }

  import autoImport.*

  override def projectSettings: Seq[Setting[_]] = inConfig(Compile)(
    Seq(
      configClassGenSourceDirectory := sourceDirectory.value / "config-classes",
      configClassGenerate / fileInputs += configClassGenSourceDirectory.value.toGlob / "*.conf",
      configClassGenerate := {
        val inputs = configClassGenerate.inputFiles.map(_.toFile).sorted
        val pkg = configClassGenPackage.value
        val outDir = sourceManaged.value / "config-classes"
        val base = baseDirectory.value
        val log = streams.value.log

        val outputs = inputs.map { file =>
          val spec = ConfigClassGen.parse(file, pkg)
          val sourceName = IO.relativize(base, file).getOrElse(file.getName)
          val content = ConfigClassGen.render(spec, sourceName)
          val outFile = outDir / pkg.replace('.', '/') / s"${ spec.name }.scala"
          // writing only on changes to avoid needless recompilation
          if (!outFile.exists() || IO.read(outFile) != content) {
            log.info(s"generating config class ${ spec.fullName } from $sourceName")
            IO.write(outFile, content)
          }
          outFile
        }

        val stale = (outDir ** "*.scala").get().toSet -- outputs
        IO.delete(stale)

        outputs
      },
      sourceGenerators += configClassGenerate.taskValue,
    ),
  )
}
