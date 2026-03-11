/*
 * © 2026. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import org.slf4j.Logger;

public final class ConfigurableLogger {
  private boolean debugFlag;
  private final Logger log;

  public ConfigurableLogger(boolean debugFlag, Logger log) {
    this.debugFlag = debugFlag;
    this.log = log;
  }

  public void setFlag(boolean value) {
    this.debugFlag = value;
  }

  public void info(String var1, Object... var2) {
    if (debugFlag) {
      log.info(var1, var2);
    } else {
      log.debug(var1, var2);
    }
  }

  public void warn(String var1, Object... var2) {
    if (debugFlag) {
      log.warn(var1, var2);
    } else {
      log.debug(var1, var2);
    }
  }
}
