/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.simona.api.ExtLinkInterface;
import edu.ie3.simona.api.simulation.ExtSimAdapterData;
import edu.ie3.simosaik.synchronisation.Synchronizer;
import edu.ie3.simosaik.utils.SimosaikUtils;

public final class SimosaikExtLink implements ExtLinkInterface {
  private MosaikSimulation extSim;

  @Override
  public MosaikSimulation getExtSimulation() {
    return extSim;
  }

  @Override
  public void setup(ExtSimAdapterData data) {
    ArgsParser.Arguments arguments = ArgsParser.parse(data.getMainArgs());

    String mosaikIP = arguments.mosaikIP();

    // for synchronising both simulations
    Synchronizer synchronizer = new Synchronizer();

    // creating and starting the simulator
    MosaikSimulator simulator = new MosaikSimulator(synchronizer);
    SimosaikUtils.startMosaikSimulation(simulator, mosaikIP);

    // creating the external simulation
    extSim = new MosaikSimulation(synchronizer);
    extSim.setAdapterData(data);
  }
}
