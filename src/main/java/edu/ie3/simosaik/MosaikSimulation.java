/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.connection.ExtDataConnection;
import edu.ie3.simona.api.data.connection.ExtEmDataConnection;
import edu.ie3.simona.api.data.connection.ExtEmDataConnection.EmMode;
import edu.ie3.simona.api.data.connection.ExtPrimaryDataConnection;
import edu.ie3.simona.api.data.connection.ExtResultDataConnection;
import edu.ie3.simona.api.data.container.ExtInputContainer;
import edu.ie3.simona.api.data.container.ExtOutputContainer;
import edu.ie3.simona.api.data.model.em.EmData;
import edu.ie3.simona.api.data.model.em.FlexOptions;
import edu.ie3.simona.api.mapping.DataType;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import edu.ie3.simona.api.ontology.em.EmCompletion;
import edu.ie3.simona.api.ontology.em.EmDataResponseMessageToExt;
import edu.ie3.simona.api.ontology.em.EmResultResponse;
import edu.ie3.simona.api.ontology.em.FlexOptionsResponse;
import edu.ie3.simona.api.simulation.ExtCoSimFramework;
import edu.ie3.simona.api.simulation.ExtCoSimulation;
import edu.ie3.simosaik.initialization.InitializationData.ModelData;
import edu.ie3.simosaik.initialization.InitializationData.SimulatorData;
import edu.ie3.simosaik.initialization.InitializationData.TickInformation;
import edu.ie3.simosaik.utils.ConfigurableLogger;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Simple external mosaik simulation. This external simulation is capable to provide SIMONA with
 * primary and em data. Also, this simulation can send result data back to mosaik.
 */
public class MosaikSimulation extends ExtCoSimulation {

  private final long stepSize;
  private final long lastTick;
  protected final boolean sendUnchangedResults;

  private final ConfigurableLogger logger;
  public boolean run = true;
  private long lastFinishedTick = -1L;

  // connections
  private final ExtPrimaryDataConnection extPrimaryDataConnection;
  private final ExtEmDataConnection extEmDataConnection;
  private final ExtResultDataConnection extResultDataConnection;

