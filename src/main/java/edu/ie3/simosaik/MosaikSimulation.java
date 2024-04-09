package edu.ie3.simosaik;

import edu.ie3.simona.api.data.ExtDataSimulation;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryData;
import edu.ie3.simona.api.data.results.ExtResultData;
import edu.ie3.simona.api.simulation.ExtSimulation;

import java.util.Optional;

public class MosaikSimulation extends ExtSimulation implements ExtDataSimulation {

    public SimonaSimulator simonaSimulatorInMosaik;

    public ExtPrimaryData extPrimaryData;
    public ExtResultData extResultsData;

    @Override
    protected Long initialize() {
        return 0L;
    }

    @Override
    protected Optional<Long> doPreActivity(long tick) {
        return Optional.empty();
    }

    @Override
    protected Optional<Long> doPostActivity(long tick) {
        return Optional.empty();
    }
}
