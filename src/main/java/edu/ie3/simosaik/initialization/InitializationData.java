/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.initialization;

import edu.ie3.simona.api.mapping.ExtEntityMapping;

public interface InitializationData {

  record SimulatorData(long stepSize, boolean disaggregate) implements InitializationData {}

  record ModelData(ExtEntityMapping mapping) implements InitializationData {}
}
