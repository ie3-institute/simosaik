package edu.ie3.simopsim;

import de.fhg.iee.opsim.abstracts.ConservativeSynchronizedProxy;
import de.fhg.iee.opsim.client.Client;
import de.fhg.iee.opsim.interfaces.ClientInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Abstract class that extends the Proxy interface of OPSIM
 */
public abstract class SimonaProxy extends ConservativeSynchronizedProxy {

    protected Logger logger;
    protected ClientInterface cli;
    protected String componentDescription = "SIMONA";

    public SimonaProxy() {
        try {
            Logger logger = LogManager.getLogger(Client.class);
            Client client = new Client(logger);
            this.logger = logger;
            this.cli = client;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ClientInterface getCli() {
        return cli;
    }
}
