/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.primaryResultSimulator;

import static edu.ie3.simona.api.simulation.mapping.DataType.*;

import edu.ie3.simona.api.data.ExtDataContainerQueue;
import edu.ie3.simona.api.data.datacontainer.ExtInputDataContainer;
import edu.ie3.simona.api.data.datacontainer.ExtResultContainer;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simosaik.MetaUtils;
import edu.ie3.simosaik.MetaUtils.ModelParams;
import edu.ie3.simosaik.MosaikSimulator;
import java.util.*;

public class PrimaryResultSimulator extends MosaikSimulator {

  private Set<String> simonaPrimaryEntities;
  private Set<String> simonaResultEntities;

  public PrimaryResultSimulator(int stepSize) {
    super("PrimaryResultSimulator", stepSize);
  }

  @Override
  public Map<String, Object> init(String sid, Float timeResolution, Map<String, Object> simParams) {
    return MetaUtils.createMetaWithPowerGrid(
        "time-based",
        ModelParams.withParams(PRIMARY_INPUT_ENTITIES, List.of("mapping")),
        ModelParams.withParams(RESULT_OUTPUT_ENTITIES, List.of("mapping")));
  }

  @Override
  public List<Map<String, Object>> create(int num, String model, Map<String, Object> modelParams) {
    Set<String> simonaEntities =
        switch (model) {
          case SIMONA_POWER_GRID_ENVIRONMENT -> Set.of(model);
          case PRIMARY_INPUT_ENTITIES -> simonaPrimaryEntities;
          case RESULT_OUTPUT_ENTITIES -> simonaResultEntities;
          default ->
              throw new IllegalArgumentException(
                  "The model " + model + " is not supported by SimonaSimulator.");
        };

    logger.warning("Model params for type '" + model + "': " + modelParams);

    int allowed = simonaEntities.size();

    if (num != allowed) {
      throwException(num, allowed, model);
    }
    return buildMap(model, simonaEntities);
  }

  public void setConnectionToSimonaApi(
      ExtEntityMapping mapping,
      ExtDataContainerQueue<ExtInputDataContainer> dataQueueExtCoSimulatorToSimonaApi,
      ExtDataContainerQueue<ExtResultContainer> dataQueueSimonaApiToExtCoSimulator) {
    logger.info("Set the mapping and the data queues between SIMONA and MOSAIK!");
    this.queueToExt = dataQueueSimonaApiToExtCoSimulator;
    this.queueToSimona = dataQueueExtCoSimulatorToSimonaApi;

    // input entities
    this.simonaPrimaryEntities = mapping.getExtId2UuidMapping(EXT_PRIMARY_INPUT).keySet();

    // result entities
    this.simonaResultEntities = new HashSet<>();
    simonaResultEntities.addAll(mapping.getExtId2UuidMapping(EXT_GRID_RESULT).keySet());
    simonaResultEntities.addAll(mapping.getExtId2UuidMapping(EXT_PARTICIPANT_RESULT).keySet());
    simonaResultEntities.addAll(mapping.getExtId2UuidMapping(EXT_FLEX_OPTIONS_RESULT).keySet());
  }
}
