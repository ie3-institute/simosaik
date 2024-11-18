/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import de.offis.mosaik.api.SimProcess;
import de.offis.mosaik.api.Simulator;
import java.util.logging.Logger;

public abstract class SimonaSimulator extends Simulator {
  protected final Logger logger = SimProcess.logger;

  public SimonaSimulator(String simName) {
    super(simName);
  }
}
