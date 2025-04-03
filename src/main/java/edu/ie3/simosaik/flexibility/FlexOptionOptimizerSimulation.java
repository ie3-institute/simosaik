/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.flexibility;

import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.em.EmMode;
import edu.ie3.simona.api.data.em.ExtEmDataConnection;
import edu.ie3.simona.api.data.results.ExtResultDataConnection;
import edu.ie3.simosaik.MosaikSimulation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class FlexOptionOptimizerSimulation extends MosaikSimulation {

  private final ExtEmDataConnection extEmDataConnection;
  private final ExtResultDataConnection extResultDataConnection;

  public FlexOptionOptimizerSimulation(
      String mosaikIP, int stepSize, boolean useFlexOptionEntitiesInsteadOfEmAgents) {
    super(
        "MosaikOptimizerSimulation",
        mosaikIP,
        new FlexOptionOptimizerSimulator(stepSize, useFlexOptionEntitiesInsteadOfEmAgents));

    // set up connection
    this.extEmDataConnection = buildEmConnection(List.of(), EmMode.EM_OPTIMIZATION, log);
    this.extResultDataConnection = buildResultConnection(Map.of(), log);
  }

  @Override
  public Set<ExtDataConnection> getDataConnections() {
    return Set.of(extEmDataConnection, extResultDataConnection);
  }

  @Override
  protected Optional<Long> activity(long tick, long nextTick) throws InterruptedException {
    Optional<Long> maybeNextTick = Optional.of(nextTick);

    // sending flexibility options to external mosaik
    sendFlexOptionResultsToExt(extResultDataConnection, tick, maybeNextTick, log);

    // sending energy management set-points to SIMONA
    sendEmSetPointsToSimona(extEmDataConnection, tick, maybeNextTick, log);

    // sending grid results to mosaik
    sendGridResultsToExt(extResultDataConnection, tick, maybeNextTick, log);

    return maybeNextTick;
  }
}
