/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.SimosaikUnits.*;

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

        if (content != null) {
          messages.add(new ParsedMessage(receiver, mosaikSender, content));
        }
      }
    }

    return messages;
  }

  private static Content parseValue(String receiver, String attr, Object value) {
    log.info("Receiver '{}', attr '{}' -> Value: {}", receiver, attr, value);

    return switch (attr) {
      case FLEX_REQUEST -> new FlexRequestMessage(receiver, extract(value, "disaggregated", false));
      case FLEX_OPTIONS -> parseFlexOptions(receiver, value, false);
        // case FLEX_OPTIONS_DISAGGREGATED -> parseFlexOptions(receiver, value, true);
      case FLEX_SET_POINT ->
          new FlexSetPointMessage(
              receiver,
              extract(value, "sender", ""),
              extractQuantity(value, ACTIVE_POWER),
              extractQuantity(value, REACTIVE_POWER));
      case FLEX_COM -> parseEmComMessage(receiver, value);
      default -> {
        if (value instanceof Double d) {
          yield new DoubleValue(attr, d);
        } else {
          log.warn("Unsupported attribute '{}'", attr);
          yield new NullValue(attr);
        }
      }
    };
  }

  private static FlexOptionsMessage parseFlexOptions(
      String receiver, Object value, boolean disaggregated) {
    Map<String, FlexOptionInformation> receiverToInformation = new HashMap<>();

    if (disaggregated) {
      // not supported yet
      // TODO: add handling of disaggregated flex options
    } else {
      receiverToInformation.put(
          receiver,
          new FlexOptionInformation(
              receiver,
              extract(value, "sender", ""),
              extractQuantity(value, FLEX_OPTION_P_MIN),
              extractQuantity(value, FLEX_OPTION_P_REF),
              extractQuantity(value, FLEX_OPTION_P_MAX)));
    }

    return new FlexOptionsMessage(receiverToInformation);
  }

  private static FlexComMessage parseEmComMessage(String receiver, Object value) {
    if (value instanceof Map<?, ?> map) {
      String sender = (String) map.get("sender");
      String msgId = (String) map.get("msgId");

      Content content = parseValue(receiver, (String) map.get("type"), map.get("content"));
      log.info("Content {}", content);

      return new FlexComMessage(receiver, sender, msgId, content);
    }

    return null;
  }

  // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

  public sealed interface Content permits FlexMessage, DoubleValue, NullValue {}

  public record NullValue(String attr) implements Content {}

  public record DoubleValue(String attr, double value) implements Content {}

  public sealed interface FlexMessage extends Content
      permits FlexComMessage, FlexOptionsMessage, FlexRequestMessage, FlexSetPointMessage {}

  public record FlexComMessage(String receiver, String sender, String msgId, Content content)
      implements FlexMessage {}

  public record FlexRequestMessage(String receiver, boolean disaggregated) implements FlexMessage {}

  public record FlexOptionsMessage(Map<String, FlexOptionInformation> receiverToInformation)
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
