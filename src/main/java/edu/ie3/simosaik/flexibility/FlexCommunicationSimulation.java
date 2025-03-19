/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.flexibility;

import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.em.ExtEmDataConnection;
import edu.ie3.simosaik.MosaikSimulation;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

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
