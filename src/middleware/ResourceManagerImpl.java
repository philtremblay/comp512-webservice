package middleware;

import client.DeadlockException_Exception;
import server.LockManager.*;


import client.WSClient;
import server.Trace;

import javax.jws.WebService;
import java.net.MalformedURLException;
import java.util.*;


//this class is implementating webservice interfaces (resourceManger)
@WebService(endpointInterface = "server.ws.ResourceManager")
public class ResourceManagerImpl implements server.ws.ResourceManager {

    WSClient flightProxy;
    WSClient carProxy;
    WSClient roomProxy;

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
    short c_flag = 0;
    short r_flag = 0;


    //flight server properties
    String f_name = "flight";
    String f_host = "localhost";
    int f_port = 8080;


    //car server properties
    String c_name = "car";
    String c_host = "localhost";
    int c_port = 8082;

    //room server properties
    String r_name = "room";
    String r_host = "localhost";
    int r_port = 8084;

    String configFile = "middleudp.xml";

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
        this.transactionBit = new BitSet();

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
                flightProxy = new WSClient(f_name, f_host, f_port);
                System.out.println("middleware is connected to the flight server: " +f_host + " " +f_port);

            } catch (MalformedURLException e) {
                System.out.println("Connecting to the flight server");
            }
        }

        if (c_flag == 1) {
            try {
                carProxy = new WSClient(c_name, c_host, c_port);
            } catch (MalformedURLException e) {
                System.out.println("Connecting to the car server " + c_host + " "+ c_port);
            }
        }

        if (r_flag == 1) {
            try {
                roomProxy = new WSClient(r_name, r_host, r_port);
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

        if (broadcast.PCBit.get(0)) {
            broadcast.addCommand(command);
            broadcast.bit.set(0);
        }

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

    // Reserve an item.
    protected boolean reserveItem(int id, int customerId, String location, String key, int itemInfo) {
        //get item info
        int count = -1;
        int price = -1;
        switch(itemInfo){
            case FLIGHT:
                count = flightProxy.proxy.queryFlight(id, Integer.parseInt(location));
                price = flightProxy.proxy.queryFlightPrice(id,Integer.parseInt(location));
                break;
            case CAR:
                count = carProxy.proxy.queryCars(id,location);
                price = carProxy.proxy.queryCarsPrice(id, location);
                break;
            case ROOM:
                count = roomProxy.proxy.queryRooms(id,location);
                price = roomProxy.proxy.queryRoomsPrice(id, location);
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
            //its only the Primary copy that sends to the RM, not the replica
            if (broadcast.PCBit.get(0)) {
                switch (itemInfo) {
                    case FLIGHT:
                        update = flightProxy.proxy.updateItemInfo(id, key, RES);
                        break;
                    case CAR:
                        update = carProxy.proxy.updateItemInfo(id, key, RES);
                        break;
                    case ROOM:
                        update = roomProxy.proxy.updateItemInfo(id, key, RES);
                        break;
                }
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


            // Decrease the number of available items in the storage.
            boolean update = true;
            switch(itemInfo){
                case FLIGHT: update = flightProxy.proxy.updateItemInfo(id,key,UNRES);
                    break;
                case CAR: update = carProxy.proxy.updateItemInfo(id,key,UNRES);
                    break;
                case ROOM: update = roomProxy.proxy.updateItemInfo(id,key,UNRES);
                    break;
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
            if(broadcast.PCBit.get(0)) {
                //ready to broadcast the command
                broadcast.addCommand(command);
                broadcast.bit.set(0);
            }
            return flightAdded;
        }
        else {

            System.out.println("FAIL to sent to flight server");
            return flightAdded;
        }

    }

    @Override
    public boolean deleteFlight(int id, int flightNumber) {

        boolean flightDeleted;
        //check price and number of seats before deleting
        int seats = flightProxy.proxy.queryFlight(id,flightNumber);
        int price = flightProxy.proxy.queryFlightPrice(id,flightNumber);

        flightDeleted = flightProxy.proxy.deleteFlight(id, flightNumber);
        ttl[id-1].pushItem(id);
        if (flightDeleted) {
            Vector cmd = cmdToVect(FLIGHT,ADD,flightNumber);
            cmd.add(seats);
            cmd.add(price);
            this.txnManager.setNewUpdateItem(id,cmd);

            this.txnManager.enlist(id,FLIGHT);

            System.out.println("DELETED flight " + flightNumber);
        }
        else {
            System.out.println("FAIL to delete flight");
        }

        return flightDeleted;
    }

    @Override
    public int queryFlight(int id, int flightNumber) {

        int flightNum = flightProxy.proxy.queryFlight(id, flightNumber);
        ttl[id-1].pushItem(id);
        if (flightNum > 0) {
            System.out.println("QUERY the flight with ID:" + id);
        }
        else {
            Trace.warn("Cannot query the flight# " + id);
        }
        this.txnManager.enlist(id,FLIGHT);
        return flightNum;
    }

    @Override
    public int queryFlightPrice(int id, int flightNumber) {

        int flightPrice = flightProxy.proxy.queryFlightPrice(id, flightNumber);
        ttl[id-1].pushItem(id);
        System.out.println("QUERY the flight price with ID: " + id);

        this.txnManager.enlist(id,FLIGHT);

        return flightPrice;
    }

    @Override
    public boolean addCars(int id, String location, int numCars, int carPrice) {

        boolean carsAdded;
        carsAdded = carProxy.proxy.addCars(id,location, numCars, carPrice);
        ttl[id-1].pushItem(id);
        if (carsAdded) {
            System.out.println("SENT the addCar command to the car server:" + c_host + ":" + c_port);
            //Set the cmd to delete because it needs to be deleted in the rollback
            Vector cmd = cmdToVect(CAR,DEL,Integer.parseInt(location));
            this.txnManager.setNewUpdateItem(id,cmd);

            //set active RM list
            this.txnManager.enlist(id,CAR);
        }
        else {
            System.out.println("FAIL to add cars");
        }
        return carsAdded;
    }

    @Override
    public boolean deleteCars(int id, String location) {

        boolean carsDeleted;
        int num = carProxy.proxy.queryCars(id,location);
        int price = carProxy.proxy.queryCarsPrice(id,location);

        carsDeleted = carProxy.proxy.deleteCars(id, location);
        ttl[id-1].pushItem(id);
        if(carsDeleted) {
            Vector cmd = cmdToVect(CAR,ADD,Integer.parseInt(location));
            cmd.add(num);
            cmd.add(price);
            this.txnManager.setNewUpdateItem(id,cmd);

            this.txnManager.enlist(id,CAR);

            System.out.println("DELETE cars " + id);
        }
        else {
            System.out.println("FAIL to delete cars ");
        }

        return carsDeleted;
    }

    @Override
    public int queryCars(int id, String location) {

        int carNum = carProxy.proxy.queryCars(id, location);
        ttl[id-1].pushItem(id);
        System.out.println("QUERY the car with ID: " + id);

        this.txnManager.enlist(id,CAR);

        return carNum;
    }

    @Override
    public int queryCarsPrice(int id, String location) {

        int carPrice = carProxy.proxy.queryCarsPrice(id, location);
        ttl[id-1].pushItem(id);
        System.out.println("QUERY the car price with ID: " + id);

        this.txnManager.enlist(id,CAR);

        return carPrice;
    }

    @Override
    public boolean addRooms(int id, String location, int numRooms, int roomPrice) {

        boolean roomsAdded = roomProxy.proxy.addRooms(id, location, numRooms, roomPrice);
        ttl[id-1].pushItem(id);
        if (roomsAdded) {
            System.out.println("EXECUTE the addRoom command to the room server: "+r_host +":"+r_port);
            Vector cmd = cmdToVect(ROOM,DEL,Integer.parseInt(location));
            this.txnManager.setNewUpdateItem(id,cmd);

            this.txnManager.enlist(id,ROOM);
        }
        else {
            System.out.println("FAIL to add rooms to the room server: "+r_host + ":" +r_port);
        }

        return roomsAdded;
    }

    @Override
    public boolean deleteRooms(int id, String location) {
        int num = roomProxy.proxy.queryRooms(id,location);
        int price = roomProxy.proxy.queryRoomsPrice(id,location);

        boolean roomDeleted = roomProxy.proxy.deleteRooms(id, location);
        ttl[id-1].pushItem(id);
        if (roomDeleted) {

            Vector cmd = cmdToVect(ROOM,ADD,Integer.parseInt(location));
            cmd.add(num);
            cmd.add(price);
            this.txnManager.setNewUpdateItem(id,cmd);

            this.txnManager.enlist(id,ROOM);
            this.txnManager.enlist(id,ROOM);


            System.out.println("EXECUTE the deleteRoom command to the rooom server: "+r_host + ":" +r_port);
        }
        else {
            System.out.println("FAIL to delete rooms");
        }
        return roomDeleted;
    }

    @Override
    public int queryRooms(int id, String location) {

        int roomquery = roomProxy.proxy.queryRooms(id, location);
        ttl[id-1].pushItem(id);

        System.out.println("QUERY the room with ID: " + id);

        this.txnManager.enlist(id,ROOM);

        return roomquery;
    }

    @Override
    public int queryRoomsPrice(int id, String location) {
        int roomPrice = roomProxy.proxy.queryRoomsPrice(id,location);
        ttl[id-1].pushItem(id);
        System.out.println("QUERY the room PRICE with ID:" + id);

        this.txnManager.enlist(id,ROOM);

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

            if (broadcast.PCBit.get(0)) {
                System.out.println("READY TO BROADCAST!!!!!");
                broadcast.addCommand(command);
                broadcast.bit.set(0);
            }
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
                //car
                if(reservedItem.getType() == 1){
                    if(flightProxy.proxy.updateDeleteCustomer(itemId,reservedItem.getKey(),count)){

                        if (!transactionBit.get(id)) {
                            Vector cmd = cmdToVect(FLIGHT, RES, Integer.parseInt(location));
                            cmd.add(count);
                            this.txnManager.setNewUpdateItem(id, cmd);
                            this.txnManager.enlist(id, FLIGHT);
                        }

                        return true;
                    }
                    else{
                        //error
                        return false;
                    }
                }
                //room
                else if (reservedItem.getType() == 2){
                    if(carProxy.proxy.updateDeleteCustomer(itemId,reservedItem.getKey(),count)){

                        if (!transactionBit.get(id)) {
                            Vector cmd = cmdToVect(CAR, RES, Integer.parseInt(location));
                            cmd.add(count);
                            this.txnManager.setNewUpdateItem(id, cmd);
                            this.txnManager.enlist(id, CAR);
                        }
                        return true;
                    }
                    else{
                        //error
                        return false;
                    }
                }
                else if (reservedItem.getType() == 3){
                    if(roomProxy.proxy.updateDeleteCustomer(itemId,reservedItem.getKey(),count)){

                        if (!transactionBit.get(id)) {
                            Vector cmd = cmdToVect(ROOM, RES, Integer.parseInt(location));
                            cmd.add(count);
                            this.txnManager.setNewUpdateItem(id, cmd);
                            this.txnManager.enlist(id, ROOM);
                        }
                        return true;
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

            String command = "querycustomer,"+id+","+customerId;
            if (broadcast.PCBit.get(0)) {
                System.out.println("READY TO BROADCAST!!!!!");
                broadcast.addCommand(command);
                broadcast.bit.set(0);
            }
            

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

        String key = flightProxy.proxy.getFlightKey(flightNumber);

        ttl[id-1].pushItem(id);
        boolean isReserved = reserveItem(id,customerId,String.valueOf(flightNumber),key,FLIGHT);
        String command = String.format("reserveflight,%d,%d,%d,%s",id,customerId,flightNumber,key);
        if (isReserved){
            System.out.println("RESERVATION ADDED TO THE CUSTOMER!!!!!");

            this.txnManager.enlist(id,FLIGHT);

            //if this is the primary copy
            if(broadcast.PCBit.get(0)) {
                //ready to broadcast the command
                broadcast.addCommand(command);
                broadcast.bit.set(0);
            }

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
        String key = flightProxy.proxy.getFlightKey(flightNumber);
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
        String key = carProxy.proxy.getCarKey(location);
        ttl[id-1].pushItem(id);
        if (reserveItem(id,customerId,location,key,CAR)){
            this.txnManager.enlist(id,CAR);
            return true;
        }
        else{
            //error
            return false;
        }
    }

    private boolean unReserveCar(int id, int customerId, String location) {
        /** call methods from the car server to execute actions **/
        String key = carProxy.proxy.getCarKey(location);
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
        String key = roomProxy.proxy.getRoomKey(location);
        ttl[id-1].pushItem(id);
        if (reserveItem(id, customerId,location, key, ROOM)){

            this.txnManager.enlist(id,ROOM);
            return true;
        }
        else{
            //error
            return false;
        }
    }
    private boolean unReserveRoom(int id, int customerId, String location) {
        /** call methods from the room server to execute actions **/
        String key = roomProxy.proxy.getRoomKey(location);
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
        if (flights.nextSetBit(0) != -1 || !carPassed || !roomPassed){
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
                    if(!flightProxy.proxy.commit(txnId)){
                        Trace.warn("ERROR IN FLIGHT RM COMMIT: " + txnId);
                        return false;
                    }
                    break;
                case CAR:
                    if(!carProxy.proxy.commit(txnId)){
                        Trace.warn("ERROR IN CAR RM COMMIT: " + txnId);
                        return false;
                    }
                    break;
                case ROOM:
                    if(!roomProxy.proxy.commit(txnId)){
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
        if(broadcast.PCBit.get(0)) {
            //ready to broadcast the command
            broadcast.addCommand(command);
            broadcast.bit.set(0);
        }

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
                                Trace.info("ADDED NEW FLIGHT WHEN ABORTING: "+txnId);
                                break;
                            case DEL:
                                if (!flightProxy.proxy.deleteFlight(txnId, location)) {
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
                                try {
                                    if (!carProxy.proxy.addCars(txnId, String.valueOf(location), numCars, price)) {
                                        Trace.warn("FAILED TO ADDCAR UPON ABORT: " + txnId);
                                        return false;
                                    }
                                } catch (Exception e) { //why isn't there an exception here?
                                    Trace.warn("DEADLOCK EXCEPTION UPON ADDFLIGHT IN ABORT: " + txnId);
                                    e.printStackTrace();
                                    return false;
                                }
                                break;
                            case DEL:
                                if (!carProxy.proxy.deleteCars(txnId, String.valueOf(location))) {
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
                                if (roomProxy.proxy.addRooms(txnId, String.valueOf(location), numRooms, price)) {
                                    Trace.warn("FAILED TO ADDROOM UPON ABORT: " + txnId);
                                    return false;
                                }
                                break;
                            case DEL:
                                if (!roomProxy.proxy.deleteRooms(txnId, String.valueOf(location))) {
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
                    if (!flightProxy.proxy.abort(txnId)){
                        Trace.warn("ERROR IN ABORT IN THE FLIGHT SERVER: " + txnId);
                        return false;
                    }
                    break;
                case CAR:
                    if (!carProxy.proxy.abort(txnId)){
                        Trace.info("ERROR IN ABORT IN THE CAR SERVER: "+txnId);
                        return false;
                    }
                    break;
                case ROOM:
                    if (!roomProxy.proxy.abort(txnId)){
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

}