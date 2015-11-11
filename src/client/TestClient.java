package client;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Vector;

/**
 * Created by Phil Tremblay on 2015-11-09.
 */
public class TestClient extends WSClient{
    public TestClient(String serviceName, String serviceHost, int servicePort)
            throws Exception {
        super(serviceName, serviceHost, servicePort);
    }
    public static void main(String[] args) {
        try {

            if (args.length != 3) {
                System.out.println("Usage: MyClient <service-name> "
                        + "<service-host> <service-port>");
                System.exit(-1);
            }

            String serviceName = args[0];
            String serviceHost = args[1];
            int servicePort = Integer.parseInt(args[2]);

            TestClient client = new TestClient(serviceName, serviceHost, servicePort);
            System.out.println("yo marc what's up!!");
            client.runSingleClient();

        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    public void runSingleClient(){
        //create flights and cars and rooms.
        int testSize = 500;
        int flightNum = 1;
        String location = "1";
        int customerId = 1;

        //for flight reservation
        Vector flight = new Vector();
        flight.add(flightNum);

        //file to write results
        PrintWriter writer = null;
        try {
            writer = new PrintWriter("perf_analysis_singleClient.csv","UTF-8");
            writer.println("Test");
            writer.println("Single RM");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        int txnId = proxy.start();
        try {
            proxy.addFlight(txnId,flightNum,4*testSize,1);
            proxy.addCars(txnId,location,testSize,1);
            proxy.addRooms(txnId,location,testSize,1);
            proxy.newCustomerId(txnId,customerId);
        } catch (client.DeadlockException_Exception e) {
            e.printStackTrace();
        }
        proxy.commit(txnId);

        //Single RM
        //reserve a flight and itinerary
        StopWatch totalTime = new StopWatch();
        StopWatch singleRMTotal = new StopWatch();
        for (int i = 0; i< testSize ; i++){
            //reserve flight
            //start timer
            txnId = proxy.start();
            StopWatch stopWatch = new StopWatch();
            proxy.reserveFlight(txnId,customerId,flightNum);
            proxy.reserveFlight(txnId,customerId,flightNum);
            proxy.reserveFlight(txnId,customerId,flightNum);
            proxy.commit(txnId);
            double time = stopWatch.elapsedTime();
            if (writer != null) {
                writer.print(time + ",");
            }
            else{
                //NullPointerException
                return;
            }
        }

        double totalSingRM = singleRMTotal.elapsedTime();
        if (writer != null) {
            writer.println("");
            writer.println("Total time for single RM: "+totalSingRM);
            writer.println("Average time for single RM: "+ totalSingRM/testSize);
            writer.println("All three RMs");
        }
        else{
            //NullPointerException
            return;
        }

        //All three RM

        //reserve a flight and itinerary
        StopWatch threeRM = new StopWatch();
        for (int i = 0; i<testSize ; i++) {
            //reserve flight
            //start timer
            StopWatch stopWatch = new StopWatch();
            txnId = proxy.start();
            //reserve itinerary
            proxy.reserveItinerary(txnId, customerId, flight, location, true, true);
            proxy.commit(txnId);
            double time = stopWatch.elapsedTime();
            if (writer != null) {
                writer.print(time + ",");
            } else {
                //NullPointerException
                return;
            }
        }
        double totalThreeRM = threeRM.elapsedTime();
        double overallTime = totalTime.elapsedTime();
        if (writer != null) {

            writer.println("");
            writer.println("Total time for all 3 RMs: ");
            writer.println(totalThreeRM);
            writer.println("Average time for all 3 RMs :");
            writer.println(totalThreeRM/testSize);
            writer.println("Total analysis time: ");
            writer.println(overallTime);
            writer.println("Total analysis average time: ");
            writer.println(overallTime / (2 * testSize));

            writer.close();
        }
        else {
            return;
        }
    }
}
