/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.initialization;

import edu.ie3.simona.api.data.connection.ExtEmDataConnection;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import edu.ie3.simona.api.simulation.ExtCoSimFramework;

import java.util.Optional;

/** Data send to SIMONA by mosaik. This data is necessary to initialize the external simulation. */
public interface InitializationData extends ExtCoSimFramework.InitData {

  record TickInformation(long stepSize, long lastTick) implements InitializationData {}

  /**
   * Simulator data that is use.
   *
   * @param emMode option for the mode of an external em data connection
   */
  record SimulatorData(
      boolean sendResults,
      boolean sendUnchangedResults,
      boolean debugFlag,
      Optional<ExtEmDataConnection.EmMode> emMode)
      implements InitializationData {}

  /**
   * Model data that is used.
   *
   * @param mapping the final eternal entity mapping
   */
  record ModelData(ExtEntityMapping mapping) implements InitializationData {}
}
