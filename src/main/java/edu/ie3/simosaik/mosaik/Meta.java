/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.mosaik;

import static edu.ie3.simosaik.SimosaikTranslation.*;
import static edu.ie3.simosaik.SimosaikTranslation.MOSAIK_VOLTAGE_DEVIATION;

import de.offis.mosaik.api.Simulator;
import java.util.Map;
import org.json.simple.JSONValue;

public interface Meta {

  String SIMONA_POWER_GRID_ENVIRONMENT = "SimonaPowerGridEnvironment";
  String PRIMARY_INPUT_ENTITIES = "PrimaryInputEntities";
  String RESULT_OUTPUT_ENTITIES = "ResultOutputEntities";

  @SuppressWarnings("unchecked")
  Map<String, Object> meta =
      (Map<String, Object>)
          JSONValue.parse(
              ("{"
                      + "    'api_version': '"
                      + Simulator.API_VERSION
                      + "',"
                      + "    'type': 'time-based',"
                      + "    'models': {"
                      + "        '"
                      + SIMONA_POWER_GRID_ENVIRONMENT
                      + "': {"
                      + "            'public': true,"
                      + "            'params': ['simona_config'],"
                      + "            'attrs': []"
                      + "        },"
                      + "        '"
                      + PRIMARY_INPUT_ENTITIES
                      + "': {"
                      + "            'public': true,"
                      + "            'params': [],"
                      + "            'attrs': ['"
                      + MOSAIK_ACTIVE_POWER
                      + "', '"
                      + MOSAIK_REACTIVE_POWER
                      + "', '"
                      + MOSAIK_VOLTAGE_DEVIATION
                      + "']"
                      + "        },"
                      + "        '"
                      + RESULT_OUTPUT_ENTITIES
                      + "': {"
                      + "            'public': true,"
                      + "            'params': [],"
                      + "            'attrs': ['"
                      + MOSAIK_ACTIVE_POWER
                      + "', '"
                      + MOSAIK_REACTIVE_POWER
                      + "', '"
                      + MOSAIK_VOLTAGE_DEVIATION
                      + "']"
                      + "        }"
                      + "    }"
                      + "}")
                  .replace("'", "\""));

  default Map<String, Object> getMeta() {
    return meta;
  }
}
