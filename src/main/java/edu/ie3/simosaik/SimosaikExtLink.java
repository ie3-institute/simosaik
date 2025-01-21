/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.simona.api.ExtLinkInterface;
import edu.ie3.simona.api.simulation.ExtSimAdapterData;
import edu.ie3.simosaik.config.ArgsParser;
import edu.ie3.simosaik.config.SimosaikConfig;
import edu.ie3.simosaik.mosaik.DefaultPrimaryResultSimulator;
import edu.ie3.simosaik.mosaik.MosaikSimulator;

public class SimosaikExtLink implements ExtLinkInterface {
  private MosaikSimulation extSim;

  @Override
  public MosaikSimulation getExtSimulation() {
    return extSim;
  }

  @Override
  public void setup(ExtSimAdapterData data) {
    ArgsParser.Arguments arguments = ArgsParser.parse(data.getMainArgs());
    SimosaikConfig config = arguments.config();

    MosaikSimulator simulator =
        switch (config.simulator().orElse("")) {
          default -> new DefaultPrimaryResultSimulator();
        };

    extSim = new MosaikSimulation(arguments.mosaikIP(), config, simulator);
    extSim.setAdapterData(data);
  }
}
