package edu.ie3.simopsim;

import de.fhg.iee.opsim.client.Client;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

public class Main {
    /*
    public static void main(String[] args) {
        try {
            String urlString = "amqp://guest:guest@localhost:5672/myvhost";
            Logger log = LogManager.getLogger(Client.class);
            Client client = new Client(log);

            SimonaRandomProxy proxy = new SimonaRandomProxy(
                    client, log
            );
            client.addProxy(proxy);
            client.reconnect(
                    urlString
            );
        } catch (URISyntaxException | IOException | NoSuchAlgorithmException | KeyManagementException |
                 TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

     */
}
