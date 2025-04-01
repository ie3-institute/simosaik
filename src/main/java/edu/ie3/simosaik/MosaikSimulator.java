/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import de.offis.mosaik.api.SimProcess;
import de.offis.mosaik.api.Simulator;
import edu.ie3.datamodel.io.naming.timeseries.ColumnScheme;
import edu.ie3.simona.api.data.ExtDataContainerQueue;
import edu.ie3.simona.api.data.datacontainer.ExtInputDataContainer;
import edu.ie3.simona.api.data.datacontainer.ExtResultContainer;
import edu.ie3.simona.api.simulation.mapping.DataType;
import edu.ie3.simona.api.simulation.mapping.ExtEntityEntry;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simosaik.utils.ResultUtils;
import edu.ie3.simosaik.utils.SimosaikUtils;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/** The mosaik simulator that exchanges information with mosaik. */
public abstract class MosaikSimulator extends Simulator implements SimonaEntities {
  protected final Logger logger = SimProcess.logger;

  protected ExtEntityMapping mapping;
  public final int stepSize;

  public final LinkedBlockingQueue<List<ExtEntityEntry>> controlledQueue =
      new LinkedBlockingQueue<>();

  public ExtDataContainerQueue<ExtInputDataContainer> queueToSimona;
  public ExtDataContainerQueue<ExtResultContainer> queueToExt;

  // entities
  protected final Set<String> simonaPrimaryEntities = new HashSet<>();
  protected final Set<String> simonaResultEntities = new HashSet<>();
  protected final Set<String> simonaEmEntities = new HashSet<>();

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
          SimosaikUtils.createInputDataContainer(time, nextTick, inputs, mapping);
      logger.info("Converted input for SIMONA! Now try to send it to SIMONA!");

      logger.info(inputs.toString());

      queueToSimona.queueData(extDataForSimona);
      logger.info("Sent converted input for tick " + time + " to SIMONA!");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return nextTick;
  }

  @Override
  public Map<String, Object> getData(Map<String, List<String>> map) throws Exception {
    logger.info("Got a request from MOSAIK to provide data!");
    ExtResultContainer results = queueToExt.takeAll();
    logger.info("Got results from SIMONA for MOSAIK!");
    Map<String, Object> data = ResultUtils.createOutput(results, map, mapping);

    logger.info(data.toString());

    logger.info("Converted results for MOSAIK! Now send it to MOSAIK!");
    return data;
  }

  public abstract void setConnectionToSimonaApi(
      ExtDataContainerQueue<ExtInputDataContainer> queueToSimona,
      ExtDataContainerQueue<ExtResultContainer> queueToExt);

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

  @SuppressWarnings("unchecked")
  protected void process(Map<String, Object> simParams) throws InterruptedException {
    logger.info("Sim parameters: " + simParams);

    Map<DataType, Map<String, ExtEntityEntry>> mapping = new HashMap<>();

    for (DataType dataType : DataType.values()) {
      if (simParams.containsKey(dataType.type)) {

        Map<String, Object> mosaikMap = (Map<String, Object>) simParams.get(dataType.type);

        Map<String, ExtEntityEntry> entities = new HashMap<>();

        for (Map.Entry<String, Object> entry : mosaikMap.entrySet()) {
          String uuid = entry.getKey();
          Object data = entry.getValue();

          if (dataType == DataType.EXT_PRIMARY_INPUT) {

            Optional<ColumnScheme> columnScheme = Optional.empty();
            String id = "";

            if (data instanceof List<?>) {
              List<String> list = (List<String>) data;

              if (list.size() == 2) {
                columnScheme = ColumnScheme.parse(list.get(1));
                id = list.get(0);
              }
            } else {
              columnScheme = Optional.of(ColumnScheme.ACTIVE_POWER);
              id = (String) data;
              logger.warning("Received no value class for primary asset with id: "+ id +"! Use default: 'p'");
            }

            entities.put(
                    id,
                    new ExtEntityEntry(UUID.fromString(uuid), id, columnScheme, dataType));
          } else {
            String id = (String) data;

            entities.put(
                id,
                new ExtEntityEntry(UUID.fromString(uuid), id, Optional.empty(), dataType));
          }
        }

        mapping.put(dataType, entities);
      }
    }

    List<ExtEntityEntry> extEntities = new ArrayList<>();

    for (DataType dataType : mapping.keySet()) {
      switch (dataType) {
        case EXT_PRIMARY_INPUT -> {
          Map<String, ExtEntityEntry> primary = mapping.get(dataType);
          this.simonaPrimaryEntities.addAll(primary.keySet());
          extEntities.addAll(primary.values());
        }
        case EXT_GRID_RESULT, EXT_PARTICIPANT_RESULT, EXT_FLEX_OPTIONS_RESULT -> {
          Map<String, ExtEntityEntry> result = mapping.get(dataType);
          this.simonaResultEntities.addAll(result.keySet());
          extEntities.addAll(result.values());
        }
        case EXT_EM_INPUT -> {
          Map<String, ExtEntityEntry> em = mapping.get(dataType);
          this.simonaEmEntities.addAll(em.keySet());
          extEntities.addAll(em.values());
        }
      }
    }

    // set mapping
    this.mapping = new ExtEntityMapping(extEntities);

    controlledQueue.put(extEntities);
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