  public MosaikSimulation(ExtCoSimFramework extCoSimFramework) {
    super("MosaikSimulation", extCoSimFramework);

    try {
      TickInformation tickInformation = getInitData(TickInformation.class);
      this.stepSize = tickInformation.stepSize();
      this.lastTick = tickInformation.lastTick();

      SimulatorData simulatorData = getInitData(SimulatorData.class);

      this.sendUnchangedResults = simulatorData.sendUnchangedResults();
      this.logger = new ConfigurableLogger(simulatorData.debugFlag(), log);

      ModelData modelData = getInitData(ModelData.class);

      ExtEntityMapping entityMapping = modelData.mapping();

      // primary data connection
      Map<UUID, Class<? extends Value>> primaryInput = entityMapping.getPrimaryMapping();

      this.extPrimaryDataConnection =
          !primaryInput.isEmpty() ? buildPrimaryConnection(primaryInput, log) : null;

      // em data connection
      Optional<EmMode> emMode = simulatorData.emMode();

      if (emMode.isPresent()) {
        List<UUID> controlledEms = entityMapping.getAssets(DataType.EM);

        this.extEmDataConnection = buildEmConnection(controlledEms, emMode.get(), log);
      } else {
        this.extEmDataConnection = null;
      }

      if (simulatorData.sendResults()) {
        // result data connection
        List<UUID> resultUuids = entityMapping.getAssets(DataType.RESULT);
        this.extResultDataConnection =
            !resultUuids.isEmpty() ? buildResultConnection(resultUuids, log) : null;
      } else {
        this.extResultDataConnection = null;
      }

    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Set<ExtDataConnection> getDataConnections() {
    return Stream.of(extPrimaryDataConnection, extEmDataConnection, extResultDataConnection)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  @Override
  protected final Long initialize() {
    log.info(
        "++++++++++++++++++++++++ Initialization of the external simulation ++++++++++++++++++++++++++");

    if (extPrimaryDataConnection != null) {
      logger.info("Primary assets: {}", extPrimaryDataConnection.getPrimaryDataAssets());
    }

    if (extEmDataConnection != null) {
      logger.info("EM mode: {}", extEmDataConnection.mode);
    }

    if (extResultDataConnection != null) {
      logger.info("Result assets: {}", extResultDataConnection.getResultUuids());
    }

    log.info(
        "++++++++++++++++++++++++ Initialization of the external simulation completed ++++++++++++++++");
    return 0L;
  }

  public final ExtOutputContainer handleExternalData(ExtInputContainer input) throws Exception {
    long tick = input.getTick();
    long nextTick = determineNextTick(tick);
    Optional<Long> maybeNextTick = Optional.of(determineNextTick(tick));

    Map<UUID, List<EmData>> emDataFromSIMONA = new HashMap<>();
    boolean sendAnyway = false;

    if (extPrimaryDataConnection != null && input.hasPrimaryData()) {
      extPrimaryDataConnection.sendPrimaryData(tick, input.extractPrimaryData(), input.getMaybeNextTick(), log);
    }

    if (extEmDataConnection != null && input.hasEmData()) {
      if (tick >= lastTick) {
        extEmDataConnection.simulateInternal(tick);
        extEmDataConnection.receiveWithType(EmCompletion.class);
      } else {

        // send received data to SIMONA
        var requests = input.extractFlexRequests();
        var options = input.extractFlexOptions();
        var setPoints = input.extractSetPoints();
        var emMessages = input.extractEmMessages();

        boolean sentEmData =
                extEmDataConnection.sendEmData(tick, requests, options, setPoints);
        boolean sentEmComData =
                extEmDataConnection.sendCommunicationMessage(tick, emMessages);

        if (sentEmData || sentEmComData) {
          logger.info("Sending em messages: {}", emMessages);
          logger.info("Sending em data: {}, {}, {}", requests, options, setPoints);
        } else {
          logger.info("Requesting a service completion for tick: {}.", tick);
          extEmDataConnection.requestCompletion(tick, nextTick);
        }

        EmDataResponseMessageToExt received = extEmDataConnection.receiveAny();

        switch (received) {
          case EmCompletion(Optional<Long> nextEmTick) -> {
            log.info("Next em tick: {}", nextEmTick);

            maybeNextTick = getNextTickOption(maybeNextTick, nextEmTick);
            sendAnyway = true;
            lastFinishedTick = tick;

            logger.info(
                    "Received completion for tick: {}. Next tick option: {}",
                    tick,
                    maybeNextTick);
          }
          case EmResultResponse(Map<UUID, List<EmData>> emResults) -> {
            emDataFromSIMONA.putAll(emResults);

            if (emDataFromSIMONA.isEmpty()) {
              sendAnyway = true;
            }
          }
          case FlexOptionsResponse(Map<UUID, List<FlexOptions>> receiverToFlexOptions) -> receiverToFlexOptions.forEach(
                  (receiver, data) ->
                          emDataFromSIMONA
                                  .computeIfAbsent(receiver, k -> new ArrayList<>())
                                  .addAll(data));
          default -> log.warn("Received unsupported data response: {}", received);
        }
      }
    }

    if (maybeNextTick.isPresent() && maybeNextTick.get() >= lastTick) {
      maybeNextTick = Optional.empty();
    }

    ExtOutputContainer container = new ExtOutputContainer(tick, maybeNextTick);
    container.addResults(requestResults(tick));
    container.addEmData(emDataFromSIMONA);

    if (!container.isEmpty() || sendAnyway) {
      return container;
    } else {
      return new ExtOutputContainer(tick, Optional.of(determineNextTick(tick)));
    }
  }

  public final ExtOutputContainer handleNoExternalData(long tick) throws Exception {
    simulateEmInternally(tick);

    ExtOutputContainer container = new ExtOutputContainer(tick, Optional.of(determineNextTick(tick)));
    container.addResults(requestResults(tick));

    return container;
  }

  @Override
  public final void finishSimulation(long tick) throws Exception {
    log.info("Mosaik is finished! The external simulation will not be activated anymore!");
    simulateEmInternally(tick);
  }

  @Override
  public long determineNextTick(long tick) {
    return Math.floorDiv(tick + stepSize, stepSize) * stepSize;
  }

  @Override
  public boolean continueActivity(long tick) {
    return lastFinishedTick < tick && run;
  }
  private Map<UUID, List<ResultEntity>> requestResults(long tick) throws InterruptedException {
    Map<UUID, List<ResultEntity>> resultsToBeSend = new HashMap<>();

    if (extResultDataConnection != null) {
      boolean includeUnchanged = sendUnchangedResults; // && !hasSentResults;

      resultsToBeSend.putAll(extResultDataConnection.requestResults(tick, includeUnchanged));

      logger.info(
              "Results (includeUnchanged={}) to be send: {}", includeUnchanged, resultsToBeSend);
    }

    return resultsToBeSend;
  }

  private void simulateEmInternally(long tick) throws InterruptedException {
    if (extEmDataConnection != null) {
      // to prevent em agents from blocking the scheduler
      extEmDataConnection.simulateInternal(tick);

      Optional<Long> nextEmTick = extEmDataConnection.receiveWithType(EmCompletion.class).maybeNextTick();
      log.info("Next em tick after internal simulation: {}", nextEmTick);
    }
  }
}
