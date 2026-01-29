/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import static edu.ie3.simosaik.SimonaEntity.*;
import static edu.ie3.simosaik.utils.MetaUtils.*;

import de.offis.mosaik.api.SimProcess;
import de.offis.mosaik.api.Simulator;
import edu.ie3.datamodel.io.naming.timeseries.ColumnScheme;
import edu.ie3.simona.api.data.connection.ExtEmDataConnection;
import edu.ie3.simona.api.data.container.ExtInputContainer;
import edu.ie3.simona.api.data.container.ExtOutputContainer;
import edu.ie3.simona.api.mapping.DataType;
import edu.ie3.simona.api.mapping.ExtEntityEntry;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import edu.ie3.simosaik.initialization.InitializationData;
import edu.ie3.simosaik.synchronization.MosaikPart;
import edu.ie3.simosaik.utils.InputUtils;
import edu.ie3.simosaik.utils.OutputUtils;
import java.util.*;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;

/** The mosaik simulator that exchanges information with mosaik. */
public class MosaikSimulator extends Simulator {
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(MosaikSimulator.class);
  private final Logger logger = SimProcess.logger;

  private final Map<SimonaEntity, Boolean> simonaEntities = new HashMap<>();

  private ExtEntityMapping mapping;
  private final Map<String, ColumnScheme> primaryType = new HashMap<>();

  private Optional<ExtOutputContainer> resultOption = Optional.empty();

  private long time;
  private final MosaikPart synchronizer;
  private final Runnable stopper;

  public MosaikSimulator(MosaikPart synchronizer, ExtEntityMapping mapping, Runnable stopper) {
    this("MosaikSimulator", synchronizer, mapping, stopper);
  }

