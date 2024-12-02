/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.mosaik;

import edu.ie3.datamodel.utils.TriFunction;
import edu.ie3.simona.api.data.ExtInputDataContainer;
import java.util.Map;

@FunctionalInterface
public interface InputWrapperFunction
    extends TriFunction<Long, Map<String, Object>, Long, ExtInputDataContainer> {}
