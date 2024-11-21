/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.config;

public class SimosaikConfig {
  public final SimosaikConfig.Simosaik simosaik;

  public static class Simosaik {
    public final java.lang.String mappingPath;
    public final Simosaik.Receive receive;
    public final boolean sendResults;

    public static class Receive {
      public final boolean em;
      public final boolean primary;

      public Receive(
          com.typesafe.config.Config c,
          java.lang.String parentPath,
          $TsCfgValidator $tsCfgValidator) {
        this.em = c.hasPathOrNull("em") && c.getBoolean("em");
        this.primary = !c.hasPathOrNull("primary") || c.getBoolean("primary");
      }
    }

    public Simosaik(
        com.typesafe.config.Config c,
        java.lang.String parentPath,
        $TsCfgValidator $tsCfgValidator) {
      this.mappingPath = $_reqStr(parentPath, c, "mappingPath", $tsCfgValidator);
      this.receive =
          c.hasPathOrNull("receive")
              ? new Simosaik.Receive(
                  c.getConfig("receive"), parentPath + "receive.", $tsCfgValidator)
              : new Simosaik.Receive(
                  com.typesafe.config.ConfigFactory.parseString("receive{}"),
                  parentPath + "receive.",
                  $tsCfgValidator);
      this.sendResults = !c.hasPathOrNull("sendResults") || c.getBoolean("sendResults");
    }

    private static java.lang.String $_reqStr(
        java.lang.String parentPath,
        com.typesafe.config.Config c,
        java.lang.String path,
        $TsCfgValidator $tsCfgValidator) {
      if (c == null) return null;
      try {
        return c.getString(path);
      } catch (com.typesafe.config.ConfigException e) {
        $tsCfgValidator.addBadPath(parentPath + path, e);
        return null;
      }
    }
  }

  public SimosaikConfig(com.typesafe.config.Config c) {
    final $TsCfgValidator $tsCfgValidator = new $TsCfgValidator();
    final java.lang.String parentPath = "";
    this.simosaik =
        c.hasPathOrNull("simosaik")
            ? new SimosaikConfig.Simosaik(
                c.getConfig("simosaik"), parentPath + "simosaik.", $tsCfgValidator)
            : new SimosaikConfig.Simosaik(
                com.typesafe.config.ConfigFactory.parseString("simosaik{}"),
                parentPath + "simosaik.",
                $tsCfgValidator);
    $tsCfgValidator.validate();
  }

  private static final class $TsCfgValidator {
    private final java.util.List<java.lang.String> badPaths = new java.util.ArrayList<>();

    void addBadPath(java.lang.String path, com.typesafe.config.ConfigException e) {
      badPaths.add("'" + path + "': " + e.getClass().getName() + "(" + e.getMessage() + ")");
    }

    void validate() {
      if (!badPaths.isEmpty()) {
        java.lang.StringBuilder sb = new java.lang.StringBuilder("Invalid configuration:");
        for (java.lang.String path : badPaths) {
          sb.append("\n    ").append(path);
        }
        throw new com.typesafe.config.ConfigException(sb.toString()) {};
      }
    }
  }
}
