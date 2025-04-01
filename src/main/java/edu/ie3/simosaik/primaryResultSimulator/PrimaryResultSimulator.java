/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.primaryResultSimulator;

import static edu.ie3.simosaik.utils.SimosaikTranslation.ALL_MOSAIK_UNITS;

import edu.ie3.simona.api.data.ExtDataContainerQueue;
import edu.ie3.simona.api.data.datacontainer.ExtInputDataContainer;
import edu.ie3.simona.api.data.datacontainer.ExtResultContainer;
import edu.ie3.simosaik.MetaUtils;
import edu.ie3.simosaik.MetaUtils.Model;
import edu.ie3.simosaik.MosaikSimulator;
import java.util.*;

public class PrimaryResultSimulator extends MosaikSimulator {

  public PrimaryResultSimulator(int stepSize) {
    super("PrimaryResultSimulator", stepSize);
  }

  @Override
  public Map<String, Object> init(String sid, Float timeResolution, Map<String, Object> simParams) {
    try {
      process(simParams);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    List<Model> models = new ArrayList<>();
    models.add(Model.of(SIMONA_POWER_GRID_ENVIRONMENT).attrs("simona_config"));
    models.add(Model.of(PRIMARY_INPUT_ENTITIES).attrs(ALL_MOSAIK_UNITS));
    models.add(Model.of(RESULT_OUTPUT_ENTITIES).attrs(ALL_MOSAIK_UNITS));

    return MetaUtils.createMeta("time-based", models);
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
      ExtDataContainerQueue<ExtInputDataContainer> dataQueueExtCoSimulatorToSimonaApi,
      ExtDataContainerQueue<ExtResultContainer> dataQueueSimonaApiToExtCoSimulator) {
    logger.info("Set the mapping and the data queues between SIMONA and MOSAIK!");
    this.queueToExt = dataQueueSimonaApiToExtCoSimulator;
    this.queueToSimona = dataQueueExtCoSimulatorToSimonaApi;
  }
}
