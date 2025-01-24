/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.config;

public enum Simulation {
  PRIMARY_RESULT,
  MOSAIK_OPTIMIZER;

  public static Simulation parse(String simulation) {
    return switch (simulation.toLowerCase()) {
      case "primaryresult" -> PRIMARY_RESULT;
      case "mosaikoptimizer" -> MOSAIK_OPTIMIZER;
      default -> throw new IllegalStateException("Unexpected value: " + simulation.toLowerCase());
    };
  }
}
