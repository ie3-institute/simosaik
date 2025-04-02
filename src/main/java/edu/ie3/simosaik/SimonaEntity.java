/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.simona.api.simulation.mapping.DataType;
import java.util.stream.Stream;

public enum SimonaEntity {
  PRIMARY_P("PrimaryP"),
  PRIMARY_PQ("PrimaryPQ"),
  EM("EM"),
  EM_COMMUNICATION("EmCommunication"),
  GRID_RESULTS("GridResults"),
  PARTICIPANT_RESULTS("ParticipantResults"),
  FLEX_RESULTS("FlexResults");

  public String name;

  SimonaEntity(String name) {
    this.name = name;
  }

  public static DataType toType(SimonaEntity simonaEntity) {
    return switch (simonaEntity) {
      case PRIMARY_P, PRIMARY_PQ -> DataType.EXT_PRIMARY_INPUT;
      case EM -> DataType.EXT_EM_INPUT;
      case EM_COMMUNICATION -> DataType.EXT_EM_COMMUNICATION;
      case GRID_RESULTS -> DataType.EXT_GRID_RESULT;
      case PARTICIPANT_RESULTS -> DataType.EXT_PARTICIPANT_RESULT;
      case FLEX_RESULTS -> DataType.EXT_FLEX_OPTIONS_RESULT;
    };
  }

  public static SimonaEntity parseType(String modelType) {
    return Stream.of(SimonaEntity.values())
        .filter(e -> e.name.equals(modelType))
        .findFirst()
        .orElseGet(
            () ->
                switch (modelType) {
                  case "p", "P" -> PRIMARY_P;
                  case "pq", "PQ", "Pq" -> PRIMARY_PQ;
                  case "Communication", "communication" -> EM_COMMUNICATION;
                  case "Grid", "grid" -> GRID_RESULTS;
                  case "Participant", "participant" -> PARTICIPANT_RESULTS;
                  default -> throw new IllegalArgumentException("Unknown model type: " + modelType);
                });
  }
}
