package client;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by marcyang on 2015-11-28.
 */
public class SignalMessage implements Runnable{

    protected Timer timer;
    PeriodMessage pm;

    public SignalMessage(Client c) {

        timer = new Timer();
        pm = new PeriodMessage(c);
    }


    public void run() {
        // TODO Auto-generated method stub
        //send HELLO to neighbors every 5 seconds
        timer.scheduleAtFixedRate(pm, new Date(), 1000);

    }

    class PeriodMessage extends TimerTask {


        Client m_c;
        public PeriodMessage(Client c) {
            this.m_c = c;

        }

        @Override
        public void run() {

            //System.out.println("TRYING TO CONNECT TO THE SERVER");

            try {
                m_c.client.proxy.signal();
            }catch (Exception e) {
                System.out.println("SIGNAL: fail to connect to PC, connecting to its replica");
                try {
                    if (getStatusCode(m_c.backup.wsdlLocation) == 200) {
                        m_c.client = m_c.backup;
                    }
                    System.out.println("SIGNAL: connected to its replica");
                    m_c.client.proxy.signal();
                } catch (IOException e1) {
                    try {
                        System.out.println("SIGNAL: connected to its replica2");
                        if (getStatusCode(m_c.primary.wsdlLocation) == 200) {
                            m_c.client = m_c.primary;
                        }
                        m_c.client.proxy.signal();
                    } catch (IOException e2) {
                        //e2.printStackTrace();
                        //Thread.currentThread().interrupt();
                        //return;
                    }
                }
            }
        }

        public int getStatusCode(URL url) throws IOException {

            HttpURLConnection http = (HttpURLConnection)url.openConnection();
            http.connect();
            int statusCode = http.getResponseCode();
            return statusCode;
        }

    }
}
