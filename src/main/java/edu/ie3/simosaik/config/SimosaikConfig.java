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
import java.util.Optional;

public record SimosaikConfig(Path mappingPath, Optional<String> simulator) {

  public static SimosaikConfig load(Path filePath) {
    if (!Files.isReadable(filePath)) {
      throw new IllegalArgumentException("Config file at " + filePath + " is not readable.");
    }

    Config config = ConfigFactory.parseFile(filePath.toFile()).getConfig("simosaik");

    Optional<String> simulator;
    try {
      simulator = Optional.ofNullable(config.getString("simulator"));
    } catch (Exception e) {
      simulator = Optional.empty();
    }

    return new SimosaikConfig(Path.of(config.getString("mappingPath")), simulator);
  }
}
