package middleware;

import client.DeadlockException_Exception;
import server.LockManager.*;


import client.WSClient;
import server.Trace;

import javax.jws.WebService;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;


//this class is implementating webservice interfaces (resourceManger)
@WebService(endpointInterface = "server.ws.ResourceManager")
public class ResourceManagerImpl implements server.ws.ResourceManager {

    WSClient flightProxy;
    WSClient flightPC;
    WSClient flightProxyBackup;
    WSClient carProxy;
    WSClient carPC;
    WSClient carProxyBackup;
    WSClient roomProxy;
    WSClient roomPC;
    WSClient roomProxyBackup;

    private static final int READ = 0;
    private static final int WRITE = 1;

    public static final int FLIGHT = 1;
    public static final int CAR = 2;
    public static final int ROOM = 3;
    public static final int CUST = 4;
    public static final int DEL = 5;
    public static final int ADD = 6;
    public static final int RES = 7;
    public static final int UNRES = 8;

    protected LockManager MWLock;
    protected BitSet transactionBit;

    short f_flag = 1;
    short c_flag = 1;
    short r_flag = 0;


    //flight server properties
    String f_name = "flight";
    String f_host = "localhost";
    int f_port = 8080;

    //flight server replica properties
    String frep_name = "flightrep";
    String frep_host = "localhost";
    int frep_port = 7080;

    //car server properties
    String c_name = "car";
    String c_host = "localhost";
    int c_port = 8082;

    //car server replica properties
    String crep_name = "carrep";
    String crep_host = "localhost";
    int crep_port = 7082;

    //room server properties
    String r_name = "room";
    String r_host = "localhost";
    int r_port = 8084;

    //room server replica properties
    String rrep_name = "roomrep";
    String rrep_host = "localhost";
    int rrep_port = 7084;

    String configFile = "middletcp.xml";

    //code for Client imported from server
    protected RMHashtable m_itemHT = new RMHashtable();

    TimeToLive[] ttl = new TimeToLive[1024];
    //Transaction Manager
    TxnManager txnManager = null;
    MidBroadcast broadcast = null;


    //constructor that creates proxies to each server
    public ResourceManagerImpl() {
        this.MWLock = new LockManager();
        this.txnManager = new TxnManager();
        //this is for the method abort
        //once the method abort is turned on, we stop adding
        //any additional transactions to the transaction manager
        this.transactionBit = new BitSet(1024);

        try {
            broadcast = new MidBroadcast(configFile, this);
            Thread jgroupThread = new Thread(broadcast);
            jgroupThread.start();
        }
        catch (Exception e) {
            e.printStackTrace();
        }


        if (f_flag == 1) {
            try {
                flightPC  = new WSClient(f_name, f_host, f_port);
                flightProxyBackup = new WSClient(frep_name,frep_host,frep_port);
                flightProxy = flightPC;
                System.out.println("middleware is connected to the flight server: " +f_host + " " +f_port);

            } catch (MalformedURLException e) {
                System.out.println("Connecting to the flight server");
            }
        }

        if (c_flag == 1) {
            try {
                carPC = new WSClient(c_name, c_host, c_port);
                carProxyBackup = new WSClient(crep_name,crep_host,crep_port);
                carProxy = carPC;
            } catch (MalformedURLException e) {
                System.out.println("Connecting to the car server " + c_host + " "+ c_port);
            }
        }

        if (r_flag == 1) {
            try {
                roomPC = new WSClient(r_name, r_host, r_port);
                roomProxyBackup = new WSClient(rrep_name,rrep_host,rrep_port);
                roomProxy = roomPC;
            } catch (MalformedURLException e) {
                System.out.println("Connecting to the room server");
            }
        }

        if (f_flag == 1) {

            if (c_flag == 0) {
                carProxy = flightProxy;
            }

            if (r_flag == 0) {
                roomProxy = flightProxy;
            }
        }
        else if (c_flag == 1) {

            if (f_flag == 0) {
                flightProxy = carProxy;
            }
            if (r_flag == 0) {
                roomProxy = carProxy;
            }
        }

        else if (r_flag == 1) {

            if (f_flag == 0) {
                flightProxy = roomProxy;
            }
            if (c_flag == 0) {
                carProxy = roomProxy;
            }
        }

//        SignalMessage ping = new SignalMessage(this);
//        Thread t = new Thread(ping);
//        t.start();

    }

    @Override
    public int start(){
        Integer txnId = txnManager.newTxn();

        String command = "start";

        //only start ttl when it is the coordinator
        //if (broadcast.PCBit.get(0)) {
        ttl[txnId - 1] = new TimeToLive(txnId, this);
        Thread t = new Thread(ttl[txnId - 1]);
        t.start();
        //}
        Trace.info("Starting a new transaction with ID : "+txnId);

        sendCommand(command);

        return txnId;
    }

    // Basic operations on RMItem //

    // Read a data item.
    protected RMItem readData(int id, String key) {
        synchronized(m_itemHT) {
            return (RMItem) m_itemHT.get(key);
        }
    }

    // Write a data item.
    protected void writeData(int id, String key, RMItem value) {
        synchronized(m_itemHT) {
            m_itemHT.put(key, value);
        }
    }

    // Remove the item out of storage.
    protected RMItem removeData(int id, String key) {
        synchronized(m_itemHT) {
            return (RMItem) m_itemHT.remove(key);
        }
    }
    //tells JGroup to broadcast
    protected void sendCommand(String command) {
        if (broadcast.PCBit.get(0)) {
            broadcast.addCommand(command);
            broadcast.bit.set(0);
        }
    }
    private int getStatusCode(URL url) throws IOException {

        HttpURLConnection http = (HttpURLConnection)url.openConnection();
        http.connect();
        int statusCode = http.getResponseCode();
        return statusCode;
    }



