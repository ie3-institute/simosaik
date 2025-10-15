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
import edu.ie3.simona.api.data.connection.ExtEmDataConnection;
import edu.ie3.simona.api.data.container.ExtInputContainer;
import edu.ie3.simona.api.data.container.ExtOutputContainer;
import edu.ie3.simona.api.mapping.DataType;
import edu.ie3.simona.api.mapping.ExtEntityEntry;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import edu.ie3.simosaik.initialization.InitializationData;
import edu.ie3.simosaik.synchronization.MosaikPart;
import edu.ie3.simosaik.utils.InputUtils;
import edu.ie3.simosaik.utils.MosaikMessageParser;
import edu.ie3.simosaik.utils.MosaikMessageParser.ParsedMessage;
import edu.ie3.simosaik.utils.ResultUtils;
import java.util.*;
import java.util.logging.Logger;

/** The mosaik simulator that exchanges information with mosaik. */
public class MosaikSimulator extends Simulator {
  private final Logger logger = SimProcess.logger;

  private final Map<SimonaEntity, Boolean> simonaEntities = new HashMap<>();

  private ExtEntityMapping mapping;
  private final List<InputUtils.MessageProcessor> messageProcessors = new ArrayList<>();

  private final List<ParsedMessage> cache = new ArrayList<>();

  private long time;

  private final MosaikPart synchronizer;

  public MosaikSimulator(MosaikPart synchronizer, ExtEntityMapping mapping) {
    this("MosaikSimulator", synchronizer, mapping);
  }

