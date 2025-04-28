/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.SimosaikUnits.ACTIVE_POWER;
import static edu.ie3.simosaik.SimosaikUnits.REACTIVE_POWER;
import static edu.ie3.simosaik.utils.SimosaikUtils.*;

import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.container.ExtInputDataContainer;
import edu.ie3.simona.api.data.em.model.FlexOptions;
import edu.ie3.simona.api.data.mapping.DataType;
import edu.ie3.simona.api.data.mapping.ExtEntityMapping;
import edu.ie3.simosaik.utils.MosaikMessageParser.*;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InputUtils {
  private static final Logger log = LoggerFactory.getLogger(InputUtils.class);

  public static ExtInputDataContainer createInputDataContainer(
      long tick, long nextTick, List<ParsedMessage> mosaikMessages, ExtEntityMapping mapping) {
    log.info("Parsed messages: {}", mosaikMessages);

    Map<String, List<Content>> receiverToMessages = new HashMap<>();

    mosaikMessages.stream()
        .collect(Collectors.groupingBy(ParsedMessage::receiver))
        .forEach(
            (receiver, messages) ->
                receiverToMessages.put(
                    receiver, messages.stream().map(ParsedMessage::content).toList()));

    log.info("Receivers to messages: {}.", receiverToMessages);

    // building input container
    ExtInputDataContainer container = new ExtInputDataContainer(tick, nextTick);

    // primary data
    parsePrimary(receiverToMessages, mapping.getExtId2UuidMapping(DataType.EXT_PRIMARY_INPUT))
        .forEach(container::addPrimaryValue);

    // em data
    Map<String, UUID> emIdToUuid =
        mapping.getExtId2UuidMapping(
            DataType.EXT_EM_INPUT, DataType.EXT_EM_COMMUNICATION, DataType.EXT_EM_OPTIMIZER);
    parseFlexRequests(receiverToMessages, emIdToUuid).forEach(container::addRequest);
    parseFlexOptions(receiverToMessages, emIdToUuid).forEach(container::addFlexOptions);
    parseSetPoints(receiverToMessages, emIdToUuid).forEach(container::addSetPoint);

    log.warn("Primary: {}", container.primaryDataString());
    log.warn("Set points: {}", container.setPointsString());

    return container;
  }

  public static Map<UUID, Value> parsePrimary(
      Map<String, List<Content>> receiverToMessages, Map<String, UUID> idToUuid) {
    if (idToUuid.isEmpty()) {
      log.warn("No external entity mapping found!");
      return Collections.emptyMap();
    }

    Map<UUID, Value> result = new HashMap<>();

    receiverToMessages.forEach(
        (receiver, messages) -> {
          UUID receiverUuid = idToUuid.get(receiver);

          List<DoubleValue> dvs =
              messages.stream()
                  .filter(msg -> msg.getClass() == DoubleValue.class)
                  .map(DoubleValue.class::cast)
                  .toList();

          Map<String, Double> attrToDouble = new HashMap<>();

          for (DoubleValue dv : dvs) {
            String attr = dv.attr();
            double d = dv.value();

            if (attrToDouble.containsKey(attr)) {
              double sum = attrToDouble.get(attr) + d;
              attrToDouble.put(attr, sum);
            } else {
              attrToDouble.put(attr, d);
            }
          }

          List<Value> values = convert(attrToDouble);

          if (values.isEmpty()) {
            log.warn("No primary value found for asset {}.", receiver);

          } else {
            if (values.size() > 1) {
              log.warn(
                  "Unexpected number of primary values for asset '{}'. Only the first one is considered",
                  receiver);
            }

            result.put(receiverUuid, values.get(0));
          }
        });

    return result;
  }

  public static Map<UUID, Optional<UUID>> parseFlexRequests(
      Map<String, List<Content>> receiverToMessages, Map<String, UUID> idToUuid) {
    if (idToUuid.isEmpty()) {
      log.warn("No external entity mapping found!");
      return Collections.emptyMap();
    }

    Map<UUID, Optional<UUID>> flexRequests = new HashMap<>();

    receiverToMessages.forEach(
        (receiver, messages) -> {
          if (idToUuid.containsKey(receiver)) {
            UUID receiverUuid = idToUuid.get(receiver);

            List<FlexRequestMessage> requests =
                messages.stream()
                    .filter(m -> m.getClass() == FlexRequestMessage.class)
                    .map(FlexRequestMessage.class::cast)
                    .toList();

            if (requests.size() > 1) {
              log.warn(
                  "Receiver '{}' received flex requests from multiple senders! This should not be possible!",
                  receiverUuid);
            }

            if (!requests.isEmpty()) {
              flexRequests.put(receiverUuid, requests.get(0).sender().map(idToUuid::get));
            }
          }
        });

    return flexRequests;
  }

  public static Map<UUID, List<FlexOptions>> parseFlexOptions(
      Map<String, List<Content>> receiverToMessages, Map<String, UUID> idToUuid) {
    if (idToUuid.isEmpty()) {
      log.warn("No external entity mapping found!");
      return Collections.emptyMap();
    }

    Map<UUID, List<FlexOptions>> flexOptions = new HashMap<>();

    receiverToMessages.forEach(
        (receiver, messages) -> {
          if (idToUuid.containsKey(receiver)) {

            UUID receiverUuid = idToUuid.get(receiver);

            List<FlexOptions> options =
                messages.stream()
                    .filter(m -> m.getClass() == FlexOptionsMessage.class)
                    .map(FlexOptionsMessage.class::cast)
                    .flatMap(m -> m.information().stream())
                    .map(
                        optionMessage ->
                            new FlexOptions(
                                receiverUuid,
                                idToUuid.get(optionMessage.sender()),
                                optionMessage.pMin(),
                                optionMessage.pRef(),
                                optionMessage.pMax()))
                    .toList();

            if (!options.isEmpty()) {
              flexOptions.put(receiverUuid, options);
            }
          }
        });

    return flexOptions;
  }

  public static Map<UUID, PValue> parseSetPoints(
      Map<String, List<Content>> receiverToMessages, Map<String, UUID> idToUuid) {
    if (idToUuid.isEmpty()) {
      log.warn("No external entity mapping found!");
      return Collections.emptyMap();
    }

    Map<UUID, PValue> setPoints = new HashMap<>();

    receiverToMessages.forEach(
        (receiver, messages) -> {
          if (idToUuid.containsKey(receiver)) {

            UUID receiverUuid = idToUuid.get(receiver);

            List<FlexSetPointMessage> setPointValues =
                messages.stream()
                    .filter(m -> m.getClass() == FlexSetPointMessage.class)
                    .map(FlexSetPointMessage.class::cast)
                    .toList();

            if (!setPointValues.isEmpty()) {

              if (setPointValues.size() > 1) {
                log.warn("Received multiple set points for asset '{}'!", receiver);
              }

              Optional<PValue> setPoint = toPValue(setPointValues.get(0).p(), null);

              if (setPoint.isEmpty()) {
                log.warn("No set point value found for asset {}.", receiver);
              } else {
                setPoints.put(receiverUuid, setPoint.get());
              }
            } else {
              // if set point is given as double values

              Map<String, Double> attrToValue =
                  messages.stream()
                      .filter(m -> m.getClass() == DoubleValue.class)
                      .map(DoubleValue.class::cast)
                      .collect(Collectors.toMap(DoubleValue::attr, DoubleValue::value));

              if (attrToValue.size() > 2) {
                log.warn("Received multiple set point values for asset '{}'!", receiver);
              } else {

                Optional<PValue> setPoint =
                    toPValue(
                        extract(attrToValue, ACTIVE_POWER), extract(attrToValue, REACTIVE_POWER));

                if (setPoint.isEmpty()) {
                  log.warn("No set point value found for asset {}.", receiver);
                } else {
                  setPoints.put(receiverUuid, setPoint.get());
                }
              }
            }
          }
        });
    return setPoints;
  }
}
