/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.config;

import com.typesafe.config.ConfigFactory;
import java.io.File;
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
   * @param mappingPath of the ext mapping source
   */
  public record Arguments(String[] mainArgs, String mosaikIP, Path mappingPath) {}

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

    SimosaikConfig config =
        new SimosaikConfig(ConfigFactory.parseFile(new File(extract(parsedArgs, "--config"))));

    Path mappingPath = Path.of(config.simosaik.mappingPath);

    return new Arguments(args, mosaikIP, mappingPath);
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
