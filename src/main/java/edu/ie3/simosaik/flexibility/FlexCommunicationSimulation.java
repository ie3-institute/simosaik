/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.flexibility;

import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.datacontainer.ExtInputDataContainer;
import edu.ie3.simona.api.data.datacontainer.ExtResultContainer;
import edu.ie3.simona.api.data.em.ExtEmDataConnection;
import edu.ie3.simona.api.data.em.ontology.*;
import edu.ie3.simona.api.simulation.mapping.DataType;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simosaik.MosaikSimulation;
import java.nio.file.Path;
import java.util.*;

public class FlexCommunicationSimulation extends MosaikSimulation {

  private final ExtEmDataConnection extEmDataConnection;
  private final Map<UUID, String> map;

  public FlexCommunicationSimulation(String mosaikIP, Path mappingPath, int stepSize) {
    super(
        "FlexCommunicationSimulation",
        mosaikIP,
        mappingPath,
        new FlexCommunicationSimulator(stepSize));

    this.map = mapping.getExtUuid2IdMapping(DataType.EXT_EM_INPUT);

    // set up connection
    this.extEmDataConnection = buildEmConnection(mapping, true, log);
  }

  @Override
  public Set<ExtDataConnection> getDataConnections() {
    return Set.of(extEmDataConnection);
  }

  @Override
  protected Optional<Long> activity(long tick, long nextTick) throws InterruptedException {
    Optional<Long> maybeNextTick = Optional.of(nextTick);

    flexCommunication(tick, maybeNextTick);

    /*
    // sending flex options to external
    sendEmFlexResultsToExt(extEmDataConnection, tick, maybeNextTick, log);

    // sending flex options to superior em agents
    sendEmFlexOptionsToSimona(extEmDataConnection, tick, maybeNextTick, log);

    // send energy management set-points to external
    sendEmSetPointsToExt(extEmDataConnection, tick, maybeNextTick, log);

    // send energy management set-points to SIMONA
    sendEmSetPointsToSimona(extEmDataConnection, tick, maybeNextTick, log);
     */

    return maybeNextTick;
  }

  public void flexCommunication(long tick, Optional<Long> maybeNextTick)
      throws InterruptedException {

    // handle flex requests
    boolean notFinished = true;

    while (notFinished) {

      long extTick = queueToSimona.takeData(ExtInputDataContainer::getTick);

      log.warn("Current simulator tick: {}, SIMONA tick: {}", extTick, tick);

      if (tick == extTick) {
        ExtInputDataContainer container = queueToSimona.takeAll();

        log.warn("Flex requests: {}", container.flexRequestsString());
        log.warn("Flex options: {}", container.flexOptionsString());
        log.warn("Set points: {}", container.setPointsString());

        // send received data to SIMONA
        var requests = container.extractFlexRequests();
        var options = container.extractFlexOptions();
        var setPoints = container.extractSetPoints();

        extEmDataConnection.convertAndSendFlexRequests(
                tick, requests, maybeNextTick, log);

        extEmDataConnection.convertAndSendFlexOptions(
                tick, options, maybeNextTick, log);

        extEmDataConnection.convertAndSendSetPoints(
                tick, setPoints, maybeNextTick, log);

        log.warn("Unhandled flex requests: {}", container.flexRequestsString());
        log.warn("Unhandled flex options: {}", container.flexOptionsString());
        log.warn("Unhandled set points: {}", container.setPointsString());


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

      if (received instanceof EmCompletion) {
        notFinished = false;
        log.info("Finished for tick: {}", tick);

      } else if (received instanceof FlexRequestResponse flexRequestResponse) {
        results.putAll(flexRequestResponse.flexRequests());

      } else if (received instanceof FlexOptionsResponse flexOptionsResponse) {
        results.putAll(flexOptionsResponse.flexOptions());

      } else if (received instanceof EmSetPointDataResponse setPointDataResponse) {
        results.putAll(setPointDataResponse.emData());

      } else {
        log.warn("Received unsupported data response: {}", received);
      }

      log.warn("Results to ext: {}", results);

      ExtResultContainer resultContainer =
          new ExtResultContainer(tick, ExtEntityMapping.mapToExt(results, map), maybeNextTick);

      queueToExt.queueData(resultContainer);
    }
  }
}