    // Reserve an item.
    protected boolean reserveItem(int id, int customerId, String location, String key, int itemInfo) {
        //get item info
        int count = -1;
        int price = -1;
        switch(itemInfo){
            case FLIGHT:
                try {
                    count = flightProxy.proxy.queryFlight(id, Integer.parseInt(location));
                    price = flightProxy.proxy.queryFlightPrice(id, Integer.parseInt(location));
                }catch (Exception e){
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");

                    try{
                        if (getStatusCode(flightProxyBackup.wsdlLocation) == 200) {
                            flightProxy = flightProxyBackup;
                        }
                        System.out.println("connected to its replica");
                        //proceeding on replica
                        count = flightProxy.proxy.queryFlight(id, Integer.parseInt(location));
                        price = flightProxy.proxy.queryFlightPrice(id, Integer.parseInt(location));

                    } catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try{
                            if (getStatusCode(flightPC.wsdlLocation) == 200) {
                                flightProxy = flightPC;
                            }
                            System.out.println("connected to its replica");
                            //proceeding on replica
                            count = flightProxy.proxy.queryFlight(id, Integer.parseInt(location));
                            price = flightProxy.proxy.queryFlightPrice(id, Integer.parseInt(location));
                        } catch (IOException e2) {
                            e2.printStackTrace();
                        }
                    }
                }
                break;
            case CAR:
                try {
                    count = carProxy.proxy.queryCars(id, location);
                    price = carProxy.proxy.queryCarsPrice(id, location);
                } catch (Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        if (getStatusCode(carProxyBackup.wsdlLocation) == 200) {
                            carProxy = carProxyBackup;
                        }
                        System.out.println("connected to its replica");
                        //proceeding on replica
                        count = carProxy.proxy.queryFlight(id, Integer.parseInt(location));
                        price = carProxy.proxy.queryFlightPrice(id, Integer.parseInt(location));
                    } catch (IOException e1) {
                        e1.printStackTrace();
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(carPC.wsdlLocation) == 200) {
                                carProxy = carPC;
                            }
                            System.out.println("connected to its replica");
                            //proceeding on replica
                            count = carProxy.proxy.queryFlight(id, Integer.parseInt(location));
                            price = carProxy.proxy.queryFlightPrice(id, Integer.parseInt(location));
                        } catch (IOException e2) {
                            e2.printStackTrace();
                        }
                    }
                }
                    break;
            case ROOM:
                try {
                    count = roomProxy.proxy.queryRooms(id, location);
                    price = roomProxy.proxy.queryRoomsPrice(id, location);
                }
                catch (Exception e){
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        if (getStatusCode(roomProxyBackup.wsdlLocation) == 200) {
                            roomProxy = roomProxyBackup;
                        }
                        System.out.println("connected to its replica");
                        //proceeding on replica
                        count = roomProxy.proxy.queryFlight(id, Integer.parseInt(location));
                        price = roomProxy.proxy.queryFlightPrice(id, Integer.parseInt(location));
                    } catch (IOException e1) {
                        e1.printStackTrace();
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(roomPC.wsdlLocation) == 200) {
                                roomProxy = roomPC;
                            }
                            System.out.println("connected to its replica");
                            //proceeding on replica
                            count = roomProxy.proxy.queryFlight(id, Integer.parseInt(location));
                            price = roomProxy.proxy.queryFlightPrice(id, Integer.parseInt(location));
                        } catch (IOException e2) {
                            e2.printStackTrace();
                        }
                    }
                }
                break;
        }

        if (count == -1) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key  + ") failed: item doesn't exist.");
            return false;
        }
        Trace.info("RM::reserveItem(" + id + ", " + customerId + ", "
                + key + ", " + location + ") called.");
        // Read customer object if it exists (and read lock it).
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") failed: customer doesn't exist.");
            return false;
        }

        if (count == 0) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") failed: no more items.");
            return false;
        } else {

            // Decrease the number of available items in the storage.
            boolean update = true;

            switch (itemInfo) {
                case FLIGHT:
                    try {
                        update = flightProxy.proxy.updateItemInfo(id, key, RES);
                    }catch (Exception e) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                        try {
                            if (getStatusCode(flightProxyBackup.wsdlLocation) == 200) {
                                flightProxy = flightProxyBackup;
                            }
                            System.out.println("connected to its replica");
                            //proceeding on replica
                            update = flightProxy.proxy.updateItemInfo(id, key, RES);

                        } catch (IOException e1) {
                            e1.printStackTrace();
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                            try {
                                if (getStatusCode(flightPC.wsdlLocation) == 200) {
                                    flightProxy = flightPC;
                                }
                                System.out.println("connected to its replica");
                                //proceeding on replica
                                update = flightProxy.proxy.updateItemInfo(id, key, RES);
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }
                    break;
                case CAR:
                    try {
                        update = carProxy.proxy.updateItemInfo(id, key, RES);
                    }catch (Exception e) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                        try {
                            if (getStatusCode(carProxyBackup.wsdlLocation) == 200) {
                                carProxy = carProxyBackup;
                            }
                            System.out.println("connected to its replica");
                            //proceeding on replica
                            update = carProxy.proxy.updateItemInfo(id, key, RES);

                        } catch (IOException e1) {
                            e1.printStackTrace();
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                            try {
                                if (getStatusCode(carPC.wsdlLocation) == 200) {
                                    carProxy = carPC;
                                }
                                System.out.println("connected to its replica");
                                //proceeding on replica
                                update = carProxy.proxy.updateItemInfo(id, key, RES);
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }
                    break;
                case ROOM:
                    try {
                        update = roomProxy.proxy.updateItemInfo(id, key, RES);
                    }catch (Exception e) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                        try {
                            if (getStatusCode(roomProxyBackup.wsdlLocation) == 200) {
                                roomProxy = roomProxyBackup;
                            }
                            System.out.println("connected to its replica");
                            //proceeding on replica
                            update = roomProxy.proxy.updateItemInfo(id, key, RES);

                        } catch (IOException e1) {
                            e1.printStackTrace();
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                            try {
                                if (getStatusCode(roomPC.wsdlLocation) == 200) {
                                    roomProxy = roomPC;
                                }
                                System.out.println("connected to its replica");
                                //proceeding on replica
                                update = roomProxy.proxy.updateItemInfo(id, key, RES);
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }
                    break;
            }

            if (!update){
                Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                        + key + ", " + location + ") failed: update item info.");
                return false;
            }
            else {
                // Do reservation
                cust.reserve(key, location, price, itemInfo, id); //change location maybe
                writeData(id, cust.getKey(), cust);
                Vector cmd = cmdToVect(CUST,UNRES,customerId);
                cmd.add(Integer.parseInt(location));
                cmd.add(key);
                cmd.add(itemInfo);
                this.txnManager.setNewUpdateItem(id,cmd);
            }

            Trace.info("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") OK.");
            return true;
        }
    }

    protected boolean unReserveItem(int id, int customerId, String location, String key, int itemInfo) {
        Trace.info("RM::unreserveItem(" + id + ", " + customerId + ", "
                + key + ", " + location + ") called.");
        // Read customer object if it exists (and read lock it).
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::unreserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") failed: customer doesn't exist.");
            return false;
        } else {

            // Decrease the number of available items in the storage --> for primary copy.
            // the replica directly delete customer data without touching the RMs
            boolean update = true;
            if (broadcast.PCBit.get(0)) {
                switch (itemInfo) {
                    case FLIGHT:
                        try {
                            update = flightProxy.proxy.updateItemInfo(id, key, UNRES);
                        }catch (Exception e) {
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                            try {
                                if (getStatusCode(flightProxyBackup.wsdlLocation) == 200) {
                                    flightProxy = flightProxyBackup;
                                }
                                System.out.println("connected to its replica");
                                //proceeding on replica
                                update = flightProxy.proxy.updateItemInfo(id, key, UNRES);

                            } catch (IOException e1) {
                                e1.printStackTrace();
                                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                                try {
                                    if (getStatusCode(flightPC.wsdlLocation) == 200) {
                                        flightProxy = flightPC;
                                    }
                                    System.out.println("connected to its replica");
                                    //proceeding on replica
                                    update = flightProxy.proxy.updateItemInfo(id, key, UNRES);
                                } catch (IOException e2) {
                                    e2.printStackTrace();
                                }
                            }
                        }
                        break;
                    case CAR:
                        try {
                            update = carProxy.proxy.updateItemInfo(id, key, UNRES);
                        }catch (Exception e) {
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                            try {
                                if (getStatusCode(carProxyBackup.wsdlLocation) == 200) {
                                    carProxy = carProxyBackup;
                                }
                                System.out.println("connected to its replica");
                                //proceeding on replica
                                update = carProxy.proxy.updateItemInfo(id, key, UNRES);

                            } catch (IOException e1) {
                                e1.printStackTrace();
                                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                                try {
                                    if (getStatusCode(carPC.wsdlLocation) == 200) {
                                        carProxy = carPC;
                                    }
                                    System.out.println("connected to its replica");
                                    //proceeding on replica
                                    update = carProxy.proxy.updateItemInfo(id, key, UNRES);
                                } catch (IOException e2) {
                                    e2.printStackTrace();
                                }
                            }
                        }
                        break;
                    case ROOM:
                        try {
                            update = roomProxy.proxy.updateItemInfo(id, key, UNRES);
                        }catch (Exception e) {
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                            try {
                                if (getStatusCode(roomProxyBackup.wsdlLocation) == 200) {
                                    roomProxy = roomProxyBackup;
                                }
                                System.out.println("connected to its replica");
                                //proceeding on replica
                                update = roomProxy.proxy.updateItemInfo(id, key, UNRES);

                            } catch (IOException e1) {
                                e1.printStackTrace();
                                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                                try {
                                    if (getStatusCode(roomPC.wsdlLocation) == 200) {
                                        roomProxy = roomPC;
                                    }
                                    System.out.println("connected to its replica");
                                    //proceeding on replica
                                    update = roomProxy.proxy.updateItemInfo(id, key, UNRES);
                                } catch (IOException e2) {
                                    e2.printStackTrace();
                                }
                            }
                        }
                        break;
                }
            }
            if (!update){
                Trace.warn("RM::unreserveItem(" + id + ", " + customerId + ", "
                        + key + ", " + location + ") failed: update item info.");
                return false;
            }
            else {
                // Do unreservation
                cust.unreserve(key); //change location maybe
                writeData(id, cust.getKey(), cust);

            }

            Trace.info("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") OK.");
            return true;
        }
    }
    protected Vector cmdToVect(int RMType, int queryType, int itemNumOrLocation){
        Vector cmd = new Vector();
        cmd.add(RMType);
        cmd.add(queryType);
        cmd.add(itemNumOrLocation);

        return cmd;
    }

    @Override
    public boolean addFlight(int id, int flightNumber, int numSeats, int flightPrice) {

        boolean flightAdded = false;
        try {

            flightAdded = flightProxy.proxy.addFlight(id, flightNumber, numSeats, flightPrice);

        }catch (client.DeadlockException_Exception e){
            System.err.println("DeadlockException: " + e.getMessage());
            return false;
        }
        catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(flightProxyBackup.wsdlLocation) == 200) {
                    flightProxy = flightProxyBackup;
                }
                System.out.println("connected to its replica");
                //proceeding on replica
                flightAdded = flightProxy.proxy.addFlight(id, flightNumber, numSeats, flightPrice);

            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try {
                    if (getStatusCode(flightPC.wsdlLocation) == 200) {
                        flightProxy = flightPC;
                    }
                    System.out.println("connected to its replica");
                    //proceeding on replica
                    flightAdded = flightProxy.proxy.addFlight(id, flightNumber, numSeats, flightPrice);
                } catch (IOException e2) {
                    e2.printStackTrace();
                    //exit program?
                } catch (DeadlockException_Exception e2) {
                    System.err.println("DeadlockException: " + e.getMessage());
                    return false;
                }
            } catch (DeadlockException_Exception e1) {
                System.err.println("DeadlockException: " + e.getMessage());
                return false;
            }
        }

        ttl[id-1].pushItem(id);

        if (flightAdded) {
            System.out.println("SENT the addFlight command to the flight server:" + f_host + ":" + f_port);

            //Set the cmd to delete because it needs to be deleted in the rollback
            Vector cmd = cmdToVect(FLIGHT, DEL, flightNumber);
            this.txnManager.setNewUpdateItem(id, cmd);

            //set active RM list
            this.txnManager.enlist(id, FLIGHT);

            String command = String.format("newflight,%d,%d,%d,%d,%b", id, flightNumber, numSeats, flightPrice, flightAdded);
            //if this is the primary copy
            sendCommand(command);

            return flightAdded;
        }
        else {

            System.out.println("FAIL to sent to flight server");
            return flightAdded;
        }

    }

    @Override
    public boolean deleteFlight(int id, int flightNumber) {

        boolean flightDeleted = false;
        int seats = 0;
        int price = 0;
        //check price and number of seats before deleting
        try {
            seats = flightProxy.proxy.queryFlight(id, flightNumber);
            price = flightProxy.proxy.queryFlightPrice(id, flightNumber);
            flightDeleted = flightProxy.proxy.deleteFlight(id, flightNumber);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(flightProxyBackup.wsdlLocation) == 200){
                    flightProxy = flightProxyBackup;
                }
                System.out.println("connected to its replica");

                seats = flightProxy.proxy.queryFlight(id, flightNumber);
                price = flightProxy.proxy.queryFlightPrice(id, flightNumber);
                flightDeleted = flightProxy.proxy.deleteFlight(id, flightNumber);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(flightPC.wsdlLocation) == 200){
                        flightProxy = flightPC;
                    }
                    System.out.println("connected to its replica");
                    seats = flightProxy.proxy.queryFlight(id, flightNumber);
                    price = flightProxy.proxy.queryFlightPrice(id, flightNumber);
                    flightDeleted = flightProxy.proxy.deleteFlight(id, flightNumber);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);
        if (flightDeleted) {
            Vector cmd = cmdToVect(FLIGHT,ADD,flightNumber);
            cmd.add(seats);
            cmd.add(price);
            this.txnManager.setNewUpdateItem(id,cmd);

            this.txnManager.enlist(id,FLIGHT);

            System.out.println("DELETED flight " + flightNumber);

            //send to replica
            String command = String.format("deleteflight,%d,%d,%b",id,flightNumber,flightDeleted);
            sendCommand(command);
        }
        else {
            System.out.println("FAIL to delete flight");
        }

        return flightDeleted;
    }

    @Override
    public int queryFlight(int id, int flightNumber) {
        int flightNum =0;
        try {
            flightNum = flightProxy.proxy.queryFlight(id, flightNumber);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(flightProxyBackup.wsdlLocation) == 200){
                    flightProxy = flightProxyBackup;
                }
                System.out.println("connected to its replica");

                flightNum = flightProxy.proxy.queryFlight(id, flightNumber);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(flightPC.wsdlLocation) == 200){
                        flightProxy = flightPC;
                    }
                    System.out.println("connected to its replica");
                    flightNum = flightProxy.proxy.queryFlight(id, flightNumber);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);
        if (flightNum > 0) {
            System.out.println("QUERY the flight with ID:" + id);
        }
        else {
            Trace.warn("Cannot query the flight# " + id);
        }
        this.txnManager.enlist(id,FLIGHT);

        String command = String.format("queryflight,%d,%d",id,flightNumber);

        sendCommand(command);

        return flightNum;
    }

    @Override
    public int queryFlightPrice(int id, int flightNumber) {

        int flightPrice =0;
        try {
            flightPrice = flightProxy.proxy.queryFlightPrice(id, flightNumber);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(flightProxyBackup.wsdlLocation) == 200){
                    flightProxy = flightProxyBackup;
                }
                System.out.println("connected to its replica");

                flightPrice = flightProxy.proxy.queryFlightPrice(id, flightNumber);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(flightPC.wsdlLocation) == 200){
                        flightProxy = flightPC;
                    }
                    System.out.println("connected to its replica");
                    flightPrice = flightProxy.proxy.queryFlightPrice(id, flightNumber);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);
        System.out.println("QUERY the flight price with ID: " + id);

        this.txnManager.enlist(id,FLIGHT);

        String command = String.format("queryflightprice,%d,%d",id,flightNumber);
        sendCommand(command);

        return flightPrice;
    }

    @Override
    public boolean addCars(int id, String location, int numCars, int carPrice) {

        boolean carsAdded = false;
        try {
            carsAdded = carProxy.proxy.addCars(id, location, numCars, carPrice);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(carProxyBackup.wsdlLocation) == 200){
                    carProxy = carProxyBackup;
                }
                System.out.println("connected to its replica");

                carsAdded = carProxy.proxy.addCars(id, location, numCars, carPrice);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(carPC.wsdlLocation) == 200){
                        carProxy = carPC;
                    }
                    System.out.println("connected to its replica");
                    carsAdded = carProxy.proxy.addCars(id, location, numCars, carPrice);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }

        ttl[id-1].pushItem(id);
        if (carsAdded) {
            System.out.println("SENT the addCar command to the car server:" + c_host + ":" + c_port);
            //Set the cmd to delete because it needs to be deleted in the rollback
            Vector cmd = cmdToVect(CAR,DEL,Integer.parseInt(location));
            this.txnManager.setNewUpdateItem(id,cmd);

            //set active RM list
            this.txnManager.enlist(id,CAR);

            String command = String.format("newcar,%d,%s,%d,%d,%b", id, location, numCars, carPrice, carsAdded);
            //if this is the primary copy
            sendCommand(command);
        }
        else {
            System.out.println("FAIL to add cars");
        }
        return carsAdded;
    }

    @Override
    public boolean deleteCars(int id, String location) {

        boolean carsDeleted = false;
        int num = 0;
        int price = 0;

        try {
            num = carProxy.proxy.queryCars(id,location);
            price = carProxy.proxy.queryCarsPrice(id,location);
            carsDeleted = carProxy.proxy.deleteCars(id, location);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(carProxyBackup.wsdlLocation) == 200){
                    carProxy = carProxyBackup;
                }
                System.out.println("connected to its replica");

                num = carProxy.proxy.queryCars(id,location);
                price = carProxy.proxy.queryCarsPrice(id,location);
                carsDeleted = carProxy.proxy.deleteCars(id, location);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(carPC.wsdlLocation) == 200){
                        carProxy = carPC;
                    }
                    System.out.println("connected to its replica");
                    num = carProxy.proxy.queryCars(id,location);
                    price = carProxy.proxy.queryCarsPrice(id,location);
                    carsDeleted = carProxy.proxy.deleteCars(id, location);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);
        if(carsDeleted) {
            Vector cmd = cmdToVect(CAR,ADD,Integer.parseInt(location));
            cmd.add(num);
            cmd.add(price);
            this.txnManager.setNewUpdateItem(id,cmd);

            this.txnManager.enlist(id,CAR);

            String command = String.format("deletecar,%d,%s,%b",id,location,carsDeleted);
            //if this is the primary copy
            sendCommand(command);

            System.out.println("DELETE cars " + id);
        }
        else {
            System.out.println("FAIL to delete cars ");
        }

        return carsDeleted;
    }

    @Override
    public int queryCars(int id, String location) {

        int carNum =0;
        carNum = carProxy.proxy.queryCars(id, location);
        try {
            carNum = carProxy.proxy.queryCars(id, location);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(carProxyBackup.wsdlLocation) == 200){
                    carProxy = carProxyBackup;
                }
                System.out.println("connected to its replica");

                carNum = carProxy.proxy.queryCars(id, location);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(carPC.wsdlLocation) == 200){
                        carProxy = carPC;
                    }
                    System.out.println("connected to its replica");
                    carNum = carProxy.proxy.queryCars(id, location);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);
        System.out.println("QUERY the car with ID: " + id);

        this.txnManager.enlist(id,CAR);

        String command = String.format("querycar,%d,%s",id,location);
        sendCommand(command);

        return carNum;
    }

    @Override
    public int queryCarsPrice(int id, String location) {

        int carPrice = 0;
        try {
            carPrice = carProxy.proxy.queryCarsPrice(id, location);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(carProxyBackup.wsdlLocation) == 200){
                    carProxy = carProxyBackup;
                }
                System.out.println("connected to its replica");

                carPrice = carProxy.proxy.queryCarsPrice(id, location);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(carPC.wsdlLocation) == 200){
                        carProxy = carPC;
                    }
                    System.out.println("connected to its replica");
                    carPrice = carProxy.proxy.queryCarsPrice(id, location);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);
        System.out.println("QUERY the car price with ID: " + id);

        this.txnManager.enlist(id,CAR);

        String command = String.format("querycar,%d,%s",id,location);
        sendCommand(command);

        return carPrice;
    }

    @Override
    public boolean addRooms(int id, String location, int numRooms, int roomPrice) {

        boolean roomsAdded = false;

        try {
            roomsAdded = roomProxy.proxy.addRooms(id, location, numRooms, roomPrice);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(roomProxyBackup.wsdlLocation) == 200){
                    roomProxy = roomProxyBackup;
                }
                System.out.println("connected to its replica");

                roomsAdded = roomProxy.proxy.addRooms(id, location, numRooms, roomPrice);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(roomPC.wsdlLocation) == 200){
                        roomProxy = roomPC;
                    }
                    System.out.println("connected to its replica");
                    roomsAdded = roomProxy.proxy.addRooms(id, location, numRooms, roomPrice);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);
        if (roomsAdded) {
            System.out.println("EXECUTE the addRoom command to the room server: "+r_host +":"+r_port);
            Vector cmd = cmdToVect(ROOM,DEL,Integer.parseInt(location));
            this.txnManager.setNewUpdateItem(id,cmd);

            this.txnManager.enlist(id,ROOM);
            String command = String.format("newroom,%d,%s,%d,%d,%b", id, location, numRooms, roomPrice, roomsAdded);
            //if this is the primary copy
            sendCommand(command);
        }
        else {
            System.out.println("FAIL to add rooms to the room server: "+r_host + ":" +r_port);
        }

        return roomsAdded;
    }

    @Override
    public boolean deleteRooms(int id, String location) {
        int num = 0;
        int price = 0;
        boolean roomDeleted = false;

        try {
            num = roomProxy.proxy.queryRooms(id,location);
            price = roomProxy.proxy.queryRoomsPrice(id,location);
            roomDeleted = roomProxy.proxy.deleteRooms(id, location);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(roomProxyBackup.wsdlLocation) == 200){
                    roomProxy = roomProxyBackup;
                }
                System.out.println("connected to its replica");

                num = roomProxy.proxy.queryRooms(id,location);
                price = roomProxy.proxy.queryRoomsPrice(id,location);
                roomDeleted = roomProxy.proxy.deleteRooms(id, location);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(roomPC.wsdlLocation) == 200){
                        roomProxy = roomPC;
                    }
                    System.out.println("connected to its replica");
                    num = roomProxy.proxy.queryRooms(id,location);
                    price = roomProxy.proxy.queryRoomsPrice(id,location);
                    roomDeleted = roomProxy.proxy.deleteRooms(id, location);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);
        if (roomDeleted) {

            Vector cmd = cmdToVect(ROOM,ADD,Integer.parseInt(location));
            cmd.add(num);
            cmd.add(price);
            this.txnManager.setNewUpdateItem(id,cmd);

            this.txnManager.enlist(id,ROOM);
            this.txnManager.enlist(id,ROOM);
            System.out.println("EXECUTE the deleteRoom command to the rooom server: "+r_host + ":" +r_port);

            String command = String.format("deleteroom,%d,%s,%b",id,location,roomDeleted);
            sendCommand(command);
        }
        else {
            System.out.println("FAIL to delete rooms");
        }
        return roomDeleted;
    }

    @Override
    public int queryRooms(int id, String location) {

        int roomquery = 0;
        roomquery = roomProxy.proxy.queryRooms(id, location);
        try {
            roomquery = roomProxy.proxy.queryRooms(id, location);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(roomProxyBackup.wsdlLocation) == 200){
                    roomProxy = roomProxyBackup;
                }
                System.out.println("connected to its replica");
                roomquery = roomProxy.proxy.queryRooms(id, location);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(roomPC.wsdlLocation) == 200){
                        roomProxy = roomPC;
                    }
                    System.out.println("connected to its replica");
                    roomquery = roomProxy.proxy.queryRooms(id, location);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);

        System.out.println("QUERY the room with ID: " + id);

        this.txnManager.enlist(id,ROOM);

        String command = String.format("queryroom,%d,%s",id,location);
        sendCommand(command);

        return roomquery;
    }

    @Override
    public int queryRoomsPrice(int id, String location) {
        int roomPrice = 0;
        try {
            roomPrice = roomProxy.proxy.queryRoomsPrice(id, location);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(roomProxyBackup.wsdlLocation) == 200){
                    roomProxy = roomProxyBackup;
                }
                System.out.println("connected to its replica");
                roomPrice = roomProxy.proxy.queryRoomsPrice(id, location);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(roomPC.wsdlLocation) == 200){
                        roomProxy = roomPC;
                    }
                    System.out.println("connected to its replica");
                    roomPrice = roomProxy.proxy.queryRoomsPrice(id, location);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);
        System.out.println("QUERY the room PRICE with ID:" + id);

        this.txnManager.enlist(id,ROOM);

        String command = String.format("queryroomprice,%d,%s",id,location);
        sendCommand(command);

        return roomPrice;
    }

    /** Do the customer logic in the middleware **/
    @Override
    public int newCustomer(int id) {
        Trace.info("INFO: RM::newCustomer(" + id + ") called.");
        // Generate a globally unique Id for the new customer.
        int customerId = Integer.parseInt(String.valueOf(id) +
                String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
                String.valueOf(Math.round(Math.random() * 100 + 1)));
        String strData = "customer,"+customerId;

        try {
            MWLock.Lock(id,strData,WRITE);

        } catch (server.LockManager.DeadlockException e) {

            return -1;
        }

        Customer cust = new Customer(customerId);
        ttl[id-1].pushItem(id);
        writeData(id, cust.getKey(), cust);
        Trace.info("RM::newCustomer(" + id + ") OK: " + customerId);
        //add command to txn command list
        Vector cmd = cmdToVect(CUST,DEL,customerId);
        this.txnManager.setNewUpdateItem(id,cmd);

        this.txnManager.enlist(id,CUST);

        String command = String.format("newcustomer,%d",id);
        sendCommand(command);

        return customerId;
    }

    @Override
    public boolean newCustomerId(int id, int customerId) {
        Trace.info("RM::newCustomer(" + id + ", " + customerId + ") called.");
        String strData = "customer,"+customerId;

        String command = String.format("newcustomerid,%d,%d", id, customerId);


        try {
            MWLock.Lock(id,strData,WRITE);
        } catch (server.LockManager.DeadlockException e) {
            e.printStackTrace();
            return false;
        }
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        ttl[id-1].pushItem(id);
        if (cust == null) {
            cust = new Customer(customerId);
            writeData(id, cust.getKey(), cust);
            Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") OK.");

            //bit is on --> abort method
            //bit is off --> not abort => add the the transaction manager
            if (!transactionBit.get(id)) {
                Vector cmd = cmdToVect(CUST, DEL, customerId);
                this.txnManager.setNewUpdateItem(id, cmd);
                this.txnManager.enlist(id, CUST);
            }

            sendCommand(command);

            return true;
        } else {
            Trace.info("INFO: RM::newCustomer(" + id + ", " +
                    customerId + ") failed: customer already exists.");
            return false;
        }
    }

    @Override
    public boolean deleteCustomer(int id, int customerId) {
        Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") called.");
        String strData = "customer,"+customerId;

        try {
            MWLock.Lock(id,strData,WRITE);
        } catch (server.LockManager.DeadlockException e) {
            e.printStackTrace();
            return false;
        }
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        ttl[id-1].pushItem(id);
        if (cust == null) {
            Trace.warn("RM::deleteCustomer(" + id + ", "
                    + customerId + ") failed: customer doesn't exist.");
            return false;
        } else {
            // Increase the reserved numbers of all reservable items that
            // the customer reserved.
            RMHashtable reservationHT = cust.getReservations();
            for (Enumeration e = reservationHT.keys(); e.hasMoreElements();) {
                String reservedKey = (String) (e.nextElement());
                ReservedItem reservedItem = cust.getReservedItem(reservedKey);
                Trace.info("RM::deleteCustomer(" + id + ", " + customerId + "): "
                        + "deleting " + reservedItem.getCount() + " reservations "
                        + "for item " + reservedItem.getKey());
                int itemId = reservedItem.getId();
                int count = reservedItem.getCount();
                String location = reservedItem.getLocation();

                Trace.info("RM::deleteCustomer(" + id + ", " + customerId + "): ");
                //flight
                if(reservedItem.getType() == 1){
                    boolean flightUpdated = false;
                    try {
                        flightUpdated = flightProxy.proxy.updateDeleteCustomer(itemId, reservedItem.getKey(), count);
                    }catch (Exception e0){
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                        try {
                            if (getStatusCode(flightProxyBackup.wsdlLocation) == 200){
                                flightProxy = flightProxyBackup;
                            }
                            System.out.println("connected to its replica");
                            flightUpdated = flightProxy.proxy.updateDeleteCustomer(itemId, reservedItem.getKey(), count);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                            try{
                                if (getStatusCode(flightPC.wsdlLocation) == 200){
                                    flightProxy = flightPC;
                                }
                                System.out.println("connected to its replica");
                                flightUpdated = flightProxy.proxy.updateDeleteCustomer(itemId, reservedItem.getKey(), count);
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }
                    if(flightUpdated){

                        if (!transactionBit.get(id)) {
                            Vector cmd = cmdToVect(FLIGHT, RES, Integer.parseInt(location));
                            cmd.add(count);
                            this.txnManager.setNewUpdateItem(id, cmd);
                            this.txnManager.enlist(id, FLIGHT);
                        }
                    }
                    else{
                        //error
                        return false;
                    }
                }
                //car
                else if (reservedItem.getType() == 2){
                    boolean carUpdated = false;
                    try {
                        carUpdated = carProxy.proxy.updateDeleteCustomer(itemId,reservedItem.getKey(),count);
                    }catch (Exception e0){
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                        try {
                            if (getStatusCode(carProxyBackup.wsdlLocation) == 200){
                                carProxy = carProxyBackup;
                            }
                            System.out.println("connected to its replica");
                            carUpdated = carProxy.proxy.updateDeleteCustomer(itemId,reservedItem.getKey(),count);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                            try{
                                if (getStatusCode(carPC.wsdlLocation) == 200){
                                    carProxy = carPC;
                                }
                                System.out.println("connected to its replica");
                                carUpdated = carProxy.proxy.updateDeleteCustomer(itemId,reservedItem.getKey(),count);
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }
                    if(carUpdated){

                        if (!transactionBit.get(id)) {
                            Vector cmd = cmdToVect(CAR, RES, Integer.parseInt(location));
                            cmd.add(count);
                            this.txnManager.setNewUpdateItem(id, cmd);
                            this.txnManager.enlist(id, CAR);
                        }
                    }
                    else{
                        //error
                        return false;
                    }
                }
                //room
                else if (reservedItem.getType() == 3){
                    boolean roomUpdated = false;
                    try {
                        roomUpdated = roomProxy.proxy.updateDeleteCustomer(itemId,reservedItem.getKey(),count);
                    }catch (Exception e0){
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                        try {
                            if (getStatusCode(roomProxyBackup.wsdlLocation) == 200){
                                roomProxy = roomProxyBackup;
                            }
                            System.out.println("connected to its replica");
                            roomUpdated = roomProxy.proxy.updateDeleteCustomer(itemId,reservedItem.getKey(),count);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                            try{
                                if (getStatusCode(roomPC.wsdlLocation) == 200){
                                    roomProxy = roomPC;
                                }
                                System.out.println("connected to its replica");
                                roomUpdated = roomProxy.proxy.updateDeleteCustomer(itemId,reservedItem.getKey(),count);
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }
                    if(roomUpdated){

                        if (!transactionBit.get(id)) {
                            Vector cmd = cmdToVect(ROOM, RES, Integer.parseInt(location));
                            cmd.add(count);
                            this.txnManager.setNewUpdateItem(id, cmd);
                            this.txnManager.enlist(id, ROOM);
                        }
                    }
                    else{
                        //error
                        return false;
                    }
                }
            }
            // Remove the customer from the storage.
            removeData(id, cust.getKey());

            if (!transactionBit.get(id)) {
                Vector cmd = cmdToVect(CUST, ADD, customerId);
                this.txnManager.setNewUpdateItem(id, cmd);
            }

            Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") OK.");
            return true;
        }
    }

    @Override
    public String queryCustomerInfo(int id, int customerId) {
        Trace.info("RM::queryCustomerInfo(" + id + ", " + customerId + ") called.");
        String strData = "customer,"+customerId;
        try {
            MWLock.Lock(id,strData,READ);
        } catch (server.LockManager.DeadlockException e) {
            return "WARN: RM::queryCustomerInfo(" + id + ", "
                    + customerId + ") failed: DeadlockException";
        }

        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        ttl[id-1].pushItem(id);
        if (cust == null) {
            Trace.warn("RM::queryCustomerInfo(" + id + ", "
                    + customerId + ") failed: customer doesn't exist.");



            // Returning an empty bill means that the customer doesn't exist.
            return "";
        } else {
            String s = cust.printBill();
            Trace.info("RM::queryCustomerInfo(" + id + ", " + customerId + "): \n");
            System.out.println(s);
            this.txnManager.enlist(id,CUST);

            String command = String.format("querycustomer,%d,%d",id,customerId);

            sendCommand(command);


            return s;
        }
    }

    /** Do the customer logic in the middleware **/

    /** Each customer needs to:
     * reserve flights
     * reserve cars
     * reserve room
     *
     * Thus, inside these methods, they need to call to their respective servers
     * such as flight server 8080, car server 8082 and room server 8084
     * in order to complete the transactions
     * **/

    @Override
    public boolean reserveFlight(int id, int customerId, int flightNumber) {
        /** call methods from the flight server to execute actions **/
        //get flight key

        String key = "";
        try {
            key = flightProxy.proxy.getFlightKey(flightNumber);
        }catch (Exception e0){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(flightProxyBackup.wsdlLocation) == 200){
                    flightProxy = flightProxyBackup;
                }
                System.out.println("connected to its replica");
                key = flightProxy.proxy.getFlightKey(flightNumber);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(flightPC.wsdlLocation) == 200){
                        flightProxy = flightPC;
                    }
                    System.out.println("connected to its replica");
                    key = flightProxy.proxy.getFlightKey(flightNumber);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }

        ttl[id-1].pushItem(id);
        boolean isReserved = reserveItem(id,customerId,String.valueOf(flightNumber),key,FLIGHT);
        String command = String.format("reserveflight,%d,%d,%d,%s",id,customerId,flightNumber,key);
        if (isReserved){
            System.out.println("RESERVATION ADDED TO THE CUSTOMER!!!!!");

            this.txnManager.enlist(id,FLIGHT);


            sendCommand(command);

            return true;
        }
        else{
            //error
            return false;
        }
    }

    private boolean unReserveFlight(int id, int customerId, int flightNumber) {
        /** call methods from the flight server to execute actions **/
        //get flight key
        String key = "";
        try {
            key = flightProxy.proxy.getFlightKey(flightNumber);
        }catch (Exception e0){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(flightProxyBackup.wsdlLocation) == 200){
                    flightProxy = flightProxyBackup;
                }
                System.out.println("connected to its replica");
                key = flightProxy.proxy.getFlightKey(flightNumber);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(flightPC.wsdlLocation) == 200){
                        flightProxy = flightPC;
                    }
                    System.out.println("connected to its replica");
                    key = flightProxy.proxy.getFlightKey(flightNumber);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }

        ttl[id-1].pushItem(id);
        if (unReserveItem(id, customerId, String.valueOf(flightNumber), key, FLIGHT)){
            this.txnManager.enlist(id,FLIGHT);

            return true;
        }
        else{
            //error
            return false;
        }
    }

    @Override
    public boolean reserveCar(int id, int customerId, String location) {
        /** call methods from the car server to execute actions **/
        String key = "";
        try {
            key = carProxy.proxy.getCarKey(location);
        }catch (Exception e0){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(carProxyBackup.wsdlLocation) == 200){
                    carProxy = carProxyBackup;
                }
                System.out.println("connected to its replica");
                key = carProxy.proxy.getCarKey(location);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(carPC.wsdlLocation) == 200){
                        carProxy = carPC;
                    }
                    System.out.println("connected to its replica");
                    key = carProxy.proxy.getCarKey(location);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);
        if (reserveItem(id,customerId,location,key,CAR)){
            this.txnManager.enlist(id,CAR);

            String command = String.format("reservecar,%d,%d,%s,%s",id,customerId,location,key);
            sendCommand(command);
            return true;
        }
        else{
            //error
            return false;
        }
    }

    private boolean unReserveCar(int id, int customerId, String location) {
        /** call methods from the car server to execute actions **/
        String key ="";
        try {
            key = carProxy.proxy.getCarKey(location);
        }catch (Exception e0){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(carProxyBackup.wsdlLocation) == 200){
                    carProxy = carProxyBackup;
                }
                System.out.println("connected to its replica");
                key = carProxy.proxy.getCarKey(location);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(carPC.wsdlLocation) == 200){
                        carProxy = carPC;
                    }
                    System.out.println("connected to its replica");
                    key = carProxy.proxy.getCarKey(location);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);
        if (unReserveItem(id, customerId, location, key, CAR)){
            this.txnManager.enlist(id,CAR);
            return true;
        }
        else{
            //error
            return false;
        }
    }

    @Override
    public boolean reserveRoom(int id, int customerId, String location) {
        /** call methods from the room server to execute actions **/
        String key = "";
        try {
            key = roomProxy.proxy.getRoomKey(location);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(roomProxyBackup.wsdlLocation) == 200){
                    roomProxy = roomProxyBackup;
                }
                System.out.println("connected to its replica");
                key = roomProxy.proxy.getRoomKey(location);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(roomPC.wsdlLocation) == 200){
                        roomProxy = roomPC;
                    }
                    System.out.println("connected to its replica");
                    key = roomProxy.proxy.getRoomKey(location);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);
        if (reserveItem(id, customerId,location, key, ROOM)){

            this.txnManager.enlist(id,ROOM);

            String command = String.format("reserveroom,%d,%d,%s,%s",id,customerId,location,key);
            sendCommand(command);
            return true;
        }
        else{
            //error
            return false;
        }
    }
    private boolean unReserveRoom(int id, int customerId, String location) {
        /** call methods from the room server to execute actions **/
        String key = "";
        try {
            key = roomProxy.proxy.getRoomKey(location);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(roomProxyBackup.wsdlLocation) == 200){
                    roomProxy = roomProxyBackup;
                }
                System.out.println("connected to its replica");
                key = roomProxy.proxy.getRoomKey(location);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(roomPC.wsdlLocation) == 200){
                        roomProxy = roomPC;
                    }
                    System.out.println("connected to its replica");
                    key = roomProxy.proxy.getRoomKey(location);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        ttl[id-1].pushItem(id);
        if (unReserveItem(id, customerId, location, key, ROOM)){

            this.txnManager.enlist(id,ROOM);
            return true;
        }
        else{
            //error
            return false;
        }
    }

    @Override
    public boolean reserveItinerary(int id, int customerId, Vector flightNumbers, String location, boolean car, boolean room) {
        /** call methods from all three servers to execute actions **/
        Iterator it = flightNumbers.iterator();
        ttl[id-1].pushItem(id);

        //for atomicity
        BitSet flights = new BitSet();
        boolean carPassed = false;
        boolean roomPassed = false;
        int i = 0;

        while(it.hasNext()){
            i++;
            if(!(reserveFlight(id,customerId,Integer.parseInt((String) it.next())))){
                flights.set(i);
                //Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " + location + ") failed: no more seats available.");
            }else {
                Trace.info("RESERVED FLIGHT + " + i);
            }
        }
        //there is a car and room
        if (car  &&  room) {
            carPassed = reserveCar(id, customerId, location);
            roomPassed = reserveRoom(id, customerId, location);
            if(carPassed && roomPassed){
                Trace.info("RESERVED CAR AND ROOM");
            }
        }
        //there is a room
        else if (room){
            roomPassed = reserveRoom(id, customerId, location);
            if(roomPassed) {
                Trace.info("RESERVED ROOM");
            }
        }
        //if there is a car
        else if(car){
            carPassed = reserveCar(id, customerId, location);
            if(carPassed) {
                Trace.info("RESERVED CAR");
            }
        }

        //if any of the reservation fails, undo all reservations
        if (flights.nextSetBit(0) != -1 || (!carPassed && car) || (!roomPassed && room)){
            Iterator it2 = flightNumbers.iterator();
            int j =0;
            int failedFlight = flights.nextSetBit(0);
            //unreserve all flights
            while (it2.hasNext()){
                //if the flight did not reserve, do not unreserve
                if (j == failedFlight){
                    failedFlight = flights.nextSetBit(j);
                    j++;
                    continue;
                }
                j++;
                unReserveFlight(id, customerId,Integer.parseInt((String) it2.next()));
                Trace.info("UNRESERVED FLIGHT "+j +" UPON NON-ATOMIC RESERVE ITINERARY FOR TRANSACTION: "+  id);

            }
            //unreserve the car if there was a reservation
            if (carPassed){
                unReserveCar(id,customerId,location);
                Trace.info("UNRESERVED CAR "+location +" UPON NON-ATOMIC RESERVE ITINERARY FOR TRANSACTION: "+  id);

            }
            //unreserve the room if there was a reservation
            else if (roomPassed){
                unReserveRoom(id,customerId,location);
                Trace.info("UNRESERVED ROOM "+location +" UPON NON-ATOMIC RESERVE ITINERARY FOR TRANSACTION: "+  id);
            }
            return false;
        }
        //send command to replica
        System.out.println(flightNumbers);
        String command = String.format("itinerary,%d,%d,",id,customerId);
        flightNumbers.toString();
        Iterator it2 = flightNumbers.iterator();
        while (it2.hasNext()){
            String next = (String) it2.next();
            String flightKey = "";
            int flightPrice = -1;
            try {
                flightKey = this.flightProxy.proxy.getFlightKey(Integer.parseInt(next));
                flightPrice = this.flightProxy.proxy.queryFlightPrice(id,Integer.parseInt(next));
            }catch (Exception e0){
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                try {
                    if (getStatusCode(flightProxyBackup.wsdlLocation) == 200){
                        flightProxy = flightProxyBackup;
                    }
                    System.out.println("connected to its replica");
                    flightKey = this.flightProxy.proxy.getFlightKey(Integer.parseInt(next));
                    flightPrice = this.flightProxy.proxy.queryFlightPrice(id, Integer.parseInt(next));
                } catch (IOException e1) {
                    e1.printStackTrace();
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                    try{
                        if (getStatusCode(flightPC.wsdlLocation) == 200){
                            flightProxy = flightPC;
                        }
                        System.out.println("connected to its replica");
                        flightKey = this.flightProxy.proxy.getFlightKey(Integer.parseInt(next));
                        flightPrice = this.flightProxy.proxy.queryFlightPrice(id, Integer.parseInt(next));
                    } catch (IOException e2) {
                        e2.printStackTrace();
                    }
                }
            }
            if (flightKey.isEmpty() || flightPrice ==-1){
                System.out.println("ERROR: Flight price and flight key sent to replica are wrong");
            }
            command = command + next + "," + flightKey + "," + flightPrice + ",";
        }
        //-1 will delimit the end or the flight numbers
        command = command + String.format("%d,%s,%b,%b,",-1,location,car,room);
        //get the keys to reserve items at the replica
        String carKey = "";
        String roomKey = "";
        int carPrice = -1;
        int roomPrice = -1;
        try {
            carKey = this.carProxy.proxy.getCarKey(location);
            carPrice = this.carProxy.proxy.queryCarsPrice(id,location);
        }catch (Exception e0){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(carProxyBackup.wsdlLocation) == 200){
                    carProxy = carProxyBackup;
                }
                System.out.println("connected to its replica");
                carKey = this.carProxy.proxy.getCarKey(location);
                carPrice = this.carProxy.proxy.queryCarsPrice(id, location);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(carPC.wsdlLocation) == 200){
                        carProxy = carPC;
                    }
                    System.out.println("connected to its replica");
                    carKey = this.carProxy.proxy.getCarKey(location);
                    carPrice = this.carProxy.proxy.queryCarsPrice(id, location);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        try {
            roomKey = this.roomProxy.proxy.getRoomKey(location);
            roomPrice = this.roomProxy.proxy.queryRoomsPrice(id,location);
        }catch (Exception e){
            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
            try {
                if (getStatusCode(roomProxyBackup.wsdlLocation) == 200){
                    roomProxy = roomProxyBackup;
                }
                System.out.println("connected to its replica");
                roomKey = this.roomProxy.proxy.getRoomKey(location);
                roomPrice = this.roomProxy.proxy.queryRoomsPrice(id, location);
            } catch (IOException e1) {
                e1.printStackTrace();
                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                try{
                    if (getStatusCode(roomPC.wsdlLocation) == 200){
                        roomProxy = roomPC;
                    }
                    System.out.println("connected to its replica");
                    roomKey = this.roomProxy.proxy.getRoomKey(location);
                    roomPrice = this.roomProxy.proxy.queryRoomsPrice(id,location);
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        if ( carKey.isEmpty() || carPrice == -1 || roomKey.isEmpty() || roomPrice==-1){
            System.out.println("ERROR: failed to send message to replica");
        }
        else {
            command = command + String.format("%s,%d,%s,%d", carKey, carPrice, roomKey, roomPrice);

            System.out.println("PC command sent on itinerary: " + command);
            sendCommand(command);
        }
        return true;
    }

    @Override
    public String getFlightKey(int flightNumber){
        return "Has to be called from middleware";
    }

    @Override
    public String getCarKey(String location){
        return "Has to be called from middleware";
    }
    @Override
    public String getRoomKey(String location){
        return "Has to be called from middleware";
    }
    @Override
    public boolean updateItemInfo(int id, String key,int resOrUnres){
        Trace.warn("Error: Has to be called from middleware");

        return false;
    }
    @Override
    public boolean updateDeleteCustomer(int id, String key, int count){
        Trace.warn("Error: Has to be called from middleware");

        return false;
    }


    @Override
    public boolean commit(int txnId) {
        //iterate through active RM list and release locks
        ttl[txnId-1].pushCommit(txnId);
        Vector RMlist;
        try{
            RMlist = this.txnManager.activeTxnRM.get(txnId);
        }
        catch (NullPointerException e){

            Trace.warn("NEED TO START TRANSACTION BEFORE CALLING COMMIT");
            return false;
        }
        Iterator it = RMlist.iterator();
        if (RMlist.isEmpty()) {
            Trace.info("RM::NOTHING TO COMMIT FOR ID: "+ txnId);
            return true;
        }

        while (it.hasNext()) {
            //release locks for this RM
            Integer RMType = (Integer) it.next();
            switch (RMType) {
                case FLIGHT:
                    boolean flightCommit = false;
                    try {
                        flightCommit = flightProxy.proxy.commit(txnId);
                    }catch (Exception e0){
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                        try {
                            if (getStatusCode(flightProxyBackup.wsdlLocation) == 200){
                                flightProxy = flightProxyBackup;
                            }
                            System.out.println("connected to its replica");
                            flightCommit = flightProxy.proxy.commit(txnId);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                            try{
                                if (getStatusCode(flightPC.wsdlLocation) == 200){
                                    flightProxy = flightPC;
                                }
                                System.out.println("connected to its replica");
                                flightCommit = flightProxy.proxy.commit(txnId);
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }

                    if(!flightCommit){
                        Trace.warn("ERROR IN FLIGHT RM COMMIT: " + txnId);
                        return false;
                    }
                    break;
                case CAR:
                    boolean carCommit = false;
                    try {
                        carCommit = carProxy.proxy.commit(txnId);
                    }catch (Exception e0){
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                        try {
                            if (getStatusCode(carProxyBackup.wsdlLocation) == 200){
                                carProxy = carProxyBackup;
                            }
                            System.out.println("connected to its replica");
                            carCommit = carProxy.proxy.commit(txnId);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                            try{
                                if (getStatusCode(carPC.wsdlLocation) == 200){
                                    carProxy = carPC;
                                }
                                System.out.println("connected to its replica");
                                carCommit = carProxy.proxy.commit(txnId);
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }

                    if(!carCommit){
                        Trace.warn("ERROR IN CAR RM COMMIT: " + txnId);
                        return false;
                    }
                    break;
                case ROOM:
                    boolean roomCommit = false;
                    try {
                        roomCommit = roomProxy.proxy.commit(txnId);
                    }catch (Exception e){
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                        try {
                            if (getStatusCode(roomProxyBackup.wsdlLocation) == 200){
                                roomProxy = roomProxyBackup;
                            }
                            System.out.println("connected to its replica");
                            roomCommit = roomProxy.proxy.commit(txnId);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                            try{
                                if (getStatusCode(roomPC.wsdlLocation) == 200){
                                    roomProxy = roomPC;
                                }
                                System.out.println("connected to its replica");
                                roomCommit = roomProxy.proxy.commit(txnId);
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }
                    if(!roomCommit){
                        Trace.warn("ERROR IN ROOM RM COMMIT: " + txnId);
                        return false;
                    }
                    break;
                case CUST:
                    //add lock manager to MIDDLEWARE
                    if (txnId > 0) {
                        Trace.info("RM::COMMIT TRANSACTION ID: " + txnId);
                        if (!this.MWLock.UnlockAll(txnId)) {
                            Trace.info("FAILED TO UNLOCK ALL CUSTOMER LOCKS: "+txnId);
                            return false;
                        }
                    }
                    else {
                        Trace.warn("INVALID TXNID: CANNOT COMMIT CUSTOMER: " + txnId);
                        return false;
                    }
                    break;
            }
        }

        //clean up entries in txnmanager upon success
        try {
            this.txnManager.activeTxnRM.remove(txnId);
            this.txnManager.txnCmdList.remove(txnId);
        }catch (NullPointerException e) {
            Trace.warn("ERROR WHEN REMOVING TXNMANAGER ENTRIES AT COMMIT: "+txnId);
            e.printStackTrace();
        }

        String command = "commit,"+txnId;
        sendCommand(command);

        return true;
    }


    @Override
    public boolean abort(int txnId){


        //turn on the transaction bit
        ttl[txnId-1].pushAbort(txnId);
        this.transactionBit.set(txnId);
        //get the commands from the stack of commands and execute them
        Stack cmdList;
        try {
            cmdList = this.txnManager.txnCmdList.get(txnId);
        }
        catch (NullPointerException e){
            e.printStackTrace();
            Trace.warn("NEED TO START TRANSCATION BEFORE CALLING ABORT");
            return false;
        }
        try {
            while (!cmdList.isEmpty()) {
                Vector cmd = (Vector) cmdList.pop();
                Integer RMType = (Integer) cmd.get(0);
                Integer queryType = (Integer) cmd.get(1);
                Integer location = (Integer) cmd.get(2);

                switch (RMType) {
                    case FLIGHT:
                        switch (queryType) {
                            case ADD:
                                Integer seats = (Integer) cmd.get(3);
                                Integer price = (Integer) cmd.get(4);
                                try {
                                    if (!flightProxy.proxy.addFlight(txnId, location, seats, price)) {
                                        Trace.warn("FAILED TO ADDFLIGHT UPON ABORT: " + txnId);
                                        return false;
                                    }
                                } catch (DeadlockException_Exception e) {
                                    Trace.warn("DEADLOCK EXCEPTION UPON ADDFLIGHT IN ABORT: " + txnId);
                                    return false;
                                }
                                catch (Exception e0){
                                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                                    try {
                                        if (getStatusCode(flightProxyBackup.wsdlLocation) == 200){
                                            flightProxy = flightProxyBackup;
                                        }
                                        System.out.println("connected to its replica");
                                        if (!flightProxy.proxy.addFlight(txnId, location, seats, price)) {
                                            Trace.warn("FAILED TO ADDFLIGHT UPON ABORT: " + txnId);
                                            return false;
                                        }
                                    } catch (IOException e1) {
                                        e1.printStackTrace();
                                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                                        try{
                                            if (getStatusCode(flightPC.wsdlLocation) == 200){
                                                flightProxy = flightPC;
                                            }
                                            System.out.println("connected to its replica");
                                            if (!flightProxy.proxy.addFlight(txnId, location, seats, price)) {
                                                Trace.warn("FAILED TO ADDFLIGHT UPON ABORT: " + txnId);
                                                return false;
                                            }
                                        } catch (IOException e2) {
                                            e2.printStackTrace();
                                        } catch (DeadlockException_Exception e) {
                                            e.printStackTrace();
                                        }
                                    } catch (DeadlockException_Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                Trace.info("ADDED NEW FLIGHT WHEN ABORTING: "+txnId);
                                break;
                            case DEL:
                                boolean flightDeleted = false;
                                try {
                                    flightDeleted = flightProxy.proxy.deleteFlight(txnId, location);
                                }catch (Exception e0){
                                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                                    try {
                                        if (getStatusCode(flightProxyBackup.wsdlLocation) == 200){
                                            flightProxy = flightProxyBackup;
                                        }
                                        System.out.println("connected to its replica");
                                        flightDeleted = flightProxy.proxy.deleteFlight(txnId, location);
                                    } catch (IOException e1) {
                                        e1.printStackTrace();
                                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                                        try{
                                            if (getStatusCode(flightPC.wsdlLocation) == 200){
                                                flightProxy = flightPC;
                                            }
                                            System.out.println("connected to its replica");
                                            flightDeleted = flightProxy.proxy.deleteFlight(txnId, location);
                                        } catch (IOException e2) {
                                            e2.printStackTrace();
                                        }
                                    }
                                }
                                if (!flightDeleted) {
                                    Trace.warn("FAILED TO DELETEFLIGHT UPON ABORT: " + txnId);
                                    return false;
                                }
                                break;
                        }
                        break;
                    case CAR:
                        switch (queryType) {
                            case ADD:
                                Integer numCars = (Integer) cmd.get(3);
                                Integer price = (Integer) cmd.get(4);
                                boolean carAdded = false;
                                try {
                                    carAdded = carProxy.proxy.addCars(txnId, String.valueOf(location), numCars, price);
                                }catch (Exception e0){
                                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                                    try {
                                        if (getStatusCode(carProxyBackup.wsdlLocation) == 200){
                                            carProxy = carProxyBackup;
                                        }
                                        System.out.println("connected to its replica");
                                        carAdded = carProxy.proxy.addCars(txnId, String.valueOf(location), numCars, price);
                                    } catch (IOException e1) {
                                        e1.printStackTrace();
                                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                                        try{
                                            if (getStatusCode(carPC.wsdlLocation) == 200){
                                                carProxy = carPC;
                                            }
                                            System.out.println("connected to its replica");
                                            carAdded = carProxy.proxy.addCars(txnId, String.valueOf(location), numCars, price);
                                        } catch (IOException e2) {
                                            e2.printStackTrace();
                                        }
                                    }
                                }
                                if (!carAdded) {
                                    Trace.warn("FAILED TO ADDCAR UPON ABORT: " + txnId);
                                    return false;
                                }

                                break;
                            case DEL:
                                boolean carDeleted = false;
                                try {
                                    carDeleted = carProxy.proxy.deleteCars(txnId, String.valueOf(location));
                                }catch (Exception e0){
                                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                                    try {
                                        if (getStatusCode(carProxyBackup.wsdlLocation) == 200){
                                            carProxy = carProxyBackup;
                                        }
                                        System.out.println("connected to its replica");
                                        carDeleted = carProxy.proxy.deleteCars(txnId, String.valueOf(location));
                                    } catch (IOException e1) {
                                        e1.printStackTrace();
                                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                                        try{
                                            if (getStatusCode(carPC.wsdlLocation) == 200){
                                                carProxy = carPC;
                                            }
                                            System.out.println("connected to its replica");
                                            carDeleted = carProxy.proxy.deleteCars(txnId, String.valueOf(location));
                                        } catch (IOException e2) {
                                            e2.printStackTrace();
                                        }
                                    }
                                }
                                if (!carDeleted) {
                                    Trace.warn("FAILED TO DELETECAR UPON ABORT: " + txnId);
                                    return false;
                                }
                                break;
                        }
                        break;
                    case ROOM:
                        switch (queryType) {
                            case ADD:
                                Integer numRooms = (Integer) cmd.get(3);
                                Integer price = (Integer) cmd.get(4);
                                boolean roomAdded = false;
                                roomAdded = roomProxy.proxy.addRooms(txnId, String.valueOf(location), numRooms, price);
                                try {
                                    roomAdded = roomProxy.proxy.addRooms(txnId, String.valueOf(location), numRooms, price);
                                }catch (Exception e){
                                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                                    try {
                                        if (getStatusCode(roomProxyBackup.wsdlLocation) == 200){
                                            roomProxy = roomProxyBackup;
                                        }
                                        System.out.println("connected to its replica");
                                        roomAdded = roomProxy.proxy.addRooms(txnId, String.valueOf(location), numRooms, price);
                                    } catch (IOException e1) {
                                        e1.printStackTrace();
                                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                                        try{
                                            if (getStatusCode(roomPC.wsdlLocation) == 200){
                                                roomProxy = roomPC;
                                            }
                                            System.out.println("connected to its replica");
                                            roomAdded = roomProxy.proxy.addRooms(txnId, String.valueOf(location), numRooms, price);
                                        } catch (IOException e2) {
                                            e2.printStackTrace();
                                        }
                                    }
                                }
                                if (!roomAdded) {
                                    Trace.warn("FAILED TO ADDROOM UPON ABORT: " + txnId);
                                    return false;
                                }
                                break;
                            case DEL:
                                boolean roomDeleted = false;
                                try {
                                    roomDeleted = roomProxy.proxy.deleteRooms(txnId, String.valueOf(location));
                                }catch (Exception e){
                                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                                    try {
                                        if (getStatusCode(roomProxyBackup.wsdlLocation) == 200){
                                            roomProxy = roomProxyBackup;
                                        }
                                        System.out.println("connected to its replica");
                                        roomDeleted = roomProxy.proxy.deleteRooms(txnId, String.valueOf(location));
                                    } catch (IOException e1) {
                                        e1.printStackTrace();
                                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                                        try{
                                            if (getStatusCode(roomPC.wsdlLocation) == 200){
                                                roomProxy = roomPC;
                                            }
                                            System.out.println("connected to its replica");
                                            roomDeleted = roomProxy.proxy.deleteRooms(txnId, String.valueOf(location));
                                        } catch (IOException e2) {
                                            e2.printStackTrace();
                                        }
                                    }
                                }
                                if (!roomDeleted) {
                                    Trace.warn("FAILED TO DELETEROOM UPON ABORT: " + txnId);
                                    return false;
                                }
                                break;
                        }
                        break;
                    case CUST:
                        switch (queryType) {
                            case ADD:
                                //location is the customerId. it gets set in newcustomer
                                if (!newCustomerId(txnId, location)) {
                                    Trace.warn("FAILED TO CREATE NEW CUSTOMER UPON ABORT: " + txnId);
                                    return false;
                                }
                                break;
                            case DEL:
                                //location is the customerId. it gets set in newcustomer
                                boolean isDeleted = deleteCustomer(txnId, location);
                                if (!isDeleted) {
                                    Trace.warn("FAILED TO DELETE CUSTOMER UPON ABORT: " + txnId);
                                    return false;
                                }
                                break;
                            case UNRES:
                                String objLocation = String.valueOf(cmd.get(3));
                                String objKey = (String) cmd.get(4);
                                Integer itemInfo = (Integer) cmd.get(5);
                                if (!unReserveItem(txnId,location,objLocation,objKey,itemInfo)){
                                    Trace.warn("FAILED UNRERSERVE CUSTOMER UPON ABORT: " + txnId);
                                    return false;
                                }
                                break;
                        }
                        break;
                }//end of the bigger switch
            }//endwhile
        }
        catch (NullPointerException e){
            e.printStackTrace();
            Trace.warn("NEED TO START TRANSCATION BEFORE CALLING ABORT");
            return false;
        }


        //abort in specific RMs
        Vector RMList = this.txnManager.activeTxnRM.get(txnId);
        Iterator it = RMList.iterator();
        while(it.hasNext()){
            Integer RMType = (Integer) it.next();
            switch (RMType){
                case FLIGHT:
                    boolean flightAborted = false;
                    try {
                        flightAborted = flightProxy.proxy.abort(txnId);
                    }catch (Exception e0){
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                        try {
                            if (getStatusCode(flightProxyBackup.wsdlLocation) == 200){
                                flightProxy = flightProxyBackup;
                            }
                            System.out.println("connected to its replica");
                            flightAborted = flightProxy.proxy.abort(txnId);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                            try{
                                if (getStatusCode(flightPC.wsdlLocation) == 200){
                                    flightProxy = flightPC;
                                }
                                System.out.println("connected to its replica");
                                flightAborted = flightProxy.proxy.abort(txnId);
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }
                    if (!flightAborted){
                        Trace.warn("ERROR IN ABORT IN THE FLIGHT SERVER: " + txnId);
                        return false;
                    }
                    break;
                case CAR:
                    boolean carAborted = false;
                    try {
                        carAborted = carProxy.proxy.abort(txnId);
                    }catch (Exception e0){
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                        try {
                            if (getStatusCode(carProxyBackup.wsdlLocation) == 200){
                                carProxy = carProxyBackup;
                            }
                            System.out.println("connected to its replica");
                            carAborted = carProxy.proxy.abort(txnId);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                            try{
                                if (getStatusCode(carPC.wsdlLocation) == 200){
                                    carProxy = carPC;
                                }
                                System.out.println("connected to its replica");
                                carAborted = carProxy.proxy.abort(txnId);
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }
                    if (!carAborted){
                        Trace.info("ERROR IN ABORT IN THE CAR SERVER: "+txnId);
                        return false;
                    }
                    break;
                case ROOM:
                    boolean roomAborted = false;
                    try {
                        roomAborted = roomProxy.proxy.abort(txnId);
                    }catch (Exception e){
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                        try {
                            if (getStatusCode(roomProxyBackup.wsdlLocation) == 200){
                                roomProxy = roomProxyBackup;
                            }
                            System.out.println("connected to its replica");
                            roomAborted = roomProxy.proxy.abort(txnId);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                            try{
                                if (getStatusCode(roomPC.wsdlLocation) == 200){
                                    roomProxy = roomPC;
                                }
                                System.out.println("connected to its replica");
                                roomAborted = roomProxy.proxy.abort(txnId);
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }
                    if (!roomAborted){
                        Trace.info("ERROR IN ABORT IN THE ROOM SERVER: "+txnId);
                        return false;
                    }
                    break;
                case CUST:
                    if(!MWLock.UnlockAll(txnId)){
                        Trace.info("ERROR IN ABORT IN THE MIDDLEWARE FOR THE CUSTOMER RM: "+txnId);
                        return false;
                    }
                    break;
            }
        }
        //clean up entries in txnmanager upon success
        try {
            this.txnManager.activeTxnRM.remove(txnId);
            this.txnManager.txnCmdList.remove(txnId);
        }catch (NullPointerException e) {
            Trace.warn("ERROR WHEN REMOVING TXNMANAGER ENTRIES AT ABORT: "+txnId);
            e.printStackTrace();
        }

        //send command to replica
        String command = "abort,"+txnId;
        sendCommand(command);

        return true;
    }

    @Override
    public boolean shutdown(){
        //check if both txn tables are empty, if they are, we can shutdown the RMs
        if (this.txnManager.txnCmdList.isEmpty() && this.txnManager.activeTxnRM.isEmpty()){
            if (f_flag == 1) {
                boolean exit;
                try {
                    exit = this.flightProxy.proxy.shutdown();
                    if(!exit){
                        Trace.warn("ERROR WHEN SHUTTING DOWN FLIGHT RM");
                        return false;
                    }
                }catch (Exception e){
                    Trace.info("SHUTTING DOWN GRACEFULLY");
                }
            }
            else{
                Trace.warn("FLIGHT RM IS NOT RUNNING");
            }
            if (c_flag == 1) {
                boolean exit;
                try {
                    exit = this.carProxy.proxy.shutdown();
                    if (!exit) {
                        Trace.warn("ERROR WHEN SHUTTING DOWN CAR RM");
                        return false;
                    }
                }catch (Exception e){
                    Trace.info("SHUTTING DOWN GRACEFULLY");
                }
            }
            else{
                Trace.warn("CAR RM IS NOT RUNNING");
            }
            if (r_flag == 1){
                boolean exit;
                try {
                    exit = this.roomProxy.proxy.shutdown();
                    if (!exit) {
                        Trace.warn("ERROR WHEN SHUTTING DOWN ROOM RM");
                        return false;
                    }
                }catch (Exception e){
                    Trace.info("SHUTTING DOWN GRACEFULLY");
                }
            }
            else {
                Trace.warn("ROOM RM IS NOT RUNNING");
            }

            //all RMs have been shut down. We can exit the middleware
            System.exit(1);
            return true;
        }
        //if not we cannot shutdown now, there are still transactions running
        else{
            Trace.warn("CANNOT SHUTDOWN AT THE MOMENT, TRANSACTIONS ARE STILL ACTIVE");
            return false;
        }
    }

    @Override
    public int signal() {

        return 2;
    }

}