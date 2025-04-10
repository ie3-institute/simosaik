/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import edu.ie3.simona.api.data.container.ExtInputDataContainer;
import edu.ie3.simona.api.data.mapping.ExtEntityMapping;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputUtils {
  private static final Logger log = LoggerFactory.getLogger(InputUtils.class);

  public static ExtInputDataContainer createInputDataContainer(
      long tick,
      long nextTick,
      List<MosaikMessageParser.MosaikMessage> mosaikMessages,
      ExtEntityMapping mapping) {
    log.info("Parsed messages: {}", mosaikMessages);

    ExtInputDataContainer container = new ExtInputDataContainer(tick, nextTick);
    Map<String, UUID> idToUuid = mapping.getFullMapping();

    // primary data
    PrimaryUtils.getPrimary(mosaikMessages, idToUuid).forEach(container::addPrimaryValue);

    // em data
    FlexUtils.getFlexRequests(mosaikMessages, idToUuid).forEach(container::addRequest);
    FlexUtils.getFlexOptions(mosaikMessages, idToUuid).forEach(container::addFlexOptions);
    FlexUtils.getSetPoint(mosaikMessages, idToUuid).forEach(container::addSetPoint);

    return container;
  }
}
