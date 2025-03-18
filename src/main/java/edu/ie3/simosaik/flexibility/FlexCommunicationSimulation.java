/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.flexibility;

import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.ExtInputDataContainer;
import edu.ie3.simona.api.data.em.ExtEmDataConnection;
import edu.ie3.simona.api.data.em.model.FlexOptionRequestValue;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simosaik.MosaikSimulation;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static edu.ie3.simosaik.SimosaikTranslation.FLEX_REQUEST;

public class FlexCommunicationSimulation extends MosaikSimulation {

  private final ExtEmDataConnection extEmDataConnection;

  public FlexCommunicationSimulation(String mosaikIP, Path mappingPath, int stepSize) {
    super(
        "FlexCommunicationSimulation",
        mosaikIP,
        mappingPath,
        new FlexCommunicationSimulator(stepSize));

    // set up connection
    this.extEmDataConnection = buildEmConnection(mapping, log);
  }

  @Override
  public Set<ExtDataConnection> getDataConnections() {
    return Set.of(extEmDataConnection);
  }

  @Override
  protected Optional<Long> activity(long tick, long nextTick) throws InterruptedException {
    Optional<Long> maybeNextTick = Optional.of(nextTick);

    ExtInputDataContainer container = dataQueueExtCoSimulatorToSimonaApi.takeData();

    FlexOptionRequestValue value = (FlexOptionRequestValue) container.getSimonaInputMap().get(FLEX_REQUEST);

    var list = ExtEntityMapping.toSimona(value.emEntities(), extEmDataConnection.simonaMapping);

    log.info("Request flex options for: {}", list);
    extEmDataConnection.requestEmFlexResults(tick, list);


    // sending flex options to external
    sendEmFlexResultsToExt(extEmDataConnection, tick, maybeNextTick, log);

    // sending flex options to superior em agents
    sendEmFlexOptionsToSimona(extEmDataConnection, tick, maybeNextTick, log);

    // send energy management set-points to external
    sendEmSetPointsToExt(extEmDataConnection, tick, maybeNextTick, log);

    // send energy management set-points to SIMONA
    sendEmSetPointsToSimona(extEmDataConnection, tick, maybeNextTick, log);

    return maybeNextTick;
  }
}
