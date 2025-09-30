/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.SimosaikUnits.*;

import edu.ie3.simosaik.exceptions.ConversionException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Power;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

public final class MosaikMessageParser {

  private static final Logger log = LoggerFactory.getLogger(MosaikMessageParser.class);

  public record ParsedMessage(String receiver, String mosaikSender, Content content) {}

  public static List<ParsedMessage> filter(
      Collection<ParsedMessage> messages, List<ParsedMessage> cache) {
    return messages.stream().filter(m -> !cache.contains(m)).toList();
  }

  public static List<ParsedMessage> parse(Map<String, Object> mosaikInput) {
    List<ParsedMessage> messages = new ArrayList<>();

    for (Map.Entry<String, Object> entry : mosaikInput.entrySet()) {
      String receiver = entry.getKey();
      Map<String, Object> data = (Map<String, Object>) entry.getValue();

      messages.addAll(parseAttributes(receiver, data));
    }

    return messages;
  }

  private static List<ParsedMessage> parseAttributes(
      String receiver, Map<String, Object> attrToData) {
    List<ParsedMessage> messages = new ArrayList<>();

    for (Map.Entry<String, Object> entry : attrToData.entrySet()) {
      String attr = entry.getKey();
      Map<String, Object> senderToValue = (Map<String, Object>) entry.getValue();

      for (Map.Entry<String, Object> senderValue : senderToValue.entrySet()) {
        String mosaikSender = senderValue.getKey();

        Content content = parseValue(receiver, attr, senderValue.getValue());

        messages.add(new ParsedMessage(receiver, mosaikSender, content));
      }
    }

    return messages;
  }

  @SuppressWarnings("unchecked")
  private static Content parseValue(String receiver, String attr, Object value) {
      log.info("Receiver '{}', attr '{}' -> Value: {}", receiver, attr, value);

    if (attr.equals(FLEX_REQUEST)) {
      Optional<String> sender = Optional.empty();
      boolean disaggregated = false;

      if (value instanceof Map<?, ?> request) {
        sender =
            Optional.ofNullable(request.get("sender"))
                .map(Object::toString)
                .map(MosaikMessageParser::trim);

        try {
          disaggregated = (boolean) request.get("disaggregated");
        } catch (Exception ignored) {
        }
      }

      return new FlexRequestMessage(receiver, sender, disaggregated);

    } else if (attr.equals(FLEX_OPTIONS)) {
      List<Object> list = new ArrayList<>();

      if (value instanceof Map<?, ?> map) {
        list.add(map);
      } else if (value instanceof List<?> valueList) {
          list.addAll(valueList);
      }

      List<FlexOptionInformation> information = new ArrayList<>();

      for (Object item : list) {

        if (item instanceof Map<?, ?> map) {
          Map<String, Object> option = (Map<String, Object>) map;

          information.add(
              new FlexOptionInformation(
                  receiver,
                  (String) option.get("sender"),
                  extractQuantity(option, FLEX_OPTION_P_MIN),
                  extractQuantity(option, FLEX_OPTION_P_REF),
                  extractQuantity(option, FLEX_OPTION_P_MAX)));
        }
      }

      return new FlexOptionsMessage(information);

    } else if (attr.equals(FLEX_SET_POINT) && value instanceof Map<?, ?> map) {
      Map<String, Object> setPoint = (Map<String, Object>) map;

      return new FlexSetPointMessage(
          receiver,
          (String) map.get("sender"),
          extractQuantity(setPoint, ACTIVE_POWER),
          extractQuantity(setPoint, REACTIVE_POWER));

    } else if (value instanceof Double d) {
      if (Double.isNaN(d)) {
        log.warn("Value for attribute '{}' for receiver '{}' is NaN.", attr, receiver);
      }

      return new DoubleValue(attr, d);

    } else {

      // throw an exception if we receive a value
      if (value != null) {
        throw new ConversionException("Could not parse attribute: " + attr + "; Value: " + value);
      } else {
        return new NullValue(attr);
      }
    }
  }

  public sealed interface Content permits FlexMessage, DoubleValue, NullValue {}

  public record NullValue(String attr) implements Content {}

  public record DoubleValue(String attr, double value) implements Content {}

  public sealed interface FlexMessage extends Content
      permits FlexRequestMessage, FlexOptionsMessage, FlexSetPointMessage {}

  public record FlexRequestMessage(String receiver, Optional<String> sender, boolean disaggregated)
      implements FlexMessage {}

  public record FlexOptionsMessage(List<FlexOptionInformation> information)
      implements FlexMessage {}

  public record FlexSetPointMessage(
      String receiver, String sender, ComparableQuantity<Power> p, ComparableQuantity<Power> q)
      implements FlexMessage {}

  public record FlexOptionInformation(
      String receiver,
      String sender,
      ComparableQuantity<Power> pMin,
      ComparableQuantity<Power> pRef,
      ComparableQuantity<Power> pMax) {}

  private static <Q extends Quantity<Q>> ComparableQuantity<Q> extractQuantity(
      Map<String, Object> map, String field) {
    if (map.containsKey(field)) {
      Object value = map.get(field);

      if (value instanceof Double d) {
        Unit<Q> unit = getPSDMUnit(field);
        return Quantities.getQuantity(d, unit);
      }
    }

    return null;
  }

  public static String trim(String sender) {
    Pattern dot = Pattern.compile("\\.");
    Matcher matcher = dot.matcher(sender);

    int count = 0;

    while (matcher.find()) {
      count++;
    }

    if (count == 0) {
      return sender;
    } else {
      return sender.split("\\.")[count];
    }
  }
}
