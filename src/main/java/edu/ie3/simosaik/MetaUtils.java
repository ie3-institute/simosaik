/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import static edu.ie3.simosaik.SimonaEntities.*;
import static edu.ie3.simosaik.utils.SimosaikTranslation.ALL_MOSAIK_UNITS;
import static java.util.Collections.emptyList;

import de.offis.mosaik.api.Simulator;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/** Methods for simplifying the creation of mosaik meta information. */
public interface MetaUtils {

  static Map<String, Object> createMetaWithPowerGrid(String type, ModelParams... additionalModels) {
    return createMeta(type, ModelParams.simonaPowerGridEnvironment(), List.of(additionalModels));
  }

  static Map<String, Object> createMeta(
      String type, ModelParams simonaPowerGrid, ModelParams... additionalModels) {
    return createMeta(type, simonaPowerGrid, List.of(additionalModels));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> createMeta(
      String type, ModelParams simonaPowerGrid, List<ModelParams> additionalModels) {
    JSONObject meta = new JSONObject();
    meta.put("api_version", Simulator.API_VERSION);
    meta.put("type", type);

    JSONObject mosaikModels = new JSONObject();
    meta.put("models", mosaikModels);
    mosaikModels.put(simonaPowerGrid.type, simonaPowerGrid.toJson());

    for (ModelParams model : additionalModels) {
      mosaikModels.put(model.type, model.toJson());
    }

    return meta;
  }

  @SuppressWarnings("unchecked")
  static JSONObject createObject(
      boolean isPublic,
      List<String> params,
      List<String> attrs,
      List<String> triggers,
      List<String> nonPersistent) {
    JSONObject obj = new JSONObject();
    obj.put("public", isPublic);

    if (params != null) {
      JSONArray paramArray = new JSONArray();
      paramArray.addAll(params);
      obj.put("params", paramArray);
    }

    if (attrs != null) {
      JSONArray attrArray = new JSONArray();
      attrArray.addAll(attrs);
      obj.put("attrs", attrArray);
    }

    if (triggers != null) {
      JSONArray triggerArray = new JSONArray();
      triggerArray.addAll(triggers);
      obj.put("trigger", triggerArray);
    }

    if (nonPersistent != null) {
      JSONArray nonPersistentArray = new JSONArray();
      nonPersistentArray.addAll(triggers);
      obj.put("non_persistent", nonPersistentArray);
    }

    return obj;
  }

  record ModelParams(
      String type,
      boolean isPublic,
      List<String> params,
      List<String> attrs,
      List<String> triggers,
      List<String> nonPersistent) {
    public static ModelParams of(String type) {
      return of(type, ALL_MOSAIK_UNITS);
    }

    public static ModelParams of(String type, String... units) {
      return of(type, List.of(units));
    }

    public static ModelParams of(String type, List<String> units) {
      return new ModelParams(type, true, emptyList(), units, emptyList(), emptyList());
    }

    public static ModelParams withParams(String type, List<String> params) {
      return of(type, params, ALL_MOSAIK_UNITS, emptyList());
    }

    public static ModelParams withParams(String type, List<String> params, List<String> units) {
      return of(type, params, units, emptyList());
    }

    public static ModelParams of(String type, List<String> units, List<String> triggers) {
      return new ModelParams(type, true, emptyList(), units, triggers, emptyList());
    }

    public static ModelParams of(
        String type, List<String> params, List<String> units, List<String> triggers) {
      return new ModelParams(type, true, params, units, triggers, emptyList());
    }

    public static ModelParams simonaPowerGridEnvironment() {
      return new ModelParams(
          SIMONA_POWER_GRID_ENVIRONMENT,
          true,
          List.of("simona_config"),
          emptyList(),
          emptyList(),
          emptyList());
    }

    public JSONObject toJson() {
      return createObject(isPublic, params, attrs, triggers, nonPersistent);
    }
  }
}
