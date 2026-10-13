import com.typesafe.config.{
  Config,
  ConfigException,
  ConfigFactory,
  ConfigObject,
  ConfigParseOptions,
  ConfigSyntax,
}
import sbt.internal.util.MessageOnlyException

import java.io.File
import scala.collection.JavaConverters.*

// AI generated - here be dragons!

/**
 * Parsing, validation and Scala code rendering for [[ConfigClassGenPlugin]] config class
 * HOCON schemas.
 *
 * See [[ConfigClassGenPlugin]] for the schema format and the bincompat rules.
 */
object ConfigClassGen {

  final case class ClassSpec(
    pkg: String,
    name: String,
    doc: String,
    imports: Seq[String],
    fields: Seq[FieldSpec],
  ) {
    def fullName: String = s"$pkg.$name"
  }

  final case class FieldSpec(
    name: String,
    tpe: String,
    default: String,
    doc: String,
  ) {
    def witherName: String = s"with${ name.capitalize }"

    /**
     * Kebab-case version of the field name for config keys, e.g. `http-url-value` for
     * `httpURLValue`.
     */
    def kebabName: String =
      name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").replaceAll("([A-Z])([A-Z][a-z])", "$1-$2").toLowerCase
  }

  private val ClassKeys: Set[String] = Set("doc", "imports", "fields")
  private val FieldKeys: Set[String] = Set("type", "default", "doc")

  /**
   * Field types supported by the generated `readFromConfig` methods, with functions
   * rendering Scala expressions which read a value of the type at a config path literal.
   */
  private val ConfigGetters: Map[String, String => String] = {
    def method(name: String): String => String = path => s"config.$name($path)"
    val finiteDuration: String => String =
      path => s"scala.jdk.javaapi.DurationConverters.toScala(config.getDuration($path))"
    Map(
      "String" -> method("getString"),
      "java.lang.String" -> method("getString"),
      "Boolean" -> method("getBoolean"),
      "Int" -> method("getInt"),
      "Long" -> method("getLong"),
      "Double" -> method("getDouble"),
      "java.time.Duration" -> method("getDuration"),
      "FiniteDuration" -> finiteDuration,
      "scala.concurrent.duration.FiniteDuration" -> finiteDuration,
    )
  }

  private val ClassNameRegex = "[A-Z][A-Za-z0-9]*".r
  private val FieldNameRegex = "[a-z][A-Za-z0-9]*".r

