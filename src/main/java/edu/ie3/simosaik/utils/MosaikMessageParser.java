/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.utils.SimosaikTranslation.*;

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

public class MosaikMessageParser {
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

    if (attr.equals(FLEX_REQUEST)) {
      Optional<String> sender =
          Optional.ofNullable(value).map(Object::toString).map(MosaikMessageParser::trim);
      return new FlexRequestMessage(receiver, sender);

    } else if (attr.equals(FLEX_OPTIONS) && value instanceof Map<?, ?> map) {
      Collection<Map<String, Object>> options =
          (Collection<Map<String, java.lang.Object>>) map.values();

      List<FlexOptionInformation> information =
          options.stream()
              .map(
                  option ->
                      new FlexOptionInformation(
                          receiver,
                          (String) option.get("sender"),
                          extractQuantity(option, FLEX_OPTION_P_MIN),
                          extractQuantity(option, FLEX_OPTION_P_REF),
                          extractQuantity(option, FLEX_OPTION_P_MAX)))
              .toList();

      return new FlexOptionsMessage(information);

    } else if (attr.equals(FLEX_SET_POINT) && value instanceof Map<?, ?> map) {
      Map<String, Object> setPoint = (Map<String, Object>) map;

      return new FlexSetPointMessage(receiver, extractQuantity(setPoint, MOSAIK_ACTIVE_POWER));

    } else if (value instanceof Double d) {
      return new DoubleValue(attr, d);

    } else {

      // throw an exception if we receive a value
      if (value != null) {
        throw new ConversionException("Could not parse attribute: " + attr);
      } else {
        return new NullValue(attr);
      }
    }
  }

  public sealed interface Content permits FlexMessage, DoubleValue, NullValue {}

  public record NullValue(String attr) implements Content {}

  public record DoubleValue(String attr, Double value) implements Content {}

  public sealed interface FlexMessage extends Content
      permits FlexRequestMessage, FlexOptionsMessage, FlexSetPointMessage {}

  public record FlexRequestMessage(String receiver, Optional<String> sender)
      implements FlexMessage {}

  public record FlexOptionsMessage(List<FlexOptionInformation> information)
      implements FlexMessage {}

  public record FlexSetPointMessage(String receiver, ComparableQuantity<Power> p)
      implements FlexMessage {}

  public record FlexOptionInformation(
      String receiver,
      String sender,
      ComparableQuantity<Power> pMin,
      ComparableQuantity<Power> pRef,
      ComparableQuantity<Power> pMax) {}

  private static <Q extends Quantity<Q>> ComparableQuantity<Q> extractQuantity(
      Map<String, Object> map, String field) {
    double d = (double) map.get(field);
    Unit<Q> unit = getPSDMUnit(field);
    return Quantities.getQuantity(d, unit);
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
