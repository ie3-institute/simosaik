/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.simona.api.ExtLinkInterface;
import edu.ie3.simona.api.simulation.ExtSimulation;
import edu.ie3.simosaik.simosaikElectrolyzer.MosaikElectrolyzerSimulation;

public class ExtLink implements ExtLinkInterface {
  private ExtSimulation extSim;

  @Override
  public ExtSimulation getExtSimulation() {
    return extSim;
  }

  @Override
  public void setup(String[] mainArgs) {
    extSim = new MosaikElectrolyzerSimulation(mainArgs);
  }
}
