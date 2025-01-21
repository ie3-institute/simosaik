/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.config;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Simple parser for the cli arguments. */
public class ArgsParser {

  /**
   * Parsed arguments.
   *
   * @param mainArgs provided arguments
   * @param mosaikIP the IP of the socket
   * @param config the simosaik config
   */
  public record Arguments(String[] mainArgs, String mosaikIP, SimosaikConfig config) {}

  /**
   * Method for parsing the provided arguments.
   *
   * @param args arguments the main simulation is started with
   * @return the parsed arguments
   */
  public static Arguments parse(String[] args) {
    Map<String, String> parsedArgs = new HashMap<>();

    for (String arg : args) {
      String[] key_value = arg.split("=");
      parsedArgs.put(key_value[0], key_value[1]);
    }

    String mosaikIP = extract(parsedArgs, "--ext-address");

    Path configPath = Path.of(extract(parsedArgs, "--config"));
    SimosaikConfig config = SimosaikConfig.load(configPath);

    return new Arguments(args, mosaikIP, config);
  }

  /**
   * Method for extracting values.
   *
   * @param parsedArgs map: argument key to value
   * @param element that should be extracted
   * @return a string value
   */
  private static String extract(Map<String, String> parsedArgs, String element) {
    String value = parsedArgs.get(element);

    if (value == null || value.isEmpty()) {
      throw new RuntimeException("No value found for required element " + element + "!");
    }

    return value;
  }
}
