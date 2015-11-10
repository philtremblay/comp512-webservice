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

        //spawn off 3 clients threads

        //for 1tps, adjust numSeconds = 3
        //for 3 tps, adjust the numSeconds = 1

        int numClients = 3;
        int numSeconds = 1;
        int testSize = 500;
        String[] locations = {"1", "2", "3", "4"};




        int trxnid = proxy.start();
        //start with 4 customers
        //fill up the tank with 150 *4 planes, cars and rooms
        proxy.newCustomerId(trxnid, 1);
        proxy.newCustomerId(trxnid, 2);
        proxy.newCustomerId(trxnid, 3);
        proxy.newCustomerId(trxnid, 4);

        //create 4 types of planes and 4 types of cars + rooms
        try {
            for (int i = 1; i < 5; i++) {
                proxy.addFlight(trxnid, i, testSize, 1);
            }

            for (int j = 0; j < locations.length; j++) {
                proxy.addCars(trxnid, locations[j], testSize, 1);
                proxy.addRooms(trxnid, locations[j], testSize, 1);
            }

        } catch (client.DeadlockException_Exception e) {
            e.printStackTrace();
        }
        proxy.commit(trxnid);
        System.out.println("START THE CLIENTS");



        Thread[] t = new Thread[numClients];
        SampleClient[] clients = new SampleClient[numClients];
        for (int i = 0; i < numClients; i++) {
            System.out.println("Client " + (i +1));
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

        int High = 4;
        int Low = 1;
        id = r.nextInt(High-Low) + Low;

        return id;
    }



    @Override
    public void run() {
        //define transaction
        int i = 0;
        String filename = "client" + (name+1) + ".csv";

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


            int customerid = RandomNumber();
            int flightid = RandomNumber();
            String location = Integer.toString(RandomNumber());

            StopWatch watch = new StopWatch();
            int trxnId = proxy.start();
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



