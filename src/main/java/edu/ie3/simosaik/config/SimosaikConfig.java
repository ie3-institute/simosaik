/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.nio.file.Files;
import java.nio.file.Path;

public class SimosaikConfig {
  public final Path mappingPath;
  public final String simulator;

  public SimosaikConfig(Path mappingPath, String simulator) {
    this.mappingPath = mappingPath;
    this.simulator = simulator;
  }

  public static SimosaikConfig load(Path filePath) {
    if (!Files.isReadable(filePath)) {
      throw new IllegalArgumentException("Config file at " + filePath + " is not readable.");
    }

    Config config = ConfigFactory.parseFile(filePath.toFile()).getConfig("simosaik");

    return new SimosaikConfig(
        Path.of(config.getString("mappingPath")), config.getString("simulator"));
  }
}
