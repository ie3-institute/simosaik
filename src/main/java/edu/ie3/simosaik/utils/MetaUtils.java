/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.SimonaEntity.*;
import static edu.ie3.simosaik.SimonaEntity.FLEX_RESULTS;
import static edu.ie3.simosaik.utils.SimosaikTranslation.*;
import static edu.ie3.simosaik.utils.SimosaikTranslation.FLEX_OPTION_P_MAX;
import static java.util.Collections.emptyList;
import static java.util.Map.entry;

import de.offis.mosaik.api.Simulator;
import edu.ie3.simosaik.SimonaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/** Methods for simplifying the creation of mosaik meta information. */
public final class MetaUtils {

  private static final Map<SimonaEntity, List<String>> attributes =
      Map.ofEntries(
          entry(PRIMARY_P, List.of(MOSAIK_ACTIVE_POWER)),
          entry(PRIMARY_PQ, List.of(MOSAIK_ACTIVE_POWER, MOSAIK_REACTIVE_POWER)),
          entry(EM, List.of(MOSAIK_ACTIVE_POWER, MOSAIK_REACTIVE_POWER)),
          entry(EM_COMMUNICATION, List.of(FLEX_REQUEST, FLEX_OPTIONS, MOSAIK_ACTIVE_POWER)),
          entry(GRID_RESULTS, ALL_MOSAIK_UNITS),
          entry(PARTICIPANT_RESULTS, List.of(MOSAIK_ACTIVE_POWER, MOSAIK_REACTIVE_POWER)),
          entry(FLEX_RESULTS, List.of(FLEX_OPTION_P_MIN, FLEX_OPTION_P_REF, FLEX_OPTION_P_MAX)));

  private MetaUtils() {
    throw new Error("Do not instantiate utility class!");
  }

  public static String getType(Collection<SimonaEntity> entities) {
    if (entities.contains(EM_COMMUNICATION)) {
      return "hybrid";
    }

    return "time-based";
  }

  public static Model from(SimonaEntity entity) {
    Model model = Model.of(entity.name).params("mapping").attrs(attributes.get(entity));

    if (entity.equals(EM_COMMUNICATION)) {
      return model.triggers(attributes.get(entity));
    }

    return model;
  }

  public static Map<String, Object> createMeta(String type, Model... models) {
    return createMeta(type, List.of(models));
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> createMeta(String type, List<Model> additionalModels) {
    JSONObject meta = new JSONObject();
    meta.put("api_version", Simulator.API_VERSION);
    meta.put("type", type);

    JSONObject mosaikModels = new JSONObject();
    meta.put("models", mosaikModels);

    for (Model model : additionalModels) {
      mosaikModels.put(model.type, model.toJson());
    }

    return meta;
  }

  @SuppressWarnings("unchecked")
  public static JSONObject createObject(
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

  public record Model(
      String type,
      boolean isPublic,
      List<String> params,
      List<String> attrs,
      List<String> triggers,
      List<String> nonPersistent) {
    public static Model of(String type) {
      return new Model(type, true, emptyList(), emptyList(), emptyList(), emptyList());
    }

    public Model params(String... params) {
      return params(List.of(params));
    }

    public Model params(List<String> params) {
      return new Model(type, isPublic, params, attrs, triggers, nonPersistent);
    }

    public Model attrs(String... attrs) {
      return attrs(List.of(attrs));
    }

    public Model attrs(List<String> attrs) {
      return new Model(type, isPublic, params, attrs, triggers, nonPersistent);
    }

    public Model triggers(String... triggers) {
      return triggers(List.of(triggers));
    }

    public Model triggers(List<String> triggers) {
      return new Model(type, isPublic, params, attrs, triggers, nonPersistent);
    }

    public JSONObject toJson() {
      return createObject(isPublic, params, attrs, triggers, nonPersistent);
    }
  }
}
