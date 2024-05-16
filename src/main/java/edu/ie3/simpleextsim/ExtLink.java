package edu.ie3.simpleextsim;

import edu.ie3.simona.api.ExtLinkInterface;
import edu.ie3.simona.api.data.ExtDataSimulation;
import edu.ie3.simona.api.simulation.ExtSimulation;

import java.util.List;

public class ExtLink implements ExtLinkInterface {
    private final SimpleExtSimulation simpleExtSim = new SimpleExtSimulation();

    @Override
    public ExtSimulation getExtSimulation() {
        return simpleExtSim;
    }

    @Override
    public List<ExtDataSimulation> getExtDataSimulations() {
        return simpleExtSim.getDataConnections();
    }
}
