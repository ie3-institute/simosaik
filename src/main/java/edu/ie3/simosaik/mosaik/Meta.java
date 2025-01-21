/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.mosaik;

import de.offis.mosaik.api.Simulator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

public interface Meta {

  String SIMONA_POWER_GRID_ENVIRONMENT = "SimonaPowerGridEnvironment";
  String PRIMARY_INPUT_ENTITIES = "PrimaryInputEntities";
  String RESULT_OUTPUT_ENTITIES = "ResultOutputEntities";


  @SuppressWarnings("unchecked")
  static Map<String, Object> createMeta(String type, Model ...models) {
    JSONObject meta = new JSONObject();
    meta.put("api_version", Simulator.API_VERSION);
    meta.put("type", type);

    JSONObject mosaikModels = new JSONObject();
    meta.put("models", mosaikModels);

    for (Model model: models) {
      mosaikModels.put(model.type, model.toJson());
    }

    return meta;
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


  record Model(
          String type,
          boolean isPublic,
          List<String> params,
          List<String> attrs
  ) {
    public static Model withoutParams(String type, String ...attrs) {
      return withoutParams(type, true, attrs);
    }

    public static Model withoutParams(String type, boolean isPublic, String ...attrs) {
      return new Model(type, isPublic, emptyList(), List.of(attrs));
    }

    public static Model withoutAttrs(String type, boolean isPublic, String ...params) {
      return new Model(type, isPublic, List.of(params), emptyList());
    }

    public static Model simonaPowerGridEnvironment() {
      return withoutAttrs(SIMONA_POWER_GRID_ENVIRONMENT, true, "simona_config");
    }

    public JSONObject toJson() {
      return createObject(isPublic, params, attrs);
    }
  }
}
