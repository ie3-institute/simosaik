/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.utils.SimosaikTranslation.*;

import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.SValue;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.container.ExtInputDataContainer;
import edu.ie3.simona.api.data.em.model.FlexOptions;
import edu.ie3.simona.api.data.mapping.ExtEntityMapping;
import edu.ie3.simosaik.utils.MosaikMessageParser.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Power;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

public class InputUtils {
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
    Map<String, UUID> idToUuid = mapping.getFullMapping();

    // primary data
    parsePrimary(receiverToMessages, idToUuid).forEach(container::addPrimaryValue);

    // em data
    parseFlexRequests(receiverToMessages, idToUuid).forEach(container::addRequest);
    parseFlexOptions(receiverToMessages, idToUuid).forEach(container::addFlexOptions);
    parseSetPoints(receiverToMessages, idToUuid).forEach(container::addSetPoint);

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

          List<Value> values = toValues(dvs);

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

            Optional<PValue> setPoint =
                toPValue(Optional.ofNullable(setPointValues.get(0).p()), Optional.empty());

            if (setPoint.isEmpty()) {
              log.warn("No set point value found for asset {}.", receiver);
            } else {
              setPoints.put(receiverUuid, setPoint.get());
            }
          }
        });
    return setPoints;
  }

  private static List<Value> toValues(List<DoubleValue> values) {
    Map<String, Double> attrToDouble = new HashMap<>();

    for (DoubleValue dv : values) {
      String attr = dv.attr();
      double d = dv.value();

      if (attrToDouble.containsKey(attr)) {
        double sum = attrToDouble.get(attr) + d;
        attrToDouble.put(attr, sum);
      } else {
        attrToDouble.put(attr, d);
      }
    }

    return toValues(attrToDouble);
  }

  private static List<Value> toValues(Map<String, Double> values) {
    List<Value> valueList = new ArrayList<>();

    // convert power
    Optional<ComparableQuantity<Power>> active =
        extractAny(values, MOSAIK_ACTIVE_POWER, MOSAIK_ACTIVE_POWER_IN);
    Optional<ComparableQuantity<Power>> reactive =
        extractAny(values, MOSAIK_REACTIVE_POWER, MOSAIK_REACTIVE_POWER_IN);

    toPValue(active, reactive).ifPresent(valueList::add);

    return valueList;
  }

  private static Optional<PValue> toPValue(
      Optional<ComparableQuantity<Power>> active, Optional<ComparableQuantity<Power>> reactive) {
    ComparableQuantity<Power> p = active.orElse(null);

    if (reactive.isPresent()) {
      return Optional.of(new SValue(p, reactive.get()));
    } else {
      if (p == null) {
        return Optional.empty();
      } else {
        return Optional.of(new PValue(p));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <Q extends Quantity<Q>> Optional<ComparableQuantity<Q>> extractAny(
      Map<String, Double> valueMap, String... fields) {
    return Stream.of(fields)
        .map(field -> (ComparableQuantity<Q>) extract(valueMap, field))
        .filter(Objects::nonNull)
        .findFirst();
  }

  @SuppressWarnings("unchecked")
  private static <Q extends Quantity<Q>> ComparableQuantity<Q> extract(
      Map<String, Double> valueMap, String field) {
    return Optional.ofNullable(valueMap.get(field))
        .map(value -> Quantities.getQuantity(value, (Unit<Q>) getPSDMUnit(field)))
        .orElse(null);
  }
}
