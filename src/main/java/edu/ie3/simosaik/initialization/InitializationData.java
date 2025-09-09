/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.initialization;

import edu.ie3.simona.api.data.connection.ExtEmDataConnection;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import java.util.Optional;

/** Data send to SIMONA by mosaik. This data is necessary to initialize the external simulation. */
public interface InitializationData {

  /**
   * Simulator data that is use.
   *
   * @param stepSize regular step size of the data provision.
   * @param disaggregate true, if disaggregated flex options should be requested.
   * @param emMode option for the mode of an external em data connection
   */
  record SimulatorData(
      long stepSize, boolean disaggregate, Optional<ExtEmDataConnection.EmMode> emMode)
      implements InitializationData {}

  /**
   * Model data that is used.
   *
   * @param mapping the final eternal entity mapping
   */
  record ModelData(ExtEntityMapping mapping) implements InitializationData {}
}
