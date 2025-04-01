/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.primaryResultSimulator;

import edu.ie3.datamodel.io.naming.timeseries.ColumnScheme;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryDataConnection;
import edu.ie3.simona.api.data.results.ExtResultDataConnection;
import edu.ie3.simona.api.simulation.mapping.DataType;
import edu.ie3.simona.api.simulation.mapping.ExtEntityEntry;
import edu.ie3.simosaik.MosaikSimulation;

import java.util.*;
import java.util.stream.Collectors;

public class PrimaryResultSimulation extends MosaikSimulation {

  private final ExtPrimaryDataConnection extPrimaryDataConnection;
  private final ExtResultDataConnection extResultDataConnection;

  public PrimaryResultSimulation(String mosaikIP, PrimaryResultSimulator simulator) {
    super("MosaikPrimaryResultSimulation", mosaikIP, simulator);

    try {
      List<ExtEntityEntry> extEntityEntries = simulator.controlledQueue.take();

      Map<DataType, List<ExtEntityEntry>> grouped =
          extEntityEntries.stream().collect(Collectors.groupingBy(ExtEntityEntry::dataType));

      Map<UUID, Class<Value>> assetsToValueClasses = new HashMap<>();

      for (ExtEntityEntry extEntityEntry :
          grouped.getOrDefault(DataType.EXT_PRIMARY_INPUT, Collections.emptyList())) {
        Optional<ColumnScheme> scheme = extEntityEntry.columnScheme();

        if (scheme.isPresent()) {
          assetsToValueClasses.put(
              extEntityEntry.uuid(), (Class<Value>) scheme.get().getValueClass());
        }
      }

      Map<DataType, List<UUID>> result =
          grouped.entrySet().stream()
              .map(
                  e ->
                      Map.entry(
                          e.getKey(), e.getValue().stream().map(ExtEntityEntry::uuid).toList()))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

      // set up connection
      this.extPrimaryDataConnection = buildPrimaryConnection(assetsToValueClasses, log);
      this.extResultDataConnection = buildResultConnection(result, log);

    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Set<ExtDataConnection> getDataConnections() {
    return Set.of(extPrimaryDataConnection, extResultDataConnection);
  }

  @Override
  protected Optional<Long> activity(long tick, long nextTick) throws InterruptedException {
    Optional<Long> maybeNextTick = Optional.of(nextTick);

    // sending primary data to SIMONA
    sendPrimaryDataToSimona(extPrimaryDataConnection, tick, maybeNextTick, log);

    // sending results to mosaik
    sendResultToExt(extResultDataConnection, tick, maybeNextTick, log);

    return maybeNextTick;
  }
}
