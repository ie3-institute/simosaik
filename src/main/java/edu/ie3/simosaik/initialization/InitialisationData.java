/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.initialization;

import edu.ie3.simona.api.mapping.ExtEntityMapping;
import java.util.Optional;
import javax.measure.quantity.Time;
import tech.units.indriya.ComparableQuantity;

public interface InitialisationData {

  record SimulatorData(long stepSize, boolean disaggregate) implements InitialisationData {}

  record ModelData(ExtEntityMapping mapping, Optional<ComparableQuantity<Time>> maxDelay)
      implements InitialisationData {}
}
