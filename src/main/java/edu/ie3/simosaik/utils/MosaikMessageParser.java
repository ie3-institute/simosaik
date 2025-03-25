/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MosaikMessageParser {
  public record MosaikMessage(String sender, String receiver, String unit, Object messageValue) {}

  public static List<MosaikMessage> filterForUnit(Collection<MosaikMessage> messages, String unit) {
    return messages.stream().filter(m -> m.unit.equals(unit)).toList();
  }

  public static List<MosaikMessage> parse(Map<String, Object> mosaikInput) {
    List<MosaikMessage> result = new ArrayList<>();

    for (Map.Entry<String, Object> entry : mosaikInput.entrySet()) {
      String assetId = entry.getKey();
      Map<String, Object> messages = (Map<String, Object>) entry.getValue();

      for (Map.Entry<String, Object> messageEntry : messages.entrySet()) {
        String unit = messageEntry.getKey();
        Map<String, Object> senderToValues = (Map<String, Object>) messageEntry.getValue();

        for (Map.Entry<String, Object> senderEntry : senderToValues.entrySet()) {
          String sender = trim(senderEntry.getKey());
          Object messageValue = senderEntry.getValue();

          result.add(new MosaikMessage(sender, assetId, unit, messageValue));
        }
      }
    }

    return result;
  }

  private static String trim(String sender) {
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
