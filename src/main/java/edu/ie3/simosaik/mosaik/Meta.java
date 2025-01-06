/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.mosaik;

import static edu.ie3.simosaik.SimosaikTranslation.*;
import static edu.ie3.simosaik.SimosaikTranslation.MOSAIK_VOLTAGE_DEVIATION;
import static java.util.Collections.emptyList;

import de.offis.mosaik.api.Simulator;
import java.util.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public interface Meta {

  String SIMONA_POWER_GRID_ENVIRONMENT = "SimonaPowerGridEnvironment";
  String PRIMARY_INPUT_ENTITIES = "PrimaryInputEntities";
  String RESULT_OUTPUT_ENTITIES = "ResultOutputEntities";

  @SuppressWarnings("unchecked")
  static Map<String, Object> getMeta() {
    Map<String, Object> map = new HashMap<>();
    map.put("api_version", Simulator.API_VERSION);
    map.put("type", "time-based");

    JSONObject models = new JSONObject();

    models.put(
        SIMONA_POWER_GRID_ENVIRONMENT, createObject(true, List.of("simona_config"), emptyList()));

    models.put(
        PRIMARY_INPUT_ENTITIES,
        createObject(
            true,
            emptyList(),
            List.of(MOSAIK_ACTIVE_POWER, MOSAIK_REACTIVE_POWER, MOSAIK_VOLTAGE_DEVIATION)));

    models.put(
        RESULT_OUTPUT_ENTITIES,
        createObject(
            true,
            emptyList(),
            List.of(MOSAIK_ACTIVE_POWER, MOSAIK_REACTIVE_POWER, MOSAIK_VOLTAGE_DEVIATION)));

    map.put("models", models);

    return map;
  }

  @SuppressWarnings("unchecked")
  static JSONObject createObject(boolean isPublic, List<String> params, List<String> attrs) {
    JSONArray paramArray = new JSONArray();
    if (params != null) paramArray.addAll(params);

    JSONArray attrArray = new JSONArray();
    if (attrs != null) attrArray.addAll(attrs);

    JSONObject obj = new JSONObject();
    obj.put("public", isPublic);
    obj.put("params", paramArray);
    obj.put("attrs", attrArray);

    return obj;
  }
}
