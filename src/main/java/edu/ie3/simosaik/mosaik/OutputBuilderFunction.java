/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.mosaik;

import edu.ie3.simona.api.data.results.ExtResultContainer;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@FunctionalInterface
public interface OutputBuilderFunction
    extends BiFunction<Map<String, List<String>>, ExtResultContainer, Map<String, Object>> {}
