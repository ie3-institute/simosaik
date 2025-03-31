/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.flexibility;

import edu.ie3.simona.api.data.ExtDataContainerQueue;
import edu.ie3.simona.api.data.datacontainer.ExtInputDataContainer;
import edu.ie3.simona.api.data.datacontainer.ExtResultContainer;
import edu.ie3.simona.api.simulation.mapping.DataType;
import edu.ie3.simona.api.simulation.mapping.ExtEntityEntry;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simosaik.MetaUtils;
import edu.ie3.simosaik.MetaUtils.Model;
import edu.ie3.simosaik.MosaikSimulator;
import edu.ie3.simosaik.utils.ResultUtils;
import edu.ie3.simosaik.utils.SimosaikUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static edu.ie3.simona.api.simulation.mapping.DataType.EXT_EM_INPUT;
import static edu.ie3.simosaik.utils.MosaikMessageParser.MosaikMessage;
import static edu.ie3.simosaik.utils.MosaikMessageParser.parse;
import static edu.ie3.simosaik.utils.SimosaikTranslation.*;

// TODO: Refactor this class
public class FlexCommunicationSimulator extends MosaikSimulator {
  private static final Logger log = LoggerFactory.getLogger(FlexCommunicationSimulator.class);

  /** Agents who receive set points */
  protected Set<String> simonaEmAgents;

  private final Set<MosaikMessage> cache = new HashSet<>();

  protected long time;

  public final LinkedBlockingQueue<List<UUID>> controlledQueue = new LinkedBlockingQueue<>();

  public FlexCommunicationSimulator(int stepSize) {
    super("FlexCommunicationSimulator", stepSize);
  }

  @Override
  public Map<String, Object> init(String sid, Float timeResolution, Map<String, Object> simParams) {
    Map<DataType, List<ExtEntityEntry>> extEntityMapping = new HashMap<>();
    List<Model> models = new ArrayList<>();

    models.add(Model.of(SIMONA_POWER_GRID_ENVIRONMENT).attrs("simona_config"));

    if (simParams.containsKey(MetaUtils.EM_COMMUNICATION)) {
      Map<String, String> emCommunication =
          (Map<String, String>) simParams.get(MetaUtils.EM_COMMUNICATION);

      log.warn("Em communication: {}", emCommunication);

      List<ExtEntityEntry> extEmEntries =
          emCommunication.entrySet().stream()
              .map(
                  e ->
                      new ExtEntityEntry(
                          UUID.fromString(e.getKey()),
                          e.getValue(),
                          Optional.empty(),
                          EXT_EM_INPUT))
              .toList();
      extEntityMapping.put(EXT_EM_INPUT, extEmEntries);

      this.simonaEmAgents = extEmEntries.stream().map(ExtEntityEntry::id).collect(Collectors.toSet());

      try {
        controlledQueue.put(extEmEntries.stream().map(ExtEntityEntry::uuid).toList());
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

        List<String> emUnits =
          List.of(
              FLEX_REQUEST,
              MOSAIK_ACTIVE_POWER,
              MOSAIK_REACTIVE_POWER,
              FLEX_OPTION_P_MIN,
              FLEX_OPTION_P_REF,
              FLEX_OPTION_P_MAX);

      models.add(Model.of(EM_AGENT_ENTITIES).attrs(emUnits).triggers(emUnits));
    }

    log.warn("Ext entity mapping: {}", extEntityMapping);

    // set mapping
    this.mapping = new ExtEntityMapping(extEntityMapping);

    return MetaUtils.createMeta("hybrid", models);
  }

  @Override
  public final List<Map<String, Object>> create(
      int num, String model, Map<String, Object> modelParams) {
    List<Map<String, Object>> entities = new ArrayList<>();
    if (Objects.equals(model, SIMONA_POWER_GRID_ENVIRONMENT)) {
      if (num != 1) {
        throw new IllegalArgumentException(
            "Requested number ("
                + num
                + ") of "
                + SIMONA_POWER_GRID_ENVIRONMENT
                + " entities is not possible.");
      }
      Map<String, Object> entity = new HashMap<>();
      entity.put("eid", model);
      entity.put("type", model);

      // EM_AGENT_ENTITIES
      List<Map<String, Object>> childEntities = new ArrayList<>(buildMap(EM_AGENT_ENTITIES, simonaEmAgents));

      entity.put("children", childEntities);
      entities.add(entity);
      return entities;
    } else {
      throw new IllegalArgumentException(
          "The model " + model + " is not supported by SimonaSimulator.");
    }
  }

  @Override
  public long step(long time, Map<String, Object> inputs, long maxAdvance) {
    if (this.time != time) {
      this.cache.clear();
    }

    this.time = time;
    logger.info("[" + this.time + "] Got inputs from MOSAIK!");
    long nextTick = time + this.stepSize;
    try {
      if (!inputs.isEmpty()) {
        logger.info(inputs.toString());

        List<MosaikMessage> mosaikMessages =
            parse(inputs).stream().filter(msg -> !cache.contains(msg)).collect(Collectors.toList());
        cache.addAll(mosaikMessages);

        logger.info("Parsed messages: " + mosaikMessages);

        ExtInputDataContainer container = SimosaikUtils.createInputDataContainer(time, nextTick, mosaikMessages, mapping);
        logger.info(container.flexOptionsString());

        // logger.info("Converted input for SIMONA! Now try to send it to SIMONA!");
        queueToSimona.queueData(container);
        logger.info("[" + this.time + "] Sent converted input to SIMONA!");
      } else {
        logger.info("[" + this.time + "] Got an empty input, so we wait for valid data!");
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return nextTick;
  }

  @Override
  public Map<String, Object> getData(Map<String, List<String>> map) throws Exception {
    logger.info("[" + this.time + "] Got a request from MOSAIK to provide data!");

    logger.info(map.toString());

    ExtResultContainer results = queueToExt.takeAll();
    Map<String, Object> output = ResultUtils.createOutput(results, map, mapping);
    output.put("time", this.time);

    logger.info(output.toString());

    logger.info("[" + this.time + "] Converted results for MOSAIK! Now send it to MOSAIK!\n");
    return output;
  }

  public void setConnectionToSimonaApi(
      ExtDataContainerQueue<ExtInputDataContainer> dataQueueExtCoSimulatorToSimonaApi,
      ExtDataContainerQueue<ExtResultContainer> dataQueueSimonaApiToExtCoSimulator) {
    logger.info("Set the mapping and the data queues between SIMONA and MOSAIK!");
    this.queueToExt = dataQueueSimonaApiToExtCoSimulator;
    this.queueToSimona = dataQueueExtCoSimulatorToSimonaApi;
  }
}
