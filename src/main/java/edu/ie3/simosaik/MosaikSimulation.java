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
import edu.ie3.simona.api.data.container.ExtResultContainer;
import edu.ie3.simona.api.mapping.DataType;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import edu.ie3.simona.api.ontology.em.*;
import edu.ie3.simona.api.simulation.ExtCoSimulation;
import edu.ie3.simosaik.initialization.InitialisationData;
import edu.ie3.simosaik.utils.SimosaikUtils;
import java.util.*;
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

  protected final MosaikSimulator mosaikSimulator; // extends Simulator in Mosaik

  // connections
  private final ExtPrimaryDataConnection extPrimaryDataConnection;
  private final ExtEmDataConnection extEmDataConnection;
  private final ExtResultDataConnection
      extResultDataConnection; // TODO: Check if we can switch to ResultListener

  public MosaikSimulation(String mosaikIP, MosaikSimulator simulator) {
    this("MosaikSimulation", mosaikIP, simulator);
  }

  public MosaikSimulation(String name, String mosaikIP, MosaikSimulator simulator) {
    super(name, simulator.getSimName());

    this.mosaikSimulator = simulator;
    mosaikSimulator.setConnectionToSimonaApi(queueToSimona, queueToExt);
    SimosaikUtils.startMosaikSimulation(mosaikSimulator, mosaikIP);

    try {
      var initData = simulator.initDataQueue.take(InitialisationData.FlexInitData.class);

      this.stepSize = initData.stepSize();
      this.disaggregateFlex = initData.disaggregate();

      ExtEntityMapping entityMapping =
          simulator.initDataQueue.take(InitialisationData.MappingData.class).mapping();

      // primary data connection
      Map<UUID, Class<? extends Value>> primaryInput =
          SimosaikUtils.buildAssetsToValueClasses(entityMapping);

      this.extPrimaryDataConnection =
          !primaryInput.isEmpty() ? buildPrimaryConnection(primaryInput, log) : null;

      // em data connection
      Optional<EmMode> mode = SimosaikUtils.findEmMode(entityMapping.getDataTypes());

      if (mode.isPresent()) {
        List<UUID> controlledEms = SimosaikUtils.buildEmData(entityMapping);

        this.extEmDataConnection = buildEmConnection(controlledEms, mode.get(), log);
      } else {
        this.extEmDataConnection = null;
      }

      // result data connection
      Map<DataType, List<UUID>> resultInput = SimosaikUtils.buildResultMapping(entityMapping);
      this.extResultDataConnection =
          !resultInput.isEmpty() ? buildResultConnection(resultInput, log) : null;

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
    log.info(
        "+++++++++++++++++++++++++++ Initialization of the external simulation completed +++++++++++++++++++++++++++");
    return 0L;
  }

  @Override
  protected final Optional<Long> doActivity(long tick) {
    log.info(
        "+++++++++++++++++++++++++++ Activities in External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++",
        tick);
    try {
      Thread.sleep(500);

      long nextTick = tick + stepSize;
      return activity(tick, nextTick);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  protected Optional<Long> activity(long tick, long nextTick) throws InterruptedException {
    Optional<Long> maybeNextTick = Optional.of(nextTick);

    if (extPrimaryDataConnection != null) {
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
        case EM_COMMUNICATION -> useFlexCommunication(extEmDataConnection, tick);

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

      long extTick = queueToSimona.takeData(ExtInputContainer::getTick);

      log.info("Current simulator tick: {}, SIMONA tick: {}", extTick, tick);

      if (tick == extTick) {
        ExtInputContainer container = queueToSimona.takeContainer();

        log.info("Flex requests: {}", container.flexRequestsString());
        log.info("Flex options: {}", container.flexOptionsString());
        log.info("Set points: {}", container.setPointsString());

        // send received data to SIMONA
        var requests = container.extractFlexRequests();
        var options = container.extractFlexOptions();
        var setPoints = container.extractSetPoints();

        if (!requests.isEmpty())
          extEmDataConnection.sendFlexRequests(tick, requests, maybeNextTick, log);

        if (!options.isEmpty())
          extEmDataConnection.sendFlexOptions(tick, options, maybeNextTick, log);

        if (!setPoints.isEmpty())
          extEmDataConnection.sendSetPoints(tick, setPoints, maybeNextTick, log);

        log.debug("Unhandled flex requests: {}", container.flexRequestsString());
        log.debug("Unhandled flex options: {}", container.flexOptionsString());
        log.debug("Unhandled set points: {}", container.setPointsString());

        if (requests.isEmpty() && options.isEmpty() && setPoints.isEmpty()) {
          log.info("Requesting a service completion for tick: {}.", tick);
          extEmDataConnection.requestCompletion(tick);
        }

      } else {
        notFinished = false;

        log.info("External simulator finished tick {}. Request completion.", tick);
        extEmDataConnection.requestCompletion(tick);
      }

      EmDataResponseMessageToExt received = extEmDataConnection.receiveAny();

      Map<UUID, ResultEntity> results = new HashMap<>();

      if (received instanceof EmCompletion completion) {
        notFinished = false;
        maybeNextTick = completion.maybeNextTick();

        log.info("Finished for tick: {}. Next tick option: {}", tick, maybeNextTick);

      } else if (received instanceof FlexRequestResponse flexRequestResponse) {
        results.putAll(flexRequestResponse.flexRequests());

      } else if (received instanceof FlexOptionsResponse flexOptionsResponse) {
        results.putAll(flexOptionsResponse.receiverToFlexOptions());

      } else if (received instanceof EmSetPointDataResponse setPointDataResponse) {
        results.putAll(setPointDataResponse.emData());

      } else {
        log.warn("Received unsupported data response: {}", received);
      }

      log.warn("Results to ext: {}", results);

      ExtResultContainer resultContainer = new ExtResultContainer(tick, results, maybeNextTick);

      queueToExt.queueData(resultContainer);
    }

    return maybeNextTick;
  }
}
