/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.primaryResultSimulator;

import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryDataConnection;
import edu.ie3.simona.api.data.results.ExtResultDataConnection;
import edu.ie3.simosaik.MosaikSimulation;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public class PrimaryResultSimulation extends MosaikSimulation {

  private final ExtPrimaryDataConnection extPrimaryDataConnection;
  private final ExtResultDataConnection extResultDataConnection;

  public PrimaryResultSimulation(String mosaikIP, Path mappingPath) {
    super("MosaikPrimaryResultSimulation", mosaikIP, mappingPath, new PrimaryResultSimulator());

    // set up connection
    this.extPrimaryDataConnection = buildPrimaryConnection(mapping, log);
    this.extResultDataConnection = buildResultConnection(mapping, log);
  }

  @Override
  public Set<ExtDataConnection> getDataConnections() {
    return Set.of(extPrimaryDataConnection, extResultDataConnection);
  }

  @Override
  protected Optional<Long> activity(long tick, long nextTick) throws InterruptedException {
    Optional<Long> maybeNextTick = Optional.of(nextTick);

    sendPrimaryDataToSimona(extPrimaryDataConnection, tick, maybeNextTick, log);
    sendResultToExt(extResultDataConnection, tick, maybeNextTick, log);

    return maybeNextTick;
  }
}