  public MosaikSimulator(String name, MosaikPart synchronizer, ExtEntityMapping mapping) {
    super(name);
    this.synchronizer = synchronizer;
    this.mapping = mapping;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> init(
      String sid, Double timeResolution, Map<String, Object> simParams) {
    // scaling must be set first
    synchronizer.setMosaikTimeScaling(timeResolution);
    List<Model> models = new ArrayList<>();

    long stepSize;
    Optional<ExtEmDataConnection.EmMode> emMode = Optional.empty();

    if (simParams.containsKey("step_size")) {
      stepSize = (Long) simParams.get("step_size");

      // update the mosaik step size
      synchronizer.setMosaikStepSize(stepSize);
    } else {
      throw new IllegalArgumentException("Step size must be set!");
    }

    if (simParams.containsKey("models")) {
      List<String> modelTypes = (List<String>) simParams.get("models");

      logger.info("Ext entity types: " + modelTypes);

      for (String model : modelTypes) {
        SimonaEntity simonaEntity = SimonaEntity.parseType(model);
        simonaEntities.put(simonaEntity, false);
        models.add(from(simonaEntity));

        // setting up the em mode
        switch (simonaEntity) {
          case EM_COMMUNICATION ->
              emMode = Optional.of(ExtEmDataConnection.EmMode.EM_COMMUNICATION);
          case EM, EM_OPTIMIZER -> emMode = Optional.of(ExtEmDataConnection.EmMode.BASE);
          default -> emMode = Optional.empty();
        }
      }
    } else {
      logger.warning(
          "No models provided! Valid models are: " + Arrays.toString(SimonaEntity.values()));
    }

    try {
      synchronizer.sendInitData(
          new InitializationData.SimulatorData(
              simonaEntities.containsKey(SimonaEntity.EM_OPTIMIZER), emMode));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    return createMeta(getType(simonaEntities.keySet()), models);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> create(int num, String model, Map<String, Object> modelParams) {
    List<Map<String, Object>> entities = new ArrayList<>();

    if (modelParams.containsKey("mapping")) {
      try {
        Map<String, String> mapping = (Map<String, String>) modelParams.get("mapping");

        // check model params
        checkModelParams(mapping.size(), num);
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
                case PRIMARY_PH -> Optional.of(ColumnScheme.ACTIVE_POWER_AND_HEAT_DEMAND);
                case PRIMARY_PQ -> Optional.of(ColumnScheme.APPARENT_POWER);
                case PRIMARY_PQH -> Optional.of(ColumnScheme.APPARENT_POWER_AND_HEAT_DEMAND);
                default -> Optional.empty();
              };

          DataType dataType = SimonaEntity.toType(modelType);

          // add simona external entity entry
          entries.add(new ExtEntityEntry(uuid, id, scheme, dataType));
        }

        this.mapping = this.mapping.updateWith(entries);
        simonaEntities.put(modelType, true);

      } catch (Exception e) {
        throw new RuntimeException("Could not build models of type '" + model + "', due to: ", e);
      }
    } else {
      logger.warning("No models are build, because no mapping was provided!");
    }

    boolean allInitialized = simonaEntities.values().stream().allMatch(x -> x == true);

    if (allInitialized) {
      try {
        synchronizer.sendInitData(new InitializationData.ModelData(mapping));

        // create input message processors
        Map<String, UUID> primaryIdToUuid = mapping.getExtId2UuidMapping(DataType.primaryTypes());

        if (!primaryIdToUuid.isEmpty())
          this.messageProcessors.add(new InputUtils.PrimaryMessageProcessor(primaryIdToUuid));

        Map<String, UUID> emIdToUuid = mapping.getExtId2UuidMapping(DataType.EM);
        logger.info("EmMapping: " + emIdToUuid);

        if (!emIdToUuid.isEmpty()) {
          this.messageProcessors.add(new InputUtils.EmMessageProcessor(emIdToUuid));
        }

      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    return entities;
  }

  @Override
  public long step(long time, Map<String, Object> inputs, long maxAdvance) throws Exception {
    if (time != this.time) {
      // clearing cache at the start of the new tick
      cache.clear();
      this.time = time;
    }

    // updating the mosaik time
    long scaledTime = synchronizer.updateMosaikTime(time);

    // the next tick we will expect data
    long nextTick = synchronizer.getNextTick();

    logger.info("[" + time + "] Got inputs from MOSAIK for tick = " + time + ". Inputs: " + inputs);

    // processing mosaik inputs
    List<ParsedMessage> parsedMessages = MosaikMessageParser.parse(inputs);
    List<ParsedMessage> filtered = MosaikMessageParser.filter(parsedMessages, cache);
    cache.addAll(filtered);

    // log the expected next tick
    logger.info("[" + time + "] Expected next simulation tick = " + nextTick);

    if (!filtered.isEmpty()) {
      ExtInputContainer extDataForSimona =
          InputUtils.createInputDataContainer(scaledTime, nextTick, filtered, messageProcessors);

      logger.info("[" + time + "] Converted input for SIMONA! Now try to send it to SIMONA!");

      // try sending data to SIMONA
      boolean isSent = synchronizer.sendInputData(extDataForSimona);

      if (isSent) {
        // only log, if data is actually send
        logger.info("[" + time + "] Sent converted input for tick " + time + " to SIMONA!");
      }
    } else {
      // setting the no input flag in the synchronizer for mosaik
      synchronizer.setNoInputFlag();
    }

    // getting the next tick, could have changed since last request
    nextTick = synchronizer.getNextTick();
    logger.info("[" + time + "] Next tick: " + nextTick);
    return nextTick;
  }

  @Override
  public Map<String, Object> getData(Map<String, List<String>> map) {
    // requesting results from SIMONA
    // we will either get result for the current tick or no results, because SIMONA finished the
    // current tick
    Optional<ExtOutputContainer> resultOption = synchronizer.requestResults();

    boolean finished = synchronizer.isFinished();

    logger.info("[" + time + "] Got a request from MOSAIK to provide data!");

    if (resultOption.isPresent()) {
      ExtOutputContainer results = resultOption.get();

      if (!results.isEmpty() && !finished) {
        logger.info("[" + time + "] Got results from SIMONA for MOSAIK!");

        Map<String, Object> data = ResultUtils.createOutput(results, map, mapping);

        logger.info(
            "["
                + time
                + "] Converted results for MOSAIK! Now send it to MOSAIK! Data for MOSAIK: "
                + data);

        return data;
      }
    }

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
        return ResultUtils.onlyTickInformation(map, nextTick);
      } else {
        // we will send an empty map, to signal mosaik, that this tick is finished
        logger.info("[" + time + "] Tick finished, sending no data to mosaik.");
        return Collections.emptyMap();
      }
    } else {
      logger.info("[" + time + "] Got no results from SIMONA!");

      return Collections.emptyMap();
    }
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
