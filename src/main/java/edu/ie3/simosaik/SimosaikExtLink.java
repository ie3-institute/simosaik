/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.simona.api.ExtLinkInterface;
import edu.ie3.simona.api.simulation.ExtSimAdapterData;
import edu.ie3.simosaik.config.ArgsParser;
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

    int stepSize = 900;
    MosaikSimulator simulator = new DefaultPrimaryResultSimulator(stepSize);

    extSim = new MosaikSimulation(arguments.mosaikIP(), arguments.config(), simulator);
    extSim.setAdapterData(data);
  }
}
