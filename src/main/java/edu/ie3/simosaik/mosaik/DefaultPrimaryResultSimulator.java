/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.mosaik;

import static edu.ie3.simona.api.simulation.mapping.DataType.EXT_GRID_RESULT;
import static edu.ie3.simona.api.simulation.mapping.DataType.EXT_PRIMARY_INPUT;
import static edu.ie3.simosaik.SimosaikTranslation.*;

import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import java.util.List;
import java.util.Map;

/** A simulator, that sends primary data to SIMONA and receives results for MOSAIK. */
public class DefaultPrimaryResultSimulator extends MosaikSimulator {

  protected String[] simonaPrimaryEntities;
  protected String[] simonaResultEntities;

  public DefaultPrimaryResultSimulator() {
    this(900);
  }

  public DefaultPrimaryResultSimulator(int stepSize) {
    super(stepSize);
  }

  @Override
  public Map<String, Object> init(String sid, Float timeResolution, Map<String, Object> simParams) {
    return Meta.createMeta(
        "time-base",
        Model.simonaPowerGridEnvironment(),
        Model.withoutParams(
            PRIMARY_INPUT_ENTITIES,
            true,
            MOSAIK_ACTIVE_POWER,
            MOSAIK_REACTIVE_POWER,
            MOSAIK_VOLTAGE_DEVIATION_PU),
        Model.withoutParams(
            RESULT_OUTPUT_ENTITIES,
            true,
            MOSAIK_ACTIVE_POWER,
            MOSAIK_REACTIVE_POWER,
            MOSAIK_VOLTAGE_DEVIATION_PU));
  }

  @Override
  public List<Map<String, Object>> create(int num, String model, Map<String, Object> modelParams) {
    return switch (model) {
      case SIMONA_POWER_GRID_ENVIRONMENT -> {
        if (num != 1) {
          throwException(num, 1, model);
        }
        yield buildMap(new String[] {model}, model);
      }
      case PRIMARY_INPUT_ENTITIES -> {
        int allowed = simonaPrimaryEntities.length;

        if (num != allowed) {
          throwException(num, allowed, model);
        }
        yield buildMap(simonaPrimaryEntities, model);
      }
      case RESULT_OUTPUT_ENTITIES -> {
        int allowed = simonaResultEntities.length;

        if (num != allowed) {
          throwException(num, allowed, model);
        }
        yield buildMap(simonaResultEntities, model);
      }
      default ->
          throw new IllegalArgumentException(
              "The model " + model + " is not supported by SimonaSimulator.");
    };
  }

  @Override
  public void setMapping(ExtEntityMapping mapping) {
    this.simonaPrimaryEntities =
        mapping.getExtId2UuidMapping(EXT_PRIMARY_INPUT).keySet().toArray(new String[0]);
    this.simonaResultEntities =
        mapping.getExtId2UuidMapping(EXT_GRID_RESULT).keySet().toArray(new String[0]);
  }
}
