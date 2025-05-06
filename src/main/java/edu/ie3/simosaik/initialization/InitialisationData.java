/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.initialization;

import edu.ie3.simona.api.data.mapping.ExtEntityMapping;

public interface InitialisationData {

  record FlexInitData(boolean disaggregate) implements InitialisationData {}

  record MappingData(ExtEntityMapping mapping) implements InitialisationData {}
}
