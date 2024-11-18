/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.config

final case class Config(
    simoopsim: scala.Option[Config.Simoopsim],
    simosaik: scala.Option[Config.Simosaik]
)
object Config {
  sealed abstract class CsvParams(
      val csvSep: java.lang.String,
      val directoryPath: java.lang.String,
      val isHierarchic: scala.Boolean
  )

  final case class Simoopsim(
      run: scala.Boolean
  )
  object Simoopsim {
    def apply(
        c: com.typesafe.config.Config,
        parentPath: java.lang.String,
        $tsCfgValidator: $TsCfgValidator
    ): Config.Simoopsim = {
      Config.Simoopsim(
        run = $_reqBln(parentPath, c, "run", $tsCfgValidator)
      )
    }
    private def $_reqBln(
        parentPath: java.lang.String,
        c: com.typesafe.config.Config,
        path: java.lang.String,
        $tsCfgValidator: $TsCfgValidator
    ): scala.Boolean = {
      if (c == null) false
      else
        try c.getBoolean(path)
        catch {
          case e: com.typesafe.config.ConfigException =>
            $tsCfgValidator.addBadPath(parentPath + path, e)
            false
        }
    }

  }

  final case class Simosaik(
      input: Config.Simosaik.Input,
      run: scala.Boolean
  )
  object Simosaik {
    final case class Input(
        mapping: Config.Simosaik.Input.Mapping
    )
    object Input {
      final case class Mapping(
      )
      object Mapping {
        def apply(
            c: com.typesafe.config.Config,
            parentPath: java.lang.String,
            $tsCfgValidator: $TsCfgValidator
        ): Config.Simosaik.Input.Mapping = {
          Config.Simosaik.Input.Mapping(
          )
        }
      }

      def apply(
          c: com.typesafe.config.Config,
          parentPath: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): Config.Simosaik.Input = {
        Config.Simosaik.Input(
          mapping = Config.Simosaik.Input.Mapping(
            if (c.hasPathOrNull("mapping")) c.getConfig("mapping")
            else com.typesafe.config.ConfigFactory.parseString("mapping{}"),
            parentPath + "mapping.",
            $tsCfgValidator
          )
        )
      }
    }

    def apply(
        c: com.typesafe.config.Config,
        parentPath: java.lang.String,
        $tsCfgValidator: $TsCfgValidator
    ): Config.Simosaik = {
      Config.Simosaik(
        input = Config.Simosaik.Input(
          if (c.hasPathOrNull("input")) c.getConfig("input")
          else com.typesafe.config.ConfigFactory.parseString("input{}"),
          parentPath + "input.",
          $tsCfgValidator
        ),
        run = $_reqBln(parentPath, c, "run", $tsCfgValidator)
      )
    }
    private def $_reqBln(
        parentPath: java.lang.String,
        c: com.typesafe.config.Config,
        path: java.lang.String,
        $tsCfgValidator: $TsCfgValidator
    ): scala.Boolean = {
      if (c == null) false
      else
        try c.getBoolean(path)
        catch {
          case e: com.typesafe.config.ConfigException =>
            $tsCfgValidator.addBadPath(parentPath + path, e)
            false
        }
    }

  }

  def apply(c: com.typesafe.config.Config): Config = {
    val $tsCfgValidator: $TsCfgValidator = new $TsCfgValidator()
    val parentPath: java.lang.String = ""
    val $result = Config(
      simoopsim =
        if (c.hasPathOrNull("simoopsim"))
          scala.Some(
            Config.Simoopsim(
              c.getConfig("simoopsim"),
              parentPath + "simoopsim.",
              $tsCfgValidator
            )
          )
        else None,
      simosaik =
        if (c.hasPathOrNull("simosaik"))
          scala.Some(
            Config.Simosaik(
              c.getConfig("simosaik"),
              parentPath + "simosaik.",
              $tsCfgValidator
            )
          )
        else None
    )
    $tsCfgValidator.validate()
    $result
  }
  final class $TsCfgValidator {
    private val badPaths =
      scala.collection.mutable.ArrayBuffer[java.lang.String]()

    def addBadPath(
        path: java.lang.String,
        e: com.typesafe.config.ConfigException
    ): Unit = {
      badPaths += s"'$path': ${e.getClass.getName}(${e.getMessage})"
    }

    def addInvalidEnumValue(
        path: java.lang.String,
        value: java.lang.String,
        enumName: java.lang.String
    ): Unit = {
      badPaths += s"'$path': invalid value $value for enumeration $enumName"
    }

    def validate(): Unit = {
      if (badPaths.nonEmpty) {
        throw new com.typesafe.config.ConfigException(
          badPaths.mkString("Invalid configuration:\n    ", "\n    ", "")
        ) {}
      }
    }
  }
}
