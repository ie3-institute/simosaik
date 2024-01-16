package edu.ie3.sim

import de.offis.mosaik.api.{SimProcess, Simulator}

import com.fasterxml.jackson.databind.ObjectMapper

import java.util.Locale
import java.util.logging.Logger
import java.{lang, util}
class SimonaSimulator(simulatorName: String) extends Simulator(simulatorName) {

  implicit val logger: Logger = SimProcess.logger
  private val mapper: ObjectMapper = new ObjectMapper()
  private val meta: String =
    s"""{
       | "api_version": "${Simulator.API_VERSION}",
       | "models": {
       |   "SimonaPowerGrid": {
       |     "public": true,
       |     "params": ["simona_config"],
       |     "attrs": ["v_dev", "line_load"]
       |   }
       | }
       |}""".stripMargin

  override def init(
                     sid: String,
                     timeResolution: lang.Float,
                     simParams: util.Map[String, AnyRef]): util.Map[String, AnyRef] = {
    Locale.setDefault(Locale.ENGLISH)

    logger.info(
      s"[SIMONA] Starting with interface '${getClass.getSimpleName.replaceAll("\\$", "")}'."
    )

    if (!simParams.isEmpty) {
      logger.warning("[SIMONA] does not expect any simulation parameters from Mosaik.")
    }

    logger.info("[SIMONA] acknowledges init request from Mosaik. Returning meta data")

    val metaOut = mapper.readValue(meta, simParams.getClass)
    metaOut
  }

  override def create(
                       num: Int,
                       model: String,
                       modelParams: util.Map[String, AnyRef]): util.List[util.Map[String, AnyRef]] = {
    // List of models returned to Mosaik
    val entities: util.List[util.Map[String, Object]] = new util.ArrayList()

    model match {
      case "SimonaPowerGrid" =>
        if (num > 1) {
          throw new MosaikParameterException(
            "[SIMONA] Only 1 simulator instance is allowed for SimonaPowerGrid."
          )}
        if (modelParams.isEmpty || (!modelParams.containsKey("simona_config")))
          throw new MosaikConfigPathException(
            "[SIMONA] Please provide a valid config file via --config <path-to-config-file>"
          )
        else {
          configPath = Array(modelParams.get("simona_config").toString)
          val entity: util.HashMap[String, Object] = new util.HashMap()
          entity.put("eid", s"$model")
          entity.put("type", model)
          entities.add(entity)
          entities
        }

      case x => throw new MosaikParameterException(s"[SIMONA] $x is not a valid Simona model")

    }
  }

  override def step(
                     time: Long,
                     inputs: util.Map[String, AnyRef],
                     maxAdvance: Long): Long = ???

  override def getData(outputs: util.Map[String, util.List[String]]): util.Map[String, AnyRef] = ???
}


object SimonaSimulator {
  def main(args: Array[String]): Unit = {
    val sim = new SimonaSimulator("Simona")
    SimProcess.startSimulation(args, sim)
  }
}
