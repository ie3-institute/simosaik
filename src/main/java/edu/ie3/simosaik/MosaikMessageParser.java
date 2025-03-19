/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
    if (sender.contains("SimonaPowerGrid-0.")) {
      return sender.replace("SimonaPowerGrid-0.", "");
    }
    return sender;
  }
}
