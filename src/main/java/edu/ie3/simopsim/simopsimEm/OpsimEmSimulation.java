package edu.ie3.simopsim.simopsimEm;

import edu.ie3.simona.api.data.ExtData;
import edu.ie3.simona.api.simulation.ExtSimulation;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class OpsimEmSimulation extends ExtSimulation {

    public OpsimEmSimulation(
            String urlToOpsim,
            Path mappingPath
    ) {
    }

    @Override
    public List<ExtData> getDataConnections() {
        return List.of();
    }

    @Override
    protected Long initialize() {
        return 0L;
    }

    @Override
    protected Optional<Long> doActivity(long tick) {
        return Optional.of(0L);
    }
}
