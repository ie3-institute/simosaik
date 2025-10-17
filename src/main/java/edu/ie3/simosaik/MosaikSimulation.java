/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.connection.ExtDataConnection;
import edu.ie3.simona.api.data.connection.ExtEmDataConnection;
import edu.ie3.simona.api.data.connection.ExtEmDataConnection.EmMode;
import edu.ie3.simona.api.data.connection.ExtPrimaryDataConnection;
import edu.ie3.simona.api.data.connection.ExtResultDataConnection;
import edu.ie3.simona.api.data.container.ExtInputContainer;
import edu.ie3.simona.api.data.container.ExtOutputContainer;
import edu.ie3.simona.api.data.model.em.EmData;
import edu.ie3.simona.api.mapping.DataType;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import edu.ie3.simona.api.ontology.em.EmCompletion;
import edu.ie3.simona.api.ontology.em.EmDataResponseMessageToExt;
import edu.ie3.simona.api.ontology.em.EmResultResponse;
import edu.ie3.simona.api.simulation.ExtCoSimulation;
import edu.ie3.simosaik.initialization.InitializationData;
import edu.ie3.simosaik.synchronization.SIMONAPart;
import edu.ie3.simosaik.utils.SimosaikUtils;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple external mosaik simulation. This external simulation is capable to provide SIMONA with
 * primary and em data. Also, this simulation can send result data back to mosaik.
 */
public class MosaikSimulation extends ExtCoSimulation {

  protected static final Logger log = LoggerFactory.getLogger(MosaikSimulation.class);

  protected final long stepSize;
  protected final boolean disaggregateFlex;

  private final SIMONAPart synchronizer;

  // connections
  private final ExtPrimaryDataConnection extPrimaryDataConnection;
  private final ExtEmDataConnection extEmDataConnection;
  private final ExtResultDataConnection extResultDataConnection;

  public MosaikSimulation(SIMONAPart synchronizer) {
    this("MosaikSimulation", synchronizer);
  }

  public MosaikSimulation(String name, SIMONAPart synchronizer) {
    super(name, "MosaikSimulator");

    this.synchronizer = synchronizer;

    try {
      var initData = synchronizer.getInitializationData(InitializationData.SimulatorData.class);

      this.stepSize = synchronizer.getStepSize();
      this.disaggregateFlex = initData.disaggregate();

      InitializationData.ModelData modelData =
          synchronizer.getInitializationData(InitializationData.ModelData.class);

      ExtEntityMapping entityMapping = modelData.mapping();

      // primary data connection
      Map<UUID, Class<? extends Value>> primaryInput = entityMapping.getPrimaryMapping();

      this.extPrimaryDataConnection =
          !primaryInput.isEmpty() ? buildPrimaryConnection(primaryInput, log) : null;

      // em data connection
      Optional<EmMode> emMode = initData.emMode();

      if (emMode.isPresent()) {
        List<UUID> controlledEms = entityMapping.getAssets(DataType.EM);

        this.extEmDataConnection = buildEmConnection(controlledEms, emMode.get(), log);
      } else {
        this.extEmDataConnection = null;
      }

      // result data connection
      List<UUID> resultUuids = entityMapping.getAssets(DataType.RESULT);
      this.extResultDataConnection =
          !resultUuids.isEmpty() ? buildResultConnection(resultUuids, log) : null;

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
        "+++++++++++++++++++++++++++ Initialization of the external simulation +++++++++++++++++++++++++++");

    synchronizer.setDataQueues(queueToSimona, queueToExt);

    log.info(
        "+++++++++++++++++++++++++++ Initialization of the external simulation completed +++++++++++++++++++++++++++");
    return 0L;
  }

