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
import edu.ie3.simona.api.simulation.ExtCoSimFramework;
import edu.ie3.simosaik.initialization.InitializationData;
import edu.ie3.simosaik.utils.ConfigurableLogger;
import edu.ie3.simosaik.utils.InputUtils;
import edu.ie3.simosaik.utils.OutputUtils;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;

/** The mosaik simulator that exchanges information with mosaik. */
public class MosaikSimulator extends Simulator implements ExtCoSimFramework<InitializationData> {
  private static final ConfigurableLogger log =
      new ConfigurableLogger(false, LoggerFactory.getLogger(MosaikSimulator.class));
  private final Logger logger = SimProcess.logger;

  private Queue<InitializationData> initDataQueue;
  private TickConverter tickConverter;
  private long lastTick = Long.MAX_VALUE;

  private final Map<SimonaEntity, Boolean> simonaEntities = new HashMap<>();

  private ExtEntityMapping mapping;
  private final Map<String, ColumnScheme> primaryType = new HashMap<>();

  private ExtInputContainer currentInputData;
  private ExtOutputContainer currentOutputData;

  private long time;
  private long scaledTime;
  private long nextSimonaTick;
  private boolean hasNextTickChanged = false;
  private boolean hasSendNextTick = false;
  private final Runnable stopper;

  // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
  // synchronization objects
  private final ReentrantLock synchronizationLock = new ReentrantLock();
  private final Condition waitForStatus = synchronizationLock.newCondition();
  private final AtomicBoolean newStatusPresent = new AtomicBoolean(false);
  private final Condition waitForResults = synchronizationLock.newCondition();

  // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

  public MosaikSimulator(ExtEntityMapping mapping, Runnable stopper) {
    this("MosaikSimulator", mapping, stopper);
  }

