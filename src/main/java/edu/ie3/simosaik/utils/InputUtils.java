/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import edu.ie3.datamodel.io.naming.timeseries.ColumnScheme;
import edu.ie3.datamodel.models.value.*;
import edu.ie3.simona.api.data.container.ExtInputContainer;
import edu.ie3.simona.api.data.model.em.*;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Power;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static edu.ie3.simosaik.SimosaikUnits.*;

public final class InputUtils {
  private static final Logger log = LoggerFactory.getLogger(InputUtils.class);


  public static Map<String, Object> filter(
          Map<String, Object> inputs, Map<String, Object> cache
  ) {
      Predicate<Map.Entry<String, Object>> filterFcn = e -> {
          String key = e.getKey();

          if (cache.containsKey(key)) {
              Object value = e.getValue();
              return value != null && !cache.get(key).equals(value);
          } else {
              return false;
          }
      };

      return inputs.entrySet().stream().filter(filterFcn).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }


  @SuppressWarnings("unchecked")
  public static ExtInputContainer createInput(
      long tick,
      long nexTick,
      ExtEntityMapping mapping,
      Map<String, Object> inputData,
      Map<String, ColumnScheme> primaryType) {
    ExtInputContainer container = new ExtInputContainer(tick, nexTick);

    // handling the input data
    for (Map.Entry<String, Object> entry : inputData.entrySet()) {
      String receiverId = entry.getKey();
      UUID receiver = mapping.from(receiverId);

      Map<String, Object> attrToData = (Map<String, Object>) entry.getValue();

      // handling primary input data
      if (primaryType.containsKey(receiverId)) {
        handlePrimaryData(container, receiver, primaryType.get(receiverId), attrToData);
      }

      // handling of flex/em data
      handleFlexData(container, mapping, receiver, attrToData);

    }

    return container;
  }

  private static void handlePrimaryData(
      ExtInputContainer container,
      UUID receiver,
      ColumnScheme primaryType,
      Map<String, Object> attrToData) {
    Value value =
        switch (primaryType) {
          case ColumnScheme.ACTIVE_POWER -> new PValue(extractQuantity(attrToData, ACTIVE_POWER));
          case ColumnScheme.APPARENT_POWER ->
              new SValue(
                  extractQuantity(attrToData, ACTIVE_POWER),
                  extractQuantity(attrToData, REACTIVE_POWER));
          case ColumnScheme.ACTIVE_POWER_AND_HEAT_DEMAND ->
              new HeatAndPValue(
                  extractQuantity(attrToData, ACTIVE_POWER),
                  extractQuantity(attrToData, THERMAL_POWER));
          case ColumnScheme.APPARENT_POWER_AND_HEAT_DEMAND ->
              new HeatAndSValue(
                  extractQuantity(attrToData, ACTIVE_POWER),
                  extractQuantity(attrToData, REACTIVE_POWER),
                  extractQuantity(attrToData, THERMAL_POWER));
          default -> {
            log.warn("Unsupported primary type: {}", primaryType);
            yield null;
          }
        };

    if (value != null) {
      container.addPrimaryValue(receiver, value);
    }
  }

  @SuppressWarnings("unchecked")
  private static void  handleFlexData(
          ExtInputContainer container,
          ExtEntityMapping mapping,
          UUID receiver,
          Map<String, Object> attrToData
  ) {

      for (Map.Entry<String, Object> e : attrToData.entrySet()) {
          String attr = e.getKey();
          Map<String, Object> senderToValues = (Map<String, Object>) e.getValue();

          // try handling em data
          if (senderToValues.size() == 1) {
              // possible, since the map is not empty
              Object value = senderToValues.values().iterator().next();

              EmData emData = handleFlexData(mapping, receiver, attr, value);
              switch (emData) {
                  case FlexOptionRequest r -> container.addRequest(r.receiver(), r);
                  case FlexOptions o -> container.addFlexOptions(o.receiver(), o);
                  case EmSetPoint s -> container.addSetPoint(s);
                  case EmCommunicationMessage<?> c -> container.addFlexComMessage(c);
                  case null, default -> log.warn("Could not process data for attribute '{}': {}", attr, senderToValues);
              }
          }
      }
  }

  private static EmData handleFlexData(
      ExtEntityMapping mapping, UUID receiver, String attr, Object value) {
    return switch (attr) {
      case FLEX_REQUEST -> new FlexOptionRequest(receiver, extract(value, "disaggregated", false));
      case FLEX_OPTIONS -> parseFlexOptions(mapping, receiver, value, false);
      case FLEX_OPTIONS_DISAGGREGATED -> parseFlexOptions(mapping, receiver, value, true);
      case FLEX_SET_POINT -> parseEmSetPoints(mapping, receiver, value);
      case FLEX_COM -> parseEmComMessage(mapping, receiver, value);
      default -> {
        log.warn("Unexpected value: {}", attr);
        yield null;
      }
    };
  }

  private static EmData parseFlexOptions(
      ExtEntityMapping mapping, UUID receiver, Object value, boolean disaggregated) {
    if (disaggregated) {
      // not supported yet
      // TODO: add handling of disaggregated flex options
      return null;
    } else {
      UUID sender = mapping.get(extract(value, "sender", "")).orElse(receiver);

      return new PowerLimitFlexOptions(
          receiver,
          sender,
              extractQuantity(value, FLEX_OPTION_P_REF),
              extractQuantity(value, FLEX_OPTION_P_MIN),
          extractQuantity(value, FLEX_OPTION_P_MAX));
    }
  }

  private static EmData parseEmSetPoints(ExtEntityMapping mapping, UUID receiver, Object value) {
      if (value == null) {
          return null;
      }

    // TODO: add handling of disaggregated set points
    ComparableQuantity<Power> active = extractQuantity(value, ACTIVE_POWER);
    ComparableQuantity<Power> reactive = extractQuantity(value, REACTIVE_POWER);

    PValue power;

    if (reactive != null) {
      power = new SValue(active, reactive);
    } else {
      power = new PValue(active);
    }

    return new EmSetPoint(receiver, power);
  }

  private static EmData parseEmComMessage(ExtEntityMapping mapping, UUID receiver, Object value) {
    if (value instanceof Map<?, ?> map) {
      String senderId = (String) map.get("sender");
      UUID sender = mapping.from(senderId);

      UUID msgId = null;
      EmData content = null;

      try {
        msgId = UUID.fromString((String) map.get("msgId"));
        content = handleFlexData(mapping, receiver, (String) map.get("type"), map.get("content"));
      } catch (Exception ignored) {
      }

      log.info("Content {}", content);

      return new EmCommunicationMessage<>(receiver, sender, msgId, content);
    }

    return null;
  }

  // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

  @SuppressWarnings("unchecked")
  private static <V> V extract(Object obj, String field, V defaultValue) {
    if (obj instanceof Map<?, ?> map) {
      try {
        return (V) map.get(field);
      } catch (ClassCastException ignored) {
      }
    }
    return defaultValue;
  }

  @SuppressWarnings("unchecked")
  private static <Q extends Quantity<Q>> ComparableQuantity<Q> extractQuantity(
      Object obj, String field) {
    if (obj == null) {
      return null;
    }

    try {
      Map<String, Double> map = (Map<String, Double>) obj;
      Unit<Q> unit = getPSDMUnit(field);
      return Quantities.getQuantity(map.get(field), unit);
    } catch (Exception ignored) {
    }

    return null;
  }
}