  @Override
  protected final Optional<Long> doActivity(long tick) {
    log.info(
        "+++++++++++++++++++++++++++ Activities in External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++",
        tick);

    Optional<Long> maybeNextTick;

    try {
      long nextTick = tick + stepSize;
      synchronizer.updateNextTickSIMONA(Optional.empty());
      synchronizer.updateTickSIMONA(tick);

      // clearing all previous data to prevent the usage of outdated data
      queueToSimona.clear();
      queueToExt.clear();

      if (!synchronizer.isFinished()) {
        maybeNextTick = activity(tick, nextTick);

        // setting the finished flag in the synchronizer for SIMONA
        synchronizer.setFinishedFlag();
      } else {
        // SIMONA will not receive data for the current tick
        maybeNextTick = Optional.of(nextTick);
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    log.info(
        "+++++++++++++++++++++++++++ Activities in External simulation finished for tick {}. +++++++++++++++++++++++++++",
        tick);
    return maybeNextTick;
  }

  protected Optional<Long> activity(long tick, long nextTick) throws InterruptedException {
    Optional<Long> maybeNextTick = Optional.of(nextTick);

    boolean expectInputs = synchronizer.expectInput();

    if (extPrimaryDataConnection != null && expectInputs) {
      // sending primary data to SIMONA
      sendPrimaryDataToSimona(extPrimaryDataConnection, tick, maybeNextTick, log);
    }

    if (extEmDataConnection != null) {
      // using em connection
      switch (extEmDataConnection.mode) {
        case BASE -> {
          // first we send flex options to mosaik
          sendFlexOptionsToExt(extEmDataConnection, tick, disaggregateFlex, log);

          // we will send the received set points to SIMONA
          sendEmSetPointsToSimona(extEmDataConnection, tick, maybeNextTick, log);

          // we will receive an em completion message
          extEmDataConnection.receiveWithType(EmCompletion.class);
        }
        case EM_COMMUNICATION -> {
          Optional<Long> nextEmChangeTick = useFlexCommunication(extEmDataConnection, tick);

          if (nextEmChangeTick.isPresent()) {
            if (nextEmChangeTick.get() < nextTick) {
              maybeNextTick = nextEmChangeTick;
            }
          } else {
            log.warn("There are no em inputs for tick '{}'. Skipping em communication.", tick);
          }
        }
        default ->
            throw new IllegalStateException(
                "The mode '" + extEmDataConnection.mode + "' is currently not supported!");
      }
    }

    if (extResultDataConnection != null) {
      // sending results to mosaik
      sendResultToExt(extResultDataConnection, tick, maybeNextTick, log);
    }

    return maybeNextTick;
  }

  protected Optional<Long> useFlexCommunication(ExtEmDataConnection extEmDataConnection, long tick)
      throws InterruptedException {
    // handle flex requests
    boolean notFinished = true;
    Optional<Long> maybeNextTick = Optional.empty();

    while (notFinished) {

      long extTick = synchronizer.currentMosaikTick();

      log.info("Current simulator tick: {}, SIMONA tick: {}", extTick, tick);

      Optional<ExtInputContainer> containerOption = getInputs(tick);

      if (tick == extTick && containerOption.isPresent()) {
        ExtInputContainer container = containerOption.get();

         log.info("Flex requests: {}", container.flexRequestsString());
         log.info("Flex options: {}", container.flexOptionsString());
         log.info("Set points: {}", container.setPointsString());

        // send received data to SIMONA
        var requests = container.extractFlexRequests();
        var options = container.extractFlexOptions();
        var setPoints = container.extractSetPoints();
        var emMessages = container.extractEmMessages();

        boolean sentEmRequests = extEmDataConnection.sendFlexRequest(tick, requests.keySet(), disaggregateFlex);

        boolean sentEmComData = extEmDataConnection.sendEmData(tick, emMessages, maybeNextTick);

        boolean sentSetPoints = extEmDataConnection.sendSetPoints(tick, setPoints, maybeNextTick);

        if (sentEmRequests || sentEmComData || sentSetPoints) {
          log.info("Sending em messages: {}", emMessages);
          log.info("Sending em data: {}, {}, {}", requests, options, setPoints);
        } else {
          extTick = synchronizer.currentMosaikTick();
          log.info("Requesting a service completion for tick: {}.", tick);
          extEmDataConnection.requestCompletion(tick, extTick);
        }

        log.info("Waiting for em message from SIMONA.");
        EmDataResponseMessageToExt received = extEmDataConnection.receiveAny();
        log.info("Received em message from SIMONA.");

        Map<UUID, List<EmData>> emDataFromSIMONA = new HashMap<>();

        switch (received) {
          case EmCompletion(Optional<Long> nextTick) -> {
            notFinished = false;
            maybeNextTick = nextTick;

            log.info("Received completion for tick: {}. Next tick option: {}", tick, maybeNextTick);
          }
          case EmResultResponse(Map<UUID, List<EmData>> emResults) ->
              emDataFromSIMONA.putAll(emResults);
          default -> log.warn("Received unsupported data response: {}", received);
        }

        synchronizer.updateNextTickSIMONA(maybeNextTick);

        log.warn("Em data to ext: {}", emDataFromSIMONA);

        ExtOutputContainer outputContainer = new ExtOutputContainer(tick, maybeNextTick);
        outputContainer.addEmData(emDataFromSIMONA);

        queueToExt.queueData(outputContainer);

      } else if (tick > extTick) {
        log.info("Waiting for external simulation to reach tick: {}", tick);
        queueToExt.queueData(new ExtOutputContainer(tick, maybeNextTick));
      } else {
        notFinished = false;
        extTick = synchronizer.currentMosaikTick();

        log.info("External simulator finished tick {}. Request completion.", tick);
        extEmDataConnection.requestCompletion(tick, extTick);

        maybeNextTick = Optional.of(extTick);
        log.info("Received completion for tick: {}. Next tick option: {}", tick, maybeNextTick);
      }
    }

    return maybeNextTick;
  }

  public Optional<ExtInputContainer> getInputs(long tick) {
    Optional<ExtInputContainer> container;

    try {
      do {
        container = queueToSimona.pollContainer(100, TimeUnit.MILLISECONDS);
      } while (tick == synchronizer.currentMosaikTick() && container.isEmpty());

    } catch (InterruptedException e) {
      container = Optional.empty();
    }

    return container;
  }
}
