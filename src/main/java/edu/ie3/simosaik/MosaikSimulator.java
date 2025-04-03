/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import static edu.ie3.simosaik.utils.MetaUtils.*;

import de.offis.mosaik.api.SimProcess;
import de.offis.mosaik.api.Simulator;
import edu.ie3.datamodel.io.naming.timeseries.ColumnScheme;
import edu.ie3.simona.api.data.ExtDataContainerQueue;
import edu.ie3.simona.api.data.container.ExtInputDataContainer;
import edu.ie3.simona.api.data.container.ExtResultContainer;
import edu.ie3.simona.api.data.mapping.DataType;
import edu.ie3.simona.api.data.mapping.ExtEntityEntry;
import edu.ie3.simona.api.data.mapping.ExtEntityMapping;
import edu.ie3.simosaik.utils.InputUtils;
import edu.ie3.simosaik.utils.ResultUtils;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/** The mosaik simulator that exchanges information with mosaik. */
public class MosaikSimulator extends Simulator {
  protected final Logger logger = SimProcess.logger;

  protected final Map<SimonaEntity, Optional<List<ExtEntityEntry>>> extEntityEntries =
      new HashMap<>();
  protected ExtEntityMapping mapping;
  public final int stepSize;

  public final LinkedBlockingQueue<ExtEntityMapping> controlledQueue = new LinkedBlockingQueue<>();

  public ExtDataContainerQueue<ExtInputDataContainer> queueToSimona;
  public ExtDataContainerQueue<ExtResultContainer> queueToExt;

  public MosaikSimulator(String name, int stepSize) {
    super(name);
    this.stepSize = stepSize;
  }

  public void setConnectionToSimonaApi(
      ExtDataContainerQueue<ExtInputDataContainer> dataQueueExtCoSimulatorToSimonaApi,
      ExtDataContainerQueue<ExtResultContainer> dataQueueSimonaApiToExtCoSimulator) {
    logger.info("Set the mapping and the data queues between SIMONA and MOSAIK!");
    this.queueToExt = dataQueueSimonaApiToExtCoSimulator;
    this.queueToSimona = dataQueueExtCoSimulatorToSimonaApi;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> init(String sid, Float timeResolution, Map<String, Object> simParams) {
    List<Model> models = new ArrayList<>();

    if (simParams.containsKey("models")) {
      List<String> modelTypes = (List<String>) simParams.get("models");

      logger.info("Ext entity types: " + modelTypes);

      for (String model : modelTypes) {
        SimonaEntity simonaEntity = SimonaEntity.parseType(model);
        extEntityEntries.put(simonaEntity, Optional.empty());
        models.add(from(simonaEntity));
      }
    } else {
      logger.warning(
          "No models provided! Valid models are: " + Arrays.toString(SimonaEntity.values()));
    }

    return createMeta(getType(extEntityEntries.keySet()), models);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> create(int num, String model, Map<String, Object> modelParams) {
    List<Map<String, Object>> entities = new ArrayList<>();

    if (modelParams.containsKey("mapping")) {
      try {
        Map<String, String> mapping = (Map<String, String>) modelParams.get("mapping");

        // check model params
        checkModelParams(num, mapping.size());

        SimonaEntity modelType = SimonaEntity.parseType(model);
        List<ExtEntityEntry> entries = new ArrayList<>();

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
          UUID uuid = UUID.fromString(entry.getKey());
          String id = entry.getValue();

          // add mosaik model
          Map<String, Object> entity = new HashMap<>();
          entity.put("eid", id);
          entity.put("type", model);
          entities.add(entity);

          Optional<ColumnScheme> scheme =
              switch (modelType) {
                case PRIMARY_P -> Optional.of(ColumnScheme.ACTIVE_POWER);
                case PRIMARY_PQ -> Optional.of(ColumnScheme.APPARENT_POWER);
                default -> Optional.empty();
              };

          DataType dataType = SimonaEntity.toType(modelType);

          // add simona external entity entry
          entries.add(new ExtEntityEntry(uuid, id, scheme, dataType));
        }

        extEntityEntries.put(modelType, Optional.of(entries));

      } catch (Exception e) {
        throw new RuntimeException("Could not build models of type '" + model + "', due to: ", e);
      }
    } else {
      logger.warning("No models are build, because no mapping was provided!");
    }

    boolean allInitialized = extEntityEntries.values().stream().allMatch(Optional::isPresent);

    if (allInitialized) {
      try {
        List<ExtEntityEntry> entries =
            extEntityEntries.values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(Collection::stream)
                .toList();

        this.mapping = new ExtEntityMapping(entries);

        this.controlledQueue.put(mapping);

      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    return entities;
  }

  @Override
  public long step(long time, Map<String, Object> inputs, long maxAdvance) {
    long nextTick = time + this.stepSize;
    try {
      logger.info("Got inputs from MOSAIK for tick = " + time);
      ExtInputDataContainer extDataForSimona =
          InputUtils.createInputDataContainer(time, nextTick, inputs, mapping);
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

  protected void checkModelParams(int expected, int received) {
    if (received < expected) {
      throw new IllegalArgumentException(
          "Requested "
              + expected
              + " entities, but the provided mapping contains only information for "
              + received
              + " entities!");
    } else if (received > expected) {
      throw new IllegalArgumentException(
          "Requested "
              + expected
              + " entities, but the provided mapping contains information for more entities ("
              + received
              + ")!");
    }
  }
}