  public MosaikSimulator(
      String name, MosaikPart synchronizer, ExtEntityMapping mapping, Runnable stopper) {
    super(name);
    this.synchronizer = synchronizer;
    this.mapping = mapping;
    this.stopper = stopper;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> init(
      String sid, Double timeResolution, Map<String, Object> simParams) {
    // scaling must be set first
    synchronizer.setMosaikTimeScaling(timeResolution);
    List<Model> models = new ArrayList<>();

    long stepSize;

    if (simParams.containsKey("step_size")) {
      stepSize = (long) simParams.get("step_size");

      // update the mosaik step size
      synchronizer.setMosaikStepSize(stepSize);
    } else {
      throw new IllegalArgumentException("Step size must be set!");
    }

    boolean sendUnchangedResults = false;

    if (simParams.containsKey("send_unchanged_results")) {
      sendUnchangedResults = (boolean) simParams.get("send_unchanged_results");
    }

    if (simParams.containsKey("models")) {
      List<String> modelTypes = (List<String>) simParams.get("models");

      logger.info("Ext entity types: " + modelTypes);

      for (String model : modelTypes) {
        SimonaEntity simonaEntity = SimonaEntity.parseType(model);
        simonaEntities.put(simonaEntity, Boolean.FALSE);
        models.add(from(simonaEntity));
      }
    } else {
      logger.warning(
          "No models provided! Valid models are: " + Arrays.toString(SimonaEntity.values()));
    }

    // setting up the em mode
    Optional<ExtEmDataConnection.EmMode> emMode = Optional.empty();

    if (simonaEntities.containsKey(EM_COMMUNICATION)) {
      emMode = Optional.of(ExtEmDataConnection.EmMode.EM_COMMUNICATION);
    } else if (simonaEntities.containsKey(EM) || simonaEntities.containsKey(EM_OPTIMIZER)) {
      emMode = Optional.of(ExtEmDataConnection.EmMode.BASE);
    }

    try {
      synchronizer.sendInitData(
          new InitializationData.SimulatorData(
              simonaEntities.containsKey(RESULTS), sendUnchangedResults, emMode));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    return createMeta(getType(simonaEntities.keySet()), models);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> create(int num, String model, Map<String, Object> modelParams) {
    List<Map<String, Object>> entities = new ArrayList<>();

    SimonaEntity modelType = SimonaEntity.parseType(model);
    DataType dataType = SimonaEntity.toType(modelType);

    Optional<ColumnScheme> scheme =
        switch (modelType) {
          case PRIMARY_P -> Optional.of(ColumnScheme.ACTIVE_POWER);
          case PRIMARY_PH -> Optional.of(ColumnScheme.ACTIVE_POWER_AND_HEAT_DEMAND);
          case PRIMARY_PQ -> Optional.of(ColumnScheme.APPARENT_POWER);
          case PRIMARY_PQH -> Optional.of(ColumnScheme.APPARENT_POWER_AND_HEAT_DEMAND);
          default -> Optional.empty();
        };

    Object included = modelParams.get("use");
    if (included == null) {
      Object mapping = modelParams.get("mapping");

      if (mapping != null) {
        logger.warning("Using deprecated parameter 'mapping', please change this to 'use'.");
        // to support old field name
        included = mapping;
      }
    }

    List<String> givenIds = new ArrayList<>();

    if (included instanceof Map<?, ?> map) {
      Map<String, String> entityMapping = (Map<String, String>) map;

      List<ExtEntityEntry> entries =
          entityMapping.entrySet().stream()
              .map(
                  e -> {
                    UUID uuid = UUID.fromString(e.getKey());
                    String id = e.getValue();

                    givenIds.add(id);

                    return new ExtEntityEntry(uuid, id, scheme, dataType);
                  })
              .toList();

      mapping = mapping.include(entries);

    } else if (included instanceof List<?> list) {
      givenIds.addAll((List<String>) list);
      mapping = mapping.include(dataType, givenIds, scheme);
    }

    if (scheme.isPresent() && givenIds.isEmpty()) {
      throw new IllegalArgumentException("Missing parameter 'use' primary input!");
    }

    List<String> ids = new ArrayList<>();

    if (givenIds.isEmpty()) {
      mapping.getAssets(dataType).forEach(uuid -> ids.add(mapping.from(uuid)));
    } else {
      // use given ids to build models
      ids.addAll(givenIds);
    }

    ids.forEach(
        id -> {
          scheme.ifPresent(column -> primaryType.put(id, column));

          Map<String, Object> entity = new HashMap<>();
          entity.put("eid", id);
          entity.put("type", model);
          entities.add(entity);
        });

    simonaEntities.put(modelType, Boolean.TRUE);

    if (entities.size() != num) {
      logger.warning(
          "The number of entities to built '"
              + num
              + "' does not match the number of built entities '"
              + entities.size()
              + "'!");
    }

    return entities;
  }

  @Override
  public void setupDone() throws Exception {
    List<SimonaEntity> entities = new ArrayList<>();

    simonaEntities.forEach(
        (entity, init) -> {
          if (!init) {
            entities.add(entity);
          }
        });

    if (!entities.isEmpty()) {
      logger.warning("The following models have not been initialized: " + entities);
    }

    synchronizer.sendInitData(new InitializationData.ModelData(mapping));
  }

  @Override
  public long step(long time, Map<String, Object> inputs, long maxAdvance) throws Exception {
    log.warn("Input: {}", inputs);

    this.time = time;

    // updating the mosaik time
    long scaledTime = synchronizer.updateMosaikTime(time);

    // the next tick we will expect data
    // long nextTick = synchronizer.getNextTick();

    logger.info("[" + time + "] Got inputs from MOSAIK for tick = " + time + ". Inputs: " + inputs);

    // log the expected next tick
    // logger.info("[" + time + "] Expected next simulation tick = " + nextTick);

    if (!inputs.isEmpty()) {
      ExtInputContainer extDataForSimona =
          InputUtils.createInput(scaledTime, mapping, inputs, primaryType);

      logger.info("[" + time + "] Converted input for SIMONA! Now try to send it to SIMONA!");

      // try sending data to SIMONA
      boolean isSent = synchronizer.sendInputData(extDataForSimona);

      if (isSent) {
        // only log, if data is actually send
        logger.info("[" + time + "] Sent converted input for tick " + time + " to SIMONA!");
      }
    } else {
      logger.info("[" + time + "] No inputs provided!");
      synchronizer.sendInputData(new ExtInputContainer(scaledTime));
    }

    // we need to wait until the results are there
    resultOption = synchronizer.requestResults();

    // getting the next tick, could have changed since last request
    long nextTick = synchronizer.getNextTick();
    logger.info("[" + time + "] Next tick: " + nextTick);
    return nextTick;
  }

  @Override
  public Map<String, Object> getData(Map<String, List<String>> map) {
    // requesting results from SIMONA
    // we will either get result for the current tick or no results, because SIMONA finished the
    // current tick

    boolean finished = synchronizer.isFinished();

    logger.info("[" + time + "] Got a request from MOSAIK to provide data!");

    if (finished) {
      Optional<ExtOutputContainer> additionalResults = synchronizer.requestResults();

      if (resultOption.isEmpty()) {
        resultOption = additionalResults;
      } else {
        additionalResults.ifPresent(
            additionalResult ->
                resultOption.ifPresent(
                    c -> {
                      c.addResults(additionalResult.getResults());
                      c.addEmData(additionalResult.getEmData());
                    }));
      }
    }

    if (resultOption.isPresent()) {
      ExtOutputContainer results = resultOption.get();

      if (!results.isEmpty()) {
        logger.info("[" + time + "] Got results from SIMONA for MOSAIK!");

        Map<String, Object> data = OutputUtils.createOutput(results, map, mapping);

        logger.info(
            "["
                + time
                + "] Converted results for MOSAIK! Now send it to MOSAIK! Data for MOSAIK: "
                + data);

        return data;
      }
    }

    // we have no data for the current tick
    if (synchronizer.outputNextTick()) {
      // to prevent sending this info twice
      synchronizer.setHasSendNextTick();

      long nextTick = synchronizer.getNextTick();

      // we should output the next tick information for those entities, that are requesting this
      // information
      logger.info(
          "["
              + time
              + "] Tick finished, sending only next tick information to mosaik. Next tick: "
              + nextTick);

      // we set the no output flag to true, since we need to return an empty map for mosaik to
      // continue with the next tick
      synchronizer.setNoOutputFlag();
      return OutputUtils.onlyTickInformation(map, nextTick);
    } else {

      if (finished) {
        // we will send an empty map, to signal mosaik, that this tick is finished
        logger.info("[" + time + "] Tick finished, sending no data to mosaik.");
      } else {
        logger.info("[" + time + "] Got no results from SIMONA!");
      }

      return Collections.emptyMap();
    }

    /*
    if (finished) {
      // we are finished for the current tick
      if (synchronizer.outputNextTick()) {
        // to prevent sending this info twice
        synchronizer.setHasSendNextTick();

        long nextTick = synchronizer.getNextTick();

        // we should output the next tick information for those entities, that are requesting this
        // information
        logger.info(
            "["
                + time
                + "] Tick finished, sending only next tick information to mosaik. Next tick: "
                + nextTick);

        // we set the no output flag to true, since we need to return an empty map for mosaik to
        // continue with the next tick
        synchronizer.setNoOutputFlag();
        return OutputUtils.onlyTickInformation(map, nextTick);
      } else {
        // we will send an empty map, to signal mosaik, that this tick is finished
        logger.info("[" + time + "] Tick finished, sending no data to mosaik.");
        return Collections.emptyMap();
      }
    } else {
      logger.info("[" + time + "] Got no results from SIMONA!");

      return Collections.emptyMap();
    }
     */
  }

  @Override
  public void cleanup() {
    // stops the external simulation
    stopper.run();
  }
}
