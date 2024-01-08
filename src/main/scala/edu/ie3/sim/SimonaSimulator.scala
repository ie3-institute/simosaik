package edu.ie3.sim

import de.offis.mosaik.api.{SimProcess, Simulator}

import java.{lang, util}
class SimonaSimulator(simulatorName: String) extends Simulator(simulatorName) {

  override def init(s: String, aFloat: lang.Float, map: util.Map[String, AnyRef]): util.Map[String, AnyRef] = ???

  override def create(i: Int, s: String, map: util.Map[String, AnyRef]): util.List[util.Map[String, AnyRef]] = ???

  override def step(l: Long, map: util.Map[String, AnyRef], l1: Long): Long = ???

  override def getData(map: util.Map[String, util.List[String]]): util.Map[String, AnyRef] = ???
}


object SimonaSimulator {
  def main(args: Array[String]): Unit = {
    val sim = new SimonaSimulator("Simona")
    SimProcess.startSimulation(args, sim)
  }
}