  public MosaikSimulator(String name, ExtEntityMapping mapping, Runnable stopper) {
    super(name);
    this.mapping = mapping;
    this.stopper = stopper;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> init(
      String sid, Double timeResolution, Map<String, Object> simParams) {
    // scaling must be set first
    tickConverter = new TickConverter(timeResolution);

    List<Model> models = new ArrayList<>();

    // set up tick information
    long stepSize;

    if (simParams.containsKey("step_size")) {
      stepSize = (long) simParams.get("step_size");
    } else {
      throw new IllegalArgumentException("Step size must be set!");
    }

    if (simParams.containsKey("last_tick")) {
      this.lastTick = (long) simParams.get("last_tick");
    }

    initDataQueue.add(
        new InitializationData.TickInformation(
            tickConverter.toSimonaTick(stepSize), tickConverter.toSimonaTick(lastTick)));

    // set up simulator data
    boolean debugFlag = (boolean) simParams.getOrDefault("debug", false);
    log.setFlag(debugFlag);

    boolean sendUnchangedResults =
        (boolean) simParams.getOrDefault("send_unchanged_results", false);

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

    initDataQueue.add(
        new InitializationData.SimulatorData(
            simonaEntities.containsKey(RESULTS), sendUnchangedResults, debugFlag, emMode));
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
      throw new IllegalArgumentException("Missing parameter 'use' for primary input!");
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
  public void setupDone() {
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

    initDataQueue.add(new InitializationData.ModelData(mapping));
  }

  @Override
  public long step(long time, Map<String, Object> inputs, long maxAdvance) throws Exception {
    log.warn("[{}] Input: {}", time, inputs);

    this.time = time;

    // updating the mosaik time
    this.scaledTime = tickConverter.toSimonaTick(time);

    // the next tick we will expect data
    // long nextTick = synchronizer.getNextTick();

    logger.info("[" + time + "] Got inputs from MOSAIK for tick = " + time + ". Inputs: " + inputs);

    // log the expected next tick
    // logger.info("[" + time + "] Expected next simulation tick = " + nextTick);

    if (!inputs.isEmpty()) {
      logger.info("[" + time + "] Sent converted input for tick " + time + " to SIMONA!");

      this.currentInputData = InputUtils.createInput(scaledTime, mapping, inputs, primaryType);
    } else {
      logger.info("[" + time + "] No inputs provided!");

      this.currentInputData = new ExtInputContainer(scaledTime);
    }

    // get the synchronization lock
    while (!synchronizationLock.tryLock()) {
      log.warn("MOSAIK: Waiting for synchronization lock.");
    }

    // sent signal that status can be retrieved now
    waitForStatus.signal();
    newStatusPresent.set(true);

    // wait for results
    waitForResults.await();
    newStatusPresent.set(false);

    // getting the next tick, could have changed since last request
    OptionalLong maybeNextTick = currentOutputData.getMaybeNextTick();

    if (maybeNextTick.isPresent()) {
      long nextTick = tickConverter.toExtTick(maybeNextTick.getAsLong());
      hasNextTickChanged = nextTick != nextSimonaTick;
      nextSimonaTick = nextTick;

      logger.info("[" + time + "] Next tick: " + nextSimonaTick);
      return nextSimonaTick;
    } else {
      logger.warning("[" + time + "] No next tick information provided!");
      nextSimonaTick = Long.MAX_VALUE;
    }

    return nextSimonaTick;
  }

  @Override
  public Map<String, Object> getData(Map<String, List<String>> map) {
    // requesting results from SIMONA
    // we will either get result for the current tick or no results, because SIMONA finished the
    // current tick
    logger.info("[" + time + "] Got a request from MOSAIK to provide data!");

    if (!currentOutputData.isEmpty()) {
      hasSendNextTick = false;
      logger.info("[" + time + "] Got results from SIMONA for MOSAIK!");

      Map<String, Object> data = OutputUtils.createOutput(currentOutputData, map, mapping);

      logger.info(
          "["
              + time
              + "] Converted results for MOSAIK! Now send it to MOSAIK! Data for MOSAIK: "
              + data);

      return data;
    } else if (hasNextTickChanged || !hasSendNextTick) {
      // we should output the next tick information for those entities, that are requesting this
      // information
      logger.info(
          "["
              + time
              + "] Tick finished, sending only next tick information to mosaik. Next tick: "
              + nextSimonaTick);

      // we set the no output flag to true, since we need to return an empty map for mosaik to
      // continue with the next tick
      hasSendNextTick = true;
      return OutputUtils.onlyTickInformation(map, nextSimonaTick);
    } else {
      logger.info("[" + time + "] Got no results from SIMONA!");
      return Collections.emptyMap();
    }
  }

  @Override
  public void cleanup() {
    // stops the external simulation
    stopper.run();
  }

  @Override
  public String getName() {
    return getSimName();
  }

  @Override
  public void setInitDataQueue(Queue<InitializationData> initDataQueue) {
    this.initDataQueue = initDataQueue;
  }

  @Override
  public Status getStatus(long simonaTick) throws InterruptedException {
    if (newStatusPresent.get()) {
      newStatusPresent.set(false);
    } else {
      // get the synchronization lock
      while (!synchronizationLock.tryLock()) {
        log.warn("SIMONA: Waiting for synchronization lock.");
      }

      log.info("Waiting for new input data.");

      // we need to wait for the input data
      waitForStatus.await();
    }

    if (simonaTick == scaledTime) {
      return new HasData(currentInputData);
    } else if (simonaTick < scaledTime) {
      return new SimonaIsBehind(scaledTime);
    } else {
      return new SimonaIsAhead();
    }
  }

  @Override
  public void provideOutputData(ExtOutputContainer outputData) {
    this.currentOutputData = outputData;

    // get the synchronization lock
    while (!synchronizationLock.tryLock()) {
      log.warn("SIMONA: Waiting for synchronization lock.");
    }

    // we need to wait for the input data
    waitForResults.signal();
    newStatusPresent.set(false);
    log.info("Provided mosaik with results.");
  }

  @Override
  public void goToNextTick(long simonaTick) {
    // provide empty output to tell mosaik to go to the next tick
    provideOutputData(new ExtOutputContainer(simonaTick, OptionalLong.of(simonaTick)));
  }
}
