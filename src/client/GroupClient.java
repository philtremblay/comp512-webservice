package client;

import java.io.*;
import java.net.MalformedURLException;
import java.util.Random;

/**
 * Created by marcyang on 2015-11-09.
 */
public class GroupClient extends WSClient {


    public GroupClient(String serviceName, String serviceHost, int servicePort) throws MalformedURLException {
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

            System.out.println("START TESTING TEN CONCURRENT CLIENTS ");
            GroupClient groupClient = new GroupClient(serviceName, serviceHost, servicePort);

            groupClient.startTest();

        } catch(Exception e) {
            e.printStackTrace();
        }

    }

    public void startTest() {
        //fill up the servers with planes

        //spawn off 10 clients threads

        //for 1tps, adjust numSeconds = 10
        //for 10 tps, adjust the numSeconds = 1

        int numClients = 1;
        int numSeconds = 1;
        int testSize = 500;
        String location1 = "1";
        String location2 = "2";
        String location3 = "3";



        int trxnid = proxy.start();
        //fill up the tank with 150 *3 planes, cars and rooms
        proxy.newCustomerId(trxnid, 1);
        proxy.newCustomerId(trxnid, 2);
        proxy.newCustomerId(trxnid, 3);

        try {
            proxy.addFlight(trxnid, 1, testSize, 1);
            proxy.addFlight(trxnid, 2, testSize, 1);
            proxy.addFlight(trxnid, 3, testSize, 1);

            proxy.addCars(trxnid, location1, testSize, 1);
            proxy.addCars(trxnid, location2, testSize, 1);
            proxy.addCars(trxnid, location3, testSize, 1);


            proxy.addRooms(trxnid, location1, testSize, 1);
            proxy.addRooms(trxnid, location2, testSize, 1);
            proxy.addRooms(trxnid, location3, testSize, 1);


        } catch (client.DeadlockException_Exception e) {
            e.printStackTrace();
        }
        proxy.commit(trxnid);
        System.out.println("START THE CLIENTS");



        Thread[] t = new Thread[numClients];
        SampleClient[] clients = new SampleClient[numClients];
        for (int i = 0; i < numClients; i++) {

            clients[i] = new SampleClient(proxy, numSeconds, i);
            t[i] =new Thread(clients[i]);
            t[i].start();
        }


        while(true) {
            //keep the threads going

        }


    }

}


class SampleClient implements Runnable {

    client.ResourceManager proxy;
    int tps;
    int numSeconds;
    int name;
    public SampleClient(client.ResourceManager proxy, int seconds, int i) {
        this.proxy = proxy;
        this.numSeconds = seconds;
        this.name = i;

    }


    public int RandomNumber() {

        int id;
        Random r = new Random();

        int High = 3;
        int Low = 1;
        id = r.nextInt(High-Low) + Low;

        return id;
    }



    @Override
    public void run() {
        //define transaction
        int i = 0;
        String filename = "client" + name + ".csv";

        //file to write results

        FileWriter f = null;
        PrintWriter writer = null;
        try {
            f = new FileWriter(filename, true);
            writer = new PrintWriter(f);
            writer.println("GROUP CLIENT TEST RESULTS");
        } catch (IOException e) {
            e.printStackTrace();
        }


        while(i < 50) {
            StopWatch watch = new StopWatch();

            int trxnId = proxy.start();
            int customerid = RandomNumber();
            int flightid = RandomNumber();
            String location = Integer.toString(RandomNumber());

            proxy.reserveFlight(trxnId, customerid, flightid);
            proxy.reserveCar(trxnId, customerid, location);
            proxy.reserveRoom(trxnId, customerid, location);
            proxy.commit(trxnId);

            double time = watch.elapsedTime();

            writer.print(time + ",");
            writer.println();


            try {
                Thread.sleep(numSeconds*1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            i++;
        }

        writer.close();


        System.out.println("CLIENT IS FINISHED");
        Thread.currentThread().interrupt();





    }
}



