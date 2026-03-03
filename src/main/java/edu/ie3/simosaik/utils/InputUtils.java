/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.SimosaikUnits.*;

import edu.ie3.datamodel.io.naming.timeseries.ColumnScheme;
import edu.ie3.datamodel.models.value.*;
import edu.ie3.simona.api.data.container.ExtInputContainer;
import edu.ie3.simona.api.data.model.em.*;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import edu.ie3.util.interval.ClosedInterval;
import java.util.*;
import java.util.function.BiFunction;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

public final class InputUtils {
  private static final Logger log = LoggerFactory.getLogger(InputUtils.class);

  @SuppressWarnings("unchecked")
  public static ExtInputContainer createInput(
      long tick,
      ExtEntityMapping mapping,
      Map<String, Object> inputData,
      Map<String, ColumnScheme> primaryType) {
    ExtInputContainer container = new ExtInputContainer(tick);

    // handling the input data
    for (Map.Entry<String, Object> entry : inputData.entrySet()) {
      String receiverId = entry.getKey();
      UUID receiver = mapping.from(receiverId);

      Map<String, Object> attrToData = (Map<String, Object>) entry.getValue();

      // handling primary input data
      if (primaryType.containsKey(receiverId)) {
        handlePrimaryData(container, receiver, primaryType.get(receiverId), attrToData);
        log.debug("Primary data: {}", container.primaryDataString());
      } else {
        // handling of flex/em data
        handleFlexData(container, mapping, receiver, attrToData);
      }
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
            log.debug("Unsupported primary type: {}", primaryType);
            yield null;
          }
        };

