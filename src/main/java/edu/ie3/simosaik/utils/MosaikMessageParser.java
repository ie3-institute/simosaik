/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MosaikMessageParser {
  private static final Logger log = LoggerFactory.getLogger(MosaikMessageParser.class);

  public record MosaikMessageInformation(String receiver, String unit, Object messageValue) {}

  public record MosaikMessage(String receiver, MultiValueMap<String, Object> unitToValues) {}

  public static List<MosaikMessage> filterForUnit(Collection<MosaikMessage> messages, String unit) {
    return messages.stream().filter(m -> m.unitToValues.containsKey(unit)).toList();
  }

  public static List<MosaikMessageInformation> extractInformation(
      Map<String, Object> mosaikInput, List<MosaikMessageInformation> cache) {
    List<MosaikMessageInformation> messageParts = new ArrayList<>();

    for (Map.Entry<String, Object> entry : mosaikInput.entrySet()) {
      String assetId = entry.getKey();
      Map<String, Object> messages = (Map<String, Object>) entry.getValue();

      for (Map.Entry<String, Object> messageEntry : messages.entrySet()) {
        String unit = messageEntry.getKey();
        Map<String, Object> senderToValues = (Map<String, Object>) messageEntry.getValue();

        for (Map.Entry<String, Object> senderEntry : senderToValues.entrySet()) {
          Object messageValue = senderEntry.getValue();

          messageParts.add(new MosaikMessageInformation(assetId, unit, messageValue));
        }
      }
    }

    List<MosaikMessageInformation> information =
        messageParts.stream().filter(msg -> !cache.contains(msg)).toList();

    // adding new messages to the cache
    cache.addAll(information);

    return information;
  }

  public static List<MosaikMessage> parse(List<MosaikMessageInformation> information) {
    List<MosaikMessage> messages = new ArrayList<>();

    information.stream()
        .collect(Collectors.groupingBy(MosaikMessageInformation::receiver))
        .forEach(
            (receiver, parts) -> {
              MultiValueMap<String, Object> unitToValues = new MultiValueMap<>();
              parts.forEach(part -> unitToValues.put(part.unit, part.messageValue));

              messages.add(new MosaikMessage(receiver, unitToValues));
            });

    log.info("Parsed mosaik messages: {}", messages);

    return messages;
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
