/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.simosaikFlexOptionOptimizer;

import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.ExtInputDataContainer;
import edu.ie3.simona.api.data.em.ExtEmDataConnection;
import edu.ie3.simona.api.data.results.ExtResultDataConnection;
import edu.ie3.simosaik.MosaikSimulation;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MosaikOptimizerSimulation extends MosaikSimulation {

  private final ExtEmDataConnection extEmDataConnection;
  private final ExtResultDataConnection extResultDataConnection;

  public MosaikOptimizerSimulation(String mosaikIP, Path mappingPath) {
    super("MosaikOptimizerSimulation", mosaikIP, mappingPath, new SimonaOptimizerSimulator());

    // set up connection
    this.extEmDataConnection = buildEmConnection(mapping, log);
    this.extResultDataConnection = buildResultConnection(mapping, log);
  }

  @Override
  public Set<ExtDataConnection> getDataConnections() {
    return Set.of(extEmDataConnection, extResultDataConnection);
  }

  @Override
  protected Optional<Long> activity(long tick, long nextTick) throws InterruptedException {
    sendDataToExt(extResultDataConnection, tick, Optional.of(nextTick), log);

    ExtInputDataContainer rawEmData = mosaikSimulator.dataQueueMosaikToSimona.takeData();
    if (rawEmData.getTick() != tick) {
      throw new RuntimeException(String.format("Mosaik provided input data for tick %d, but SIMONA expects input data for tick %d", rawEmData.getTick(), tick));
    }
    Map<String, Value> inputMap = rawEmData.getSimonaInputMap();

    sendEmDataToSimona(extEmDataConnection, tick, inputMap, Optional.of(nextTick), log);

    sendDataToExt(extResultDataConnection, tick, Optional.of(nextTick), log);

    return Optional.of(nextTick);
  }
}
