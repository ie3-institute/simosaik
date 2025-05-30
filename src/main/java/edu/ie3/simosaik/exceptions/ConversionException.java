/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.exceptions;

/** Exception, that is thrown, if the conversion is not possible. */
public class ConversionException extends RuntimeException {

  public ConversionException(String message) {
    super(message);
  }
}
