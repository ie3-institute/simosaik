/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.SimonaEntity.EM_COMMUNICATION;
import static edu.ie3.simosaik.SimonaEntity.EM_OPTIMIZER;
import static edu.ie3.simosaik.SimosaikUnits.*;
import static java.util.Collections.emptyList;

import de.offis.mosaik.api.Simulator;
import edu.ie3.simosaik.SimonaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/** Methods for simplifying the creation of mosaik meta information. */
public final class MetaUtils {

  /**
   * Method to retrieve all attributes for a given {@link SimonaEntity}.
   *
   * @param entity given entity
   * @return all attributes
   */
  private static List<String> getAttributes(SimonaEntity entity) {
    return switch (entity) {
      case PRIMARY_P -> List.of(ACTIVE_POWER);
      case PRIMARY_PH -> List.of(ACTIVE_POWER, THERMAL_POWER);
      case PRIMARY_PQ -> List.of(ACTIVE_POWER, REACTIVE_POWER);
      case PRIMARY_PQH -> List.of(ACTIVE_POWER, REACTIVE_POWER, THERMAL_POWER);
      case EM ->
          List.of(
              ACTIVE_POWER,
              REACTIVE_POWER,
              FLEX_SET_POINT,
              FLEX_OPTION_P_MIN,
              FLEX_OPTION_P_REF,
              FLEX_OPTION_P_MAX,
              FLEX_OPTIONS);
      case EM_COMMUNICATION ->
          List.of(SIMONA_NEXT_TICK, FLEX_REQUEST, FLEX_OPTIONS, FLEX_SET_POINT, FLEX_COM);
      case EM_OPTIMIZER ->
          List.of(
              ACTIVE_POWER,
              REACTIVE_POWER,
              FLEX_SET_POINT,
              FLEX_OPTION_MAP_P_MIN,
              FLEX_OPTION_MAP_P_REF,
              FLEX_OPTION_MAP_P_MAX,
              FLEX_OPTIONS_DISAGGREGATED);
      case GRID_RESULTS -> ALL_GRID_UNITS;
      case NODE_RESULTS -> List.of(VOLTAGE_MAG, VOLTAGE_ANG);
      case LINE_RESULTS -> List.of(CURRENT_MAG, CURRENT_ANG);
      case PARTICIPANT_RESULTS -> ALL_PARTICIPANT_UNITS;
    };
  }

  private MetaUtils() {
    throw new Error("Do not instantiate utility class!");
  }

  public static String getType(Collection<SimonaEntity> entities) {
    if (entities.contains(EM_COMMUNICATION) || entities.contains(EM_OPTIMIZER)) {
      return "hybrid";
    }

    return "time-based";
  }

  public static Model from(SimonaEntity entity) {
    List<String> attributes = getAttributes(entity);

    Model model = Model.of(entity.name).params("mapping").attrs(attributes);

    if (entity.equals(EM_COMMUNICATION)) {
      return model.triggers(attributes);
    }

    if (entity.equals(EM_OPTIMIZER)) {
      return model.triggers(attributes);
    }

    return model;
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
      obj.put("non-persistent", nonPersistentArray);
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
