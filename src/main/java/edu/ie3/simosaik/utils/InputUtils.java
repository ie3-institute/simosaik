/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import edu.ie3.simona.api.data.datacontainer.ExtInputDataContainer;
import edu.ie3.simona.api.simulation.mapping.DataType;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputUtils {
  private static final Logger log = LoggerFactory.getLogger(InputUtils.class);

  public static ExtInputDataContainer createInputDataContainer(
      long tick, long nextTick, Map<String, Object> inputs, ExtEntityMapping mapping) {
    return createInputDataContainer(tick, nextTick, MosaikMessageParser.parse(inputs), mapping);
  }

  public static ExtInputDataContainer createInputDataContainer(
      long tick,
      long nextTick,
      List<MosaikMessageParser.MosaikMessage> mosaikMessages,
      ExtEntityMapping mapping) {
    log.info("Parsed messages: {}", mosaikMessages);

    ExtInputDataContainer container = new ExtInputDataContainer(tick, nextTick);

    // primary data
    Map<String, UUID> primaryMapping = mapping.getExtId2UuidMapping(DataType.EXT_PRIMARY_INPUT);
    PrimaryUtils.getPrimary(mosaikMessages, primaryMapping).forEach(container::addPrimaryValue);

    // em data
    Map<String, UUID> idToUuid = mapping.getExtId2UuidMapping(DataType.EXT_EM_INPUT);
    FlexUtils.getFlexRequests(mosaikMessages, idToUuid).forEach(container::addRequest);
    FlexUtils.getFlexOptions(mosaikMessages, idToUuid).forEach(container::addFlexOptions);
    FlexUtils.getSetPoint(mosaikMessages, idToUuid).forEach(container::addSetPoint);

    return container;
  }
}
