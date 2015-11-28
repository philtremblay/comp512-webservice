package middleware;


import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by marcyang on 2015-11-28.
 */
public class SignalMessage implements Runnable{

    protected ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(1);
    PeriodMessage pm;

    public SignalMessage(ResourceManagerImpl c) {

        pm = new PeriodMessage(c);
    }

    public void run() {

        //send HELLO to neighbors every 5 seconds

        scheduledPool.scheduleWithFixedDelay(pm, 1, 1, TimeUnit.SECONDS);

    }

    class PeriodMessage extends TimerTask {


        ResourceManagerImpl m_c;
        public PeriodMessage(ResourceManagerImpl c) {
            this.m_c = c;

        }

        @Override
        public void run() {

            while(true) {
                //reconnecting the flight proxy
                try {
                    m_c.flightProxy.proxy.signal();
                } catch (Exception e) {
                    System.out.println("SIGNAL: fail to connect to PC, connecting to its replica");
                    try {
                        if (getStatusCode(m_c.flightProxyBackup.wsdlLocation) == 200) {
                            m_c.flightProxy = m_c.flightProxyBackup;
                        }
                        System.out.println("SIGNAL: connected to its replica");
                        m_c.flightProxy.proxy.signal();
                    } catch (IOException e1) {
                        try {
                            System.out.println("SIGNAL: connected to its replica2");
                            if (getStatusCode(m_c.flightPC.wsdlLocation) == 200) {
                                m_c.flightProxy = m_c.flightPC;
                            }
                            m_c.flightProxy.proxy.signal();
                        } catch (IOException e2) {
                            //e2.printStackTrace();
                            //Thread.currentThread().interrupt();
                            //return;
                        }
                    }
                }

                //reconnecting the car proxy
                try {
                    m_c.carProxy.proxy.signal();
                } catch (Exception e) {
                    System.out.println("SIGNAL: fail to connect to PC, connecting to its replica");
                    try {
                        if (getStatusCode(m_c.carProxyBackup.wsdlLocation) == 200) {
                            m_c.carProxy = m_c.carProxyBackup;
                        }
                        System.out.println("SIGNAL: connected to its replica");
                        m_c.carProxy.proxy.signal();
                    } catch (IOException e1) {
                        try {
                            System.out.println("SIGNAL: connected to its replica2");
                            if (getStatusCode(m_c.carPC.wsdlLocation) == 200) {
                                m_c.carProxy = m_c.carPC;
                            }
                            m_c.carProxy.proxy.signal();
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }

                //reconning the room proxy
                try {
                    m_c.roomProxy.proxy.signal();
                } catch (Exception e) {
                    System.out.println("SIGNAL: fail to connect to PC, connecting to its replica");
                    try {
                        if (getStatusCode(m_c.flightProxyBackup.wsdlLocation) == 200) {
                            m_c.roomProxy = m_c.flightProxyBackup;
                        }
                        System.out.println("SIGNAL: connected to its replica");
                        m_c.roomProxy.proxy.signal();
                    } catch (IOException e1) {
                        try {
                            System.out.println("SIGNAL: connected to its replica2");
                            if (getStatusCode(m_c.roomPC.wsdlLocation) == 200) {
                                m_c.roomProxy = m_c.roomPC;
                            }
                            m_c.roomProxy.proxy.signal();
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
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