    if (value != null) {
      container.addPrimaryValue(receiver, value);
    }
  }

  @SuppressWarnings("unchecked")
  private static void handleFlexData(
      ExtInputContainer container,
      ExtEntityMapping mapping,
      UUID receiver,
      Map<String, Object> attrToData) {

    for (Map.Entry<String, Object> e : attrToData.entrySet()) {
      String attr = e.getKey();
      Map<String, Object> senderToValues = (Map<String, Object>) e.getValue();

      // try handling em data
      if (senderToValues.size() == 1) {
        // possible, since the map is not empty
        Object value = senderToValues.values().iterator().next();

        if (Objects.equals(attr, FLEX_COM)) {
          parseEmComMessage(mapping, receiver, value).forEach(container::addFlexComMessage);
        } else {
          EmData emData = handleFlexData(mapping, receiver, attr, value);
          switch (emData) {
            case FlexOptionRequest r -> container.addRequest(r);
            case FlexOptions o -> container.addFlexOptions(o.receiver(), o);
            case EmSetPoint s -> container.addSetPoint(s);
            case null, default ->
                log.debug("Could not process data for attribute '{}': {}", attr, senderToValues);
          }
        }
      }
    }
  }

  private static EmData handleFlexData(
      ExtEntityMapping mapping, UUID receiver, String attr, Object value) {
    return switch (attr) {
      case FLEX_REQUEST -> {
        if (value != null) {
          yield new FlexOptionRequest(receiver, extract(value, "disaggregated", false));
        } else {
          yield null;
        }
      }
      case FLEX_OPTIONS -> parseFlexOptions(mapping, receiver, value);
      case FLEX_SET_POINT -> parseEmSetPoints(mapping, receiver, value);
      default -> {
        log.debug("Unexpected attribute value: {}", attr);
        yield null;
      }
    };
  }

  private static FlexOptions parseFlexOptions(
      ExtEntityMapping mapping, UUID receiver, Object value) {
    UUID sender = mapping.get(extract(value, "sender", "")).orElse(receiver);
    return parseFlexOptions(mapping, receiver, sender, value);
  }

  private static FlexOptions parseFlexOptions(
      ExtEntityMapping mapping, UUID receiver, UUID sender, Object value) {
    Map<UUID, FlexOptions> disaggregated = new HashMap<>();

    Map<String, Object> disaggregatedAttrToData =
        extract(value, "disaggregated", Collections.emptyMap());

    disaggregatedAttrToData.forEach(
        (dSenderId, dValue) -> {
          UUID dSender = mapping.from(dSenderId);
          UUID dReceiver = mapping.get(extract(value, "receiver", "")).orElse(receiver);
          disaggregated.put(dSender, parseFlexOptions(mapping, dReceiver, dSender, dValue));
        });

    if (containsAll(value, FLEX_OPTION_P_REF, FLEX_OPTION_P_MIN, FLEX_OPTION_P_MAX)) {
      // we have a power limit flex option
      return new PowerLimitFlexOptions(
          receiver,
          sender,
          extractQuantity(value, FLEX_OPTION_P_REF),
          extractQuantity(value, FLEX_OPTION_P_MIN),
          extractQuantity(value, FLEX_OPTION_P_MAX),
          disaggregated);
    } else if (containsAll(
        value, FLEX_OPTION_P_MIN, FLEX_OPTION_P_MAX, ETA_CHARGE, ETA_DISCHARGE)) {
      // we have energy boundaries flex options
      Map<Long, ClosedInterval<ComparableQuantity<Energy>>> tickToEnergyLimits = new HashMap<>();

      Map<String, Object> tickToEnergy =
          extract(value, "tickToEnergyLimits", Collections.emptyMap());
      tickToEnergy.forEach(
          (tickStr, dict) -> {
            long tick = Long.parseLong(tickStr);

            ComparableQuantity<Energy> l = extractQuantity(dict, LOWER_ENERGY_LIMIT);
            ComparableQuantity<Energy> u = extractQuantity(dict, UPPER_ENERGY_LIMIT);

            tickToEnergyLimits.put(tick, new ClosedInterval<>(l, u));
          });

      return new EnergyBoundariesFlexOptions(
          receiver,
          sender,
          extract(value, "flexType", "-"),
          extractQuantity(value, FLEX_OPTION_P_MIN),
          extractQuantity(value, FLEX_OPTION_P_MAX),
          extractQuantity(value, ETA_CHARGE),
          extractQuantity(value, ETA_DISCHARGE),
          tickToEnergyLimits,
          disaggregated);

    } else {
      // can't specify the type of flex option
      // /returning only disaggregated flex options
      return new MultiFlexOptions(receiver, disaggregated);
    }
  }

  private static EmData parseEmSetPoints(ExtEntityMapping mapping, UUID receiver, Object value) {
    if (value == null) {
      return null;
    }

    BiFunction<ComparableQuantity<Power>, ComparableQuantity<Power>, PValue> builder =
        (active, reactive) -> {
          if (reactive != null) {
            return new SValue(active, reactive);
          } else if (active != null) {
            return new PValue(active);
          } else {
            return null;
          }
        };

    // handling of disaggregated set points
    Map<String, Object> disaggregated = extract(value, "disaggregated", Collections.emptyMap());
    Map<UUID, PValue> disaggregatedSetPoints = new HashMap<>();

    disaggregated.forEach(
        (id, data) -> {
          UUID model = mapping.from(id);

          ComparableQuantity<Power> active = extractQuantity(value, ACTIVE_POWER);
          ComparableQuantity<Power> reactive = extractQuantity(value, REACTIVE_POWER);

          Optional<PValue> power = Optional.ofNullable(builder.apply(active, reactive));

          power.ifPresent(p -> disaggregatedSetPoints.put(model, p));
        });

    // handling of aggregated set point
    ComparableQuantity<Power> active = extractQuantity(value, ACTIVE_POWER);
    ComparableQuantity<Power> reactive = extractQuantity(value, REACTIVE_POWER);

    Optional<PValue> power = Optional.ofNullable(builder.apply(active, reactive));

    if (power.isPresent() && !disaggregatedSetPoints.isEmpty()) {
      log.debug(
          "Got aggregated and disaggregated set point(s) at the same time for model '{}'. This could cause problems!",
          reactive);
    }

    return new EmSetPoint(receiver, power, disaggregatedSetPoints);
  }

  private static List<EmCommunicationMessage<?>> parseEmComMessage(
      ExtEntityMapping mapping, UUID receiver, Object value) {
    List<EmCommunicationMessage<?>> messages = new ArrayList<>();

    if (value instanceof List<?> list) {
      for (Object item : list) {
        messages.addAll(parseEmComMessage(mapping, receiver, item));
      }

    } else if (value instanceof Map<?, ?> map) {
      messages.add(parseEmComMessage(mapping, receiver, map));
    }

    return messages;
  }

  private static EmCommunicationMessage<?> parseEmComMessage(
      ExtEntityMapping mapping, UUID receiver, Map<?, ?> map) {
    String senderId = (String) map.get("sender");
    UUID sender = mapping.from(senderId);

    UUID msgId = null;
    EmData content = null;

    try {
      msgId = UUID.fromString((String) map.get("msg_id"));
      content = handleFlexData(mapping, receiver, (String) map.get("type"), map.get("content"));
    } catch (Exception ignored) {
    }

    return new EmCommunicationMessage<>(receiver, sender, msgId, content);
  }

  // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

  @SuppressWarnings("unchecked")
  private static boolean containsAll(Object obj, String... fields) {
    if (obj instanceof Map<?, ?> map) {
      try {
        return ((Map<String, ?>) map).keySet().containsAll(List.of(fields));
      } catch (Exception e) {
        return false;
      }
    }
    return false;
  }

  private static boolean containsAny(Object obj, String... fields) {
    if (obj instanceof Map<?, ?> map) {
      try {
        return Arrays.stream(fields).anyMatch(map::containsKey);
      } catch (Exception e) {
        return false;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private static <V> V extract(Object obj, String field, V defaultValue) {
    if (obj instanceof Map<?, ?> map) {
      try {
        return Optional.ofNullable((V) map.get(field)).orElse(defaultValue);
      } catch (ClassCastException ignored) {
      }
    }
    return defaultValue;
  }

  private static <Q extends Quantity<Q>> ComparableQuantity<Q> extractQuantity(
      Object obj, String field) {
    if (obj == null) {
      return null;
    }

    if (obj instanceof Map<?, ?> map) {
      Object mosaikValue = map.get(field);
      double value = 0d;

      if (mosaikValue instanceof Double d) {
        value = d;
      } else if (mosaikValue instanceof Map<?, ?> nested) {
        for (Object o : nested.values()) {
          try {
            double d = (double) o;
            value += d;
          } catch (Exception ignored) {
          }
        }
      }

      Unit<Q> unit = getPSDMUnit(field);
      return Quantities.getQuantity(value, unit);
    }

    return null;
  }
}