  /**
   * Parses and validates a config class HOCON schema file.
   *
   * The class name is the file name without the `.conf` extension.
   *
   * @param pkg
   *   package of the generated class
   * @throws MessageOnlyException
   *   on parsing or validation errors
   */
  def parse(file: File, pkg: String): ClassSpec = {
    def fail(msg: String): Nothing =
      throw new MessageOnlyException(s"invalid config class schema $file: $msg")

    val name = file.getName.stripSuffix(".conf")
    if (!ClassNameRegex.pattern.matcher(name).matches()) {
      fail(s"invalid class name, the file name without .conf must match ${ ClassNameRegex.regex }: $name")
    }

    try {
      val root = ConfigFactory.parseFile(
        file,
        ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF).setAllowMissing(false),
      )
      checkKeys(root.root(), "root", ClassKeys, fail)

      val doc = requiredDoc(root, "root", fail)

      val imports = if (root.hasPath("imports")) {
        root.getStringList("imports").asScala.toList.map { imp =>
          if (imp.trim.isEmpty || imp.contains('\n')) {
            fail(s"imports must contain non-empty single-line strings: $imp")
          }
          imp.trim
        }
      } else {
        Nil
      }

      if (!root.hasPath("fields")) {
        fail("fields is missing")
      }
      val fieldsObj = root.getObject("fields")
      if (fieldsObj.isEmpty) {
        fail("fields must not be empty")
      }

      // HOCON objects don't keep the key order, restoring it from the source line numbers
      def lineOf(fieldName: String): Int = fieldsObj.get(fieldName).origin().lineNumber()
      val fieldNames = fieldsObj.keySet().asScala.toList.sortBy(lineOf)
      fieldNames.groupBy(lineOf).values.find(_.size > 1).foreach { names =>
        fail(s"fields must start on separate lines: ${ names.sorted.mkString(", ") }")
      }

      val fields = fieldNames.map { fieldName =>
        val path = s"fields.$fieldName"
        if (!FieldNameRegex.pattern.matcher(fieldName).matches()) {
          fail(s"invalid field name, must match ${ FieldNameRegex.regex }: $fieldName")
        }
        val fieldObj = fieldsObj.toConfig.getObject(fieldName)
        checkKeys(fieldObj, path, FieldKeys, fail)
        val fieldConfig = fieldObj.toConfig
        val tpe = requiredString(fieldConfig, "type", path, fail)
        if (!ConfigGetters.contains(tpe)) {
          fail(
            s"$path: type $tpe is not supported by readFromConfig, " +
              s"supported types: ${ ConfigGetters.keys.toList.sorted.mkString(", ") }",
          )
        }
        FieldSpec(
          name = fieldName,
          tpe = tpe,
          default = requiredString(fieldConfig, "default", path, fail),
          doc = requiredDoc(fieldConfig, path, fail),
        )
      }

      val fieldNameSet = fieldNames.toSet
      fields.map(_.witherName).filter(fieldNameSet.contains).foreach { clash =>
        fail(s"field name clashes with a generated wither method: $clash")
      }
      fields.groupBy(_.kebabName).values.find(_.size > 1).foreach { clashing =>
        fail(s"fields have the same kebab-case config key: ${ clashing.map(_.name).mkString(", ") }")
      }

      ClassSpec(
        pkg = pkg,
        name = name,
        doc = doc,
        imports = imports,
        fields = fields,
      )
    } catch {
      // the messages contain the file name and the line number
      case e: ConfigException =>
        throw new MessageOnlyException(s"invalid config class schema: ${ e.getMessage }")
    }
  }

  private def checkKeys(
    obj: ConfigObject,
    path: String,
    allowedKeys: Set[String],
    fail: String => Nothing,
  ): Unit = {
    obj.keySet().asScala.toList.sorted.filterNot(allowedKeys).foreach { unknown =>
      fail(s"$path: unknown key: $unknown, allowed keys: ${ allowedKeys.toList.sorted.mkString(", ") }")
    }
  }

  /**
   * Reads a string value, unquoted HOCON numbers and booleans are read as their source
   * text.
   */
  private def requiredString(
    config: Config,
    key: String,
    path: String,
    fail: String => Nothing,
  ): String = {
    if (!config.hasPath(key)) {
      fail(s"$path: $key is missing")
    }
    val value = config.getString(key).trim
    if (value.isEmpty || value.contains('\n')) {
      fail(s"$path: $key must be a non-empty single-line string")
    }
    value
  }

  private def requiredDoc(config: Config, path: String, fail: String => Nothing): String = {
    if (!config.hasPath("doc")) {
      fail(s"$path: doc is missing")
    }
    val doc = stripIndent(config.getString("doc"))
    if (doc.isEmpty) {
      fail(s"$path: doc must not be empty")
    }
    if (doc.contains("*/")) {
      fail(s"$path: doc must not contain */")
    }
    doc
  }

  /**
   * Strips the common indentation of a multi-line string, along with the trailing
   * whitespace and the leading and trailing blank lines.
   *
   * The first line doesn't count for the common indentation, as it follows the opening
   * quotes.
   */
  private def stripIndent(text: String): String = {
    val lines = text.split('\n').toList.map(_.replaceAll("\\s+$", ""))
    val indent = lines.drop(1).filter(_.nonEmpty).map(_.takeWhile(_ == ' ').length).reduceOption(_ min _)
    val stripped = lines.take(1).map(_.trim) ++ lines.drop(1).map(_.drop(indent.getOrElse(0)))
    stripped.dropWhile(_.isEmpty).reverse.dropWhile(_.isEmpty).reverse.mkString("\n")
  }

  /**
   * Renders Scala source code for a config class.
   *
   * @param sourceFileName
   *   schema file name to mention in the generated file header
   */
  def render(spec: ClassSpec, sourceFileName: String): String = {
    val name = spec.name
    val fields = spec.fields
    val needsQuote = fields.exists(f => isStringType(f.tpe))

    val out = new StringBuilder
    def line(s: String = ""): Unit = {
      out ++= s
      out += '\n'
    }
    def docLines(textIndent: String, text: String): Unit = {
      text.split('\n').foreach(l => line(s" * $textIndent$l".replaceAll("\\s+$", "")))
    }

    line(s"// Generated by ConfigClassGenPlugin from $sourceFileName - DO NOT EDIT")
    line(s"package ${ spec.pkg }")
    if (spec.imports.nonEmpty) {
      line()
      spec.imports.foreach(i => line(s"import $i"))
    }
    line()

    // class scaladoc
    line("/**")
    docLines("", spec.doc)
    line(" *")
    line(s" * Instances are created from [[$name.Default]] and customized with `withXxx` methods,")
    line(s" * or read from a Typesafe Config object with [[$name.readFromConfig]].")
    fields.foreach { f =>
      line(" *")
      line(s" * @param ${ f.name }")
      docLines("  ", f.doc)
      line(s" *   Default: `${ f.default }`")
    }
    line(" */")

    // class
    line(s"final class $name private (")
    fields.foreach(f => line(s"  val ${ f.name }: ${ f.tpe },"))
    line(") {")

    fields.foreach { f =>
      line()
      line("  /**")
      line(s"   * Returns a copy with [[${ f.name }]] set to the given value.")
      line("   */")
      line(s"  def ${ f.witherName }(${ f.name }: ${ f.tpe }): $name = {")
      line(s"    copy(${ f.name } = ${ f.name })")
      line("  }")
    }

    line()
    line("  private def copy(")
    fields.foreach(f => line(s"    ${ f.name }: ${ f.tpe } = this.${ f.name },"))
    line(s"  ): $name = {")
    line(s"    new $name(")
    fields.foreach(f => line(s"      ${ f.name } = ${ f.name },"))
    line("    )")
    line("  }")

    line()
    line("  override def equals(other: Any): Boolean = other match {")
    line(s"    case that: $name =>")
    line("      (this eq that) || (")
    fields.zipWithIndex.foreach { case (f, idx) =>
      val sep = if (idx < fields.size - 1) " &&" else ""
      line(s"        this.${ f.name } == that.${ f.name }$sep")
    }
    line("      )")
    line("    case _ => false")
    line("  }")

    line()
    line("  override def hashCode(): Int = {")
    line(s"""    var hash = "$name".##""")
    fields.foreach(f => line(s"    hash = 37 * hash + this.${ f.name }.##"))
    line("    hash")
    line("  }")

    // strings are quoted and escaped to keep the values single-line
    def renderedValue(f: FieldSpec): String = {
      if (isStringType(f.tpe)) s"$name.quote(this.${ f.name })" else s"this.${ f.name }"
    }

    line()
    line("  override def toString: String = {")
    line(s"""    "$name(" +""")
    fields.zipWithIndex.foreach { case (f, idx) =>
      val sep = if (idx < fields.size - 1) " + \", \" +" else " +"
      line(s"""      "${ f.name } = " + ${ renderedValue(f) }$sep""")
    }
    line("      \")\"")
    line("  }")

    line()
    line("  /**")
    line("   * Renders the field values on separate lines, e.g. for logging:")
    line("   * {{{")
    line(s"   * $name:")
    fields.take(2).foreach(f => line(s"   * - ${ f.name } = ..."))
    if (fields.size > 2) line("   * ...")
    line("   * }}}")
    line("   */")
    line("  def renderMultiline: String = {")
    line(s"""    "$name:\\n" +""")
    fields.zipWithIndex.foreach { case (f, idx) =>
      val sep = if (idx < fields.size - 1) " + \"\\n\" +" else ""
      line(s"""      "- ${ f.name } = " + ${ renderedValue(f) }$sep""")
    }
    line("  }")
    line("}")

    // companion
    line()
    line(s"object $name {")
    line()
    line("  /**")
    line("   * Instance with all the fields set to their default values.")
    line("   */")
    line(s"  val Default: $name = new $name(")
    fields.foreach(f => line(s"    ${ f.name } = ${ f.default },"))
    line("  )")
    line()
    line("  /**")
    line("   * Returns [[Default]].")
    line("   */")
    line(s"  def apply(): $name = Default")
    line()
    line("  /**")
    line("   * Reads an instance from a Typesafe Config object, the missing fields get their default")
    line("   * values.")
    line("   *")
    line("   * The keys are either the field names as is or in kebab-case, e.g. `fieldName` or")
    line("   * `field-name`. If both are set, the field name one is used. Other keys are ignored.")
    line("   *")
    line("   * @throws com.typesafe.config.ConfigException")
    line("   *   if the config is invalid")
    line("   */")
    line(s"  def readFromConfig(config: com.typesafe.config.Config): $name = {")
    line(s"    new $name(")
    fields.foreach { f =>
      val read = ConfigGetters(f.tpe)
      line(s"      ${ f.name } =")
      Seq(f.name, f.kebabName).distinct.zipWithIndex.foreach { case (key, idx) =>
        val keyLiteral = "\"" + key + "\""
        val keyword = if (idx == 0) "if" else "else if"
        line(s"        $keyword (config.hasPath($keyLiteral)) ${ read(keyLiteral) }")
      }
      line(s"        else Default.${ f.name },")
    }
    line("    )")
    line("  }")
    if (needsQuote) {
      line()
      line(QuoteMethod)
    }
    line("}")

    out.toString
  }

  private def isStringType(tpe: String): Boolean = {
    tpe == "String" || tpe == "java.lang.String" || tpe == "scala.Predef.String"
  }

  /**
   * Quotes and escapes a string for a readable but reversible single-line `toString`
   * output.
   */
  private val QuoteMethod: String =
    """  private def quote(str: String): String = {
      |    val builder = new java.lang.StringBuilder(str.length + 2)
      |    builder.append('"')
      |    str.foreach {
      |      case '"' => builder.append("\\\"")
      |      case '\\' => builder.append("\\\\")
      |      case '\n' => builder.append("\\n")
      |      case '\r' => builder.append("\\r")
      |      case '\t' => builder.append("\\t")
      |      case c if c < ' ' => builder.append(f"\\u${ c.toInt }%04x")
      |      case c => builder.append(c)
      |    }
      |    builder.append('"').toString
      |  }""".stripMargin
}
