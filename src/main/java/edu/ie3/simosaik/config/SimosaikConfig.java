/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import edu.ie3.datamodel.utils.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public record SimosaikConfig(
    Path mappingPath,
    Simulation simulation,
    int stepSize,
    boolean useFlexOptionEntitiesInsteadOfEmAgents) {

  public static SimosaikConfig load(Path filePath) {
    if (!Files.isReadable(filePath)) {
      throw new IllegalArgumentException("Config file at " + filePath + " is not readable.");
    }

    Config baseConfig = ConfigFactory.parseFile(filePath.toFile());
    int stepSize = getStepSize(baseConfig);

    Config config = baseConfig.getConfig("simosaik");

    String simulation;
    try {
      simulation = Optional.of(config.getString("simulation")).orElse("PrimaryResult");
    } catch (Exception e) {
      simulation = "PrimaryResult";
    }

    boolean useFlexOptionEntitiesInsteadOfEmAgents = false;
    // config.getBoolean("useFlexOptionEntitiesInsteadOfEmAgents");

    return new SimosaikConfig(
        Path.of(config.getString("mappingPath")),
        Simulation.parse(simulation),
        stepSize,
        useFlexOptionEntitiesInsteadOfEmAgents);
  }

  private static int getStepSize(Config baseConfig) {
    Try.TrySupplier<Duration, ConfigException> supplier =
        () -> baseConfig.getConfig("simona").getConfig("powerflow").getDuration("resolution");
    return Try.of(supplier, ConfigException.class)
        .map(duration -> (int) duration.toSeconds())
        .getOrThrow();
  }
}
