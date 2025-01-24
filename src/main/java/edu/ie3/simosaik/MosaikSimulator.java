/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import de.offis.mosaik.api.SimProcess;
import de.offis.mosaik.api.Simulator;
import edu.ie3.simona.api.data.DataQueueExtSimulationExtSimulator;
import edu.ie3.simona.api.data.ExtInputDataContainer;
import edu.ie3.simona.api.data.results.ExtResultContainer;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import java.util.*;
import java.util.logging.Logger;

/** The mosaik simulator that exchanges information with mosaik. */
public abstract class MosaikSimulator extends Simulator implements SimonaEntities, MetaUtils {
  protected final Logger logger = SimProcess.logger;

  public final int stepSize;

  protected DataQueueExtSimulationExtSimulator<ExtInputDataContainer> dataQueueMosaikToSimona;
  protected DataQueueExtSimulationExtSimulator<ExtResultContainer> dataQueueSimonaToMosaik;

  public MosaikSimulator(String name, int stepSize) {
    super(name);
    this.stepSize = stepSize;
  }

  @Override
  public long step(long time, Map<String, Object> inputs, long maxAdvance) {
    long nextTick = time + this.stepSize;
    try {
      logger.info("Got inputs from MOSAIK for tick = " + time);
      ExtInputDataContainer extDataForSimona =
          SimosaikUtils.createExtInputDataContainer(time, inputs, nextTick);
      logger.info("Converted input for SIMONA! Now try to send it to SIMONA!");
      dataQueueMosaikToSimona.queueData(extDataForSimona);
      logger.info("Sent converted input for tick " + time + " to SIMONA!");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return nextTick;
  }

  @Override
  public Map<String, Object> getData(Map<String, List<String>> map) throws Exception {
    logger.info("Got a request from MOSAIK to provide data!");
    ExtResultContainer results = dataQueueSimonaToMosaik.takeData();
    logger.info("Got results from SIMONA for MOSAIK!");
    Map<String, Object> data = SimosaikUtils.createSimosaikOutputMap(map, results);
    logger.info("Converted results for MOSAIK! Now send it to MOSAIK!");
    return data;
  }

  public abstract void setConnectionToSimonaApi(
      ExtEntityMapping mapping,
      DataQueueExtSimulationExtSimulator<ExtInputDataContainer> dataQueueExtCoSimulatorToSimonaApi,
      DataQueueExtSimulationExtSimulator<ExtResultContainer> dataQueueSimonaApiToExtCoSimulator);

  /**
   * Builds a map for each given entity.
   *
   * @param model type of entities
   * @param simonaEntities set of entities
   * @return a list of maps
   */
  protected List<Map<String, Object>> buildMap(String model, Set<String> simonaEntities) {
    List<Map<String, Object>> entities = new ArrayList<>();

    for (String simonaEntity : simonaEntities) {
      Map<String, Object> entity = new HashMap<>();
      entity.put("eid", simonaEntity);
      entity.put("type", model);
      entities.add(entity);
    }

    return entities;
  }

  protected void throwException(int num, int allowed, String type) {
    throw new IllegalArgumentException(
        "Requested number ("
            + num
            + ") of "
            + type
            + " entities is not possible. Allowed: "
            + allowed);
  }
}
