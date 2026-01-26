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
import edu.ie3.simona.api.ontology.em.*;
import edu.ie3.simona.api.simulation.ExtCoSimulation;
import edu.ie3.simosaik.initialization.InitializationData;
import edu.ie3.simosaik.synchronization.SIMONAPart;
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
  protected final boolean sendUnchangedResults;

  private final SIMONAPart synchronizer;
  public boolean run = true;

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
      this.sendUnchangedResults = initData.sendUnchangedResults();

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

      if (initData.sendResults()) {
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
        "+++++++++++++++++++++++++++ Initialization of the external simulation +++++++++++++++++++++++++++");

    synchronizer.setDataQueues(queueToSimona, queueToExt);

    if (extPrimaryDataConnection != null) {
      log.warn("Primary assets: {}", extPrimaryDataConnection.getPrimaryDataAssets());
    }

    if (extEmDataConnection != null) {
      log.warn("EM mode: {}", extEmDataConnection.mode);
    }

    if (extResultDataConnection != null) {
      log.warn("Result assets: {}", extResultDataConnection.getResultUuids());
    }

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

      if (tick % stepSize == 0) {
        synchronizer.updateNextTickSIMONA(nextTick);
      }

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
    Set<ResultEntity> cache = new HashSet<>();

    while (run) {
      Map<UUID, List<ResultEntity>> resultsToBeSend = new HashMap<>();
      Map<UUID, List<EmData>> emDataFromSIMONA = new HashMap<>();
      boolean sendAnyway = false;

      Optional<ExtInputContainer> inputOption = getInputs(tick);

      long extTick = synchronizer.currentMosaikTick();
      log.info("Current simulator tick: {}, SIMONA tick: {}", extTick, tick);

      if (extTick == tick && inputOption.isPresent()) {
        ExtInputContainer input = inputOption.get();

        if (input.isEmpty()) {
          log.warn("Received no input data from Mosaik!");
          sendAnyway = true;

        } else {

          if (extPrimaryDataConnection != null && input.hasPrimaryData()) {
            extPrimaryDataConnection.sendPrimaryData(
                tick, input.extractPrimaryData(), input.getMaybeNextTick(), log);
          }

          if (extEmDataConnection != null && input.hasEmData()) {
            // send received data to SIMONA
            var requests = input.extractFlexRequests();
            var options = input.extractFlexOptions();
            var setPoints = input.extractSetPoints();
            var emMessages = input.extractEmMessages();

            boolean sentEmData = extEmDataConnection.sendEmData(tick, requests, options, setPoints);
            boolean sentEmComData = extEmDataConnection.sendCommunicationMessage(tick, emMessages);

            if (sentEmData || sentEmComData) {
              log.info("Sending em messages: {}", emMessages);
              log.info("Sending em data: {}, {}, {}", requests, options, setPoints);
            } else {
              log.info("Requesting a service completion for tick: {}.", tick);
              extEmDataConnection.requestCompletion(tick, extTick);
            }

            EmDataResponseMessageToExt received = extEmDataConnection.receiveAny();

            switch (received) {
              case EmCompletion(Optional<Long> nextEmTick) -> {
                if (nextEmTick.isPresent()) {
                  if (nextEmTick.get() < nextTick) {
                    maybeNextTick = nextEmTick;
                  }

                  synchronizer.updateNextTickSIMONA(maybeNextTick);
                }

                sendAnyway = true;

                log.info(
                    "Received completion for tick: {}. Next tick option: {}", tick, maybeNextTick);
              }
              case EmResultResponse(Map<UUID, List<EmData>> emResults) -> {
                emDataFromSIMONA.putAll(emResults);

                // log.info("Received em results: {}", emResults);

                if (emDataFromSIMONA.isEmpty()) {
                  sendAnyway = true;
                }
              }
              case FlexOptionsResponse(Map<UUID, List<FlexOptions>> receiverToFlexOptions) ->
                  receiverToFlexOptions.forEach(
                      (receiver, data) ->
                          emDataFromSIMONA
                              .computeIfAbsent(receiver, k -> new ArrayList<>())
                              .addAll(data));
              default -> log.warn("Received unsupported data response: {}", received);
            }
          }
        }
      } else if (extTick > tick) {
        log.warn("Received inputs for next tick: {}", extTick);

        // maybe unnecessary
        synchronizer.updateNextTickSIMONA(extTick);

        if (extEmDataConnection != null) {
          log.info("External simulator finished tick {}. Request completion.", tick);
          extEmDataConnection.sendExtMsg(new RequestEmCompletion(tick, Optional.of(extTick)));

          // used to empty the queue
          EmDataResponseMessageToExt msg = extEmDataConnection.receiveAny();
          if (msg instanceof EmResultResponse(Map<UUID, List<EmData>> emResults)
              && !emResults.isEmpty()) {
            log.warn(
                "Received unexpected em results after requesting completion. Results: {}",
                emResults);
          }

          maybeNextTick = Optional.of(extTick);
          log.warn("Next tick: {}", maybeNextTick);
        }
        break;
      } else if (extTick < tick) {
        log.warn("Received inputs for previous tick: {}", extTick);
        break;
      } else {
        log.warn("No inputs received!");
        break;
      }

      // handle results
      if (extResultDataConnection != null) {
        Map<UUID, List<ResultEntity>> results = extResultDataConnection.requestResults(tick);

        if (sendUnchangedResults) {
          resultsToBeSend.putAll(results);
        } else {
          results.forEach(
              (uuid, res) -> {
                List<ResultEntity> filtered = res.stream().filter(r -> !cache.contains(r)).toList();

                if (!filtered.isEmpty()) {
                  resultsToBeSend.put(uuid, filtered);

                  // update cache
                  cache.addAll(filtered);
                }
              });
        }
      }

      ExtOutputContainer container = new ExtOutputContainer(tick, maybeNextTick);
      container.addResults(resultsToBeSend);
      container.addEmData(emDataFromSIMONA);

      if (!container.isEmpty() || sendAnyway) {
        log.debug("Sending output data.");
        queueToExt.queueData(container);
      }
    }

    if (!run) {
      log.info("Mosaik is finished! The external simulation will not be activated anymore!");

      if (extEmDataConnection != null) {
        // to prevent em agents from blocking the scheduler
        extEmDataConnection.simulateInternal(tick);
        extEmDataConnection.receiveWithType(EmCompletion.class);
      }

      maybeNextTick = Optional.empty();
    }

    return maybeNextTick;
  }

  public Optional<ExtInputContainer> getInputs(long tick) {
    Optional<ExtInputContainer> container;

    try {
      do {
        container = queueToSimona.pollContainer(100, TimeUnit.MILLISECONDS);
      } while (tick == synchronizer.currentMosaikTick() && container.isEmpty() && run);

    } catch (InterruptedException e) {
      container = Optional.empty();
    }

    return container;
  }
}
