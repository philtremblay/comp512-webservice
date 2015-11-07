package middleware;

import client.DeadlockException_Exception;
import server.LockManager.*;

import client.DeadlockException;

import client.DeadlockException_Exception;

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

    short f_flag = 1;
    short c_flag = 1;
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

    //code for Client imported from server
    protected RMHashtable m_itemHT = new RMHashtable();

    //Transaction Manager
    TxnManager txnManager = new TxnManager();

    // Basic operations on RMItem //

    // Read a data item.
    private RMItem readData(int id, String key) {
        synchronized(m_itemHT) {
            return (RMItem) m_itemHT.get(key);
        }
    }

    // Write a data item.
    private void writeData(int id, String key, RMItem value) {
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

    //constructor that creates proxies to each server
    public ResourceManagerImpl() {
        this.MWLock = new LockManager();

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
                price = carProxy.proxy.queryCarsPrice(id,location);
                break;
            case ROOM:
                count = roomProxy.proxy.queryRooms(id,location);
                price = roomProxy.proxy.queryRoomsPrice(id,location);
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
            switch(itemInfo){
                case FLIGHT: update = flightProxy.proxy.updateItemInfo(id,key,RES);
                    break;
                case CAR: update = carProxy.proxy.updateItemInfo(id,key,RES);
                    break;
                case ROOM: update = roomProxy.proxy.updateItemInfo(id,key,RES);
                    break;
            }
            if (!update){
                Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                        + key + ", " + location + ") failed: update item info.");
                return false;
            }
            else {
                // Do reservation
                cust.reserve(key, location, price,itemInfo,id); //change location maybe
                writeData(id, cust.getKey(), cust);
            }

            Trace.info("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") OK.");
            return true;
        }
    }

    // Unreserve an item.
    protected boolean unreserveItem(int id, int customerId, String location, String key, int itemInfo) {
        //get item info
        int count = -1;
        //int price = -1;
        switch(itemInfo){
            case FLIGHT:
                count = flightProxy.proxy.queryFlight(id, Integer.parseInt(location));
                //price = flightProxy.proxy.queryFlightPrice(id,Integer.parseInt(location));
                break;
            case CAR:
                count = carProxy.proxy.queryCars(id,location);
                //price = carProxy.proxy.queryCarsPrice(id,location);
                break;
            case ROOM:
                count = roomProxy.proxy.queryRooms(id,location);
                //price = roomProxy.proxy.queryRoomsPrice(id,location);
                break;
        }

        Trace.info("RM::unreserveItem(" + id + ", " + customerId + ", "
                + key + ", " + location + ") called.");
        // Read customer object if it exists (and read lock it).
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::unreserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") failed: customer doesn't exist.");
            return false;
        }

        // Check if the item is available.
        if (count == -1) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") failed: item doesn't exist.");
            return false;
        }
        else {


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
                cust.unreserve(key, location,itemInfo,id); //change location maybe
                writeData(id, cust.getKey(), cust);
            }

            Trace.warn("RM::unreserveItem(" + id + ", " + customerId + ", "
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


        if (flightAdded) {
            System.out.println("SENT the addFlight command to the flight server:" + f_host + ":" + f_port);

            //Set the cmd to delete because it needs to be deleted in the rollback
            Vector cmd = cmdToVect(FLIGHT, DEL, flightNumber);
            this.txnManager.setNewUpdateItem(id, cmd);

            //set active RM list
            this.txnManager.enlist(id, FLIGHT);

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

        flightDeleted = flightProxy.proxy.deleteFlight(id, flightNumber);

        if (flightDeleted) {
            int seats = flightProxy.proxy.queryFlight(id,flightNumber);
            int price = flightProxy.proxy.queryFlightPrice(id,flightNumber);
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

        System.out.println("QUERY the flight price with ID: " + id);

        this.txnManager.enlist(id,FLIGHT);

        return flightPrice;
    }

    @Override
    public boolean addCars(int id, String location, int numCars, int carPrice) {

        boolean carsAdded;
        carsAdded = carProxy.proxy.addCars(id,location, numCars, carPrice);
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
        carsDeleted = carProxy.proxy.deleteCars(id, location);

        if(carsDeleted) {
            int num = carProxy.proxy.queryCars(id,location);
            int price = carProxy.proxy.queryCarsPrice(id,location);
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

        System.out.println("QUERY the car with ID: " + id);

        this.txnManager.enlist(id,CAR);

        return carNum;
    }

    @Override
    public int queryCarsPrice(int id, String location) {

        int carPrice = carProxy.proxy.queryCarsPrice(id, location);

        System.out.println("QUERY the car price with ID: " + id);

        this.txnManager.enlist(id,CAR);

        return carPrice;
    }

    @Override
    public boolean addRooms(int id, String location, int numRooms, int roomPrice) {

        boolean roomsAdded = roomProxy.proxy.addRooms(id, location, numRooms, roomPrice);

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

        boolean roomDeleted = roomProxy.proxy.deleteRooms(id, location);

        if (roomDeleted) {
            int num = roomProxy.proxy.queryRooms(id,location);
            int price = roomProxy.proxy.queryRoomsPrice(id,location);
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
        System.out.println("QUERY the room with ID: "+ id);

        this.txnManager.enlist(id,ROOM);

        return roomquery;
    }

    @Override
    public int queryRoomsPrice(int id, String location) {
        int roomPrice = roomProxy.proxy.queryRoomsPrice(id,location);
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
            e.printStackTrace();
            return -1;
        }

        Customer cust = new Customer(customerId);
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
        Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") called.");
        String strData = "customer,"+customerId;

        try {
            MWLock.Lock(id,strData,WRITE);
        } catch (server.LockManager.DeadlockException e) {
            e.printStackTrace();
            return false;
        }
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            cust = new Customer(customerId);
            writeData(id, cust.getKey(), cust);
            Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") OK.");

            Vector cmd = cmdToVect(CUST,DEL,customerId);
            this.txnManager.setNewUpdateItem(id,cmd);
            this.txnManager.enlist(id,CUST);

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
                        Vector cmd = cmdToVect(FLIGHT,RES,Integer.parseInt(location));
                        cmd.add(count);

                        this.txnManager.setNewUpdateItem(id,cmd);
                        this.txnManager.enlist(id,FLIGHT);

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
                        Vector cmd = cmdToVect(CAR,RES,Integer.parseInt(location));
                        cmd.add(count);

                        this.txnManager.setNewUpdateItem(id,cmd);
                        this.txnManager.enlist(id,CAR);

                        return true;
                    }
                    else{
                        //error
                        return false;
                    }
                }
                else if (reservedItem.getType() == 3){
                    if(roomProxy.proxy.updateDeleteCustomer(itemId,reservedItem.getKey(),count)){
                        Vector cmd = cmdToVect(CAR,RES,Integer.parseInt(location));
                        cmd.add(count);

                        this.txnManager.setNewUpdateItem(id,cmd);
                        this.txnManager.enlist(id,ROOM);

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
            Vector cmd = cmdToVect(CUST,ADD,customerId);
            this.txnManager.setNewUpdateItem(id,cmd);
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
            e.printStackTrace();
            return "WARN: RM::queryCustomerInfo(" + id + ", "
                    + customerId + ") failed: DeadlockException";
        }

        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
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
        if (reserveItem(id,customerId,String.valueOf(flightNumber),key,FLIGHT )){
            System.out.println("RESERVATION ADDED TO THE CUSTOMER!!!!!");
            Vector cmd = cmdToVect(FLIGHT,UNRES,flightNumber);
            cmd.add(customerId);
            cmd.add(key);
            this.txnManager.setNewUpdateItem(id,cmd);

            this.txnManager.enlist(id,FLIGHT);
            txnManager.enlist(id,CUST);
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
        if (reserveItem(id,customerId,location,key,CAR)){
            Vector cmd = cmdToVect(CAR,UNRES,Integer.parseInt(location));
            cmd.add(customerId);
            cmd.add(key);
            this.txnManager.setNewUpdateItem(id,cmd);

            this.txnManager.enlist(id,CAR);
            this.txnManager.enlist(id,CUST);
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
        if (reserveItem(id, customerId,location, key, ROOM)){
            Vector cmd = cmdToVect(CAR,UNRES,Integer.parseInt(location));
            cmd.add(customerId);
            cmd.add(key);
            this.txnManager.setNewUpdateItem(id,cmd);

            this.txnManager.enlist(id,ROOM);
            this.txnManager.enlist(id,CUST);
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

        boolean carflag = false;
        boolean roomflag = false;

        while(it.hasNext()){
            if(!(reserveFlight(id,customerId,Integer.parseInt((String)it.next())))){
                //error
                Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " + location + ") failed: no more seats available.");            }
                return false;
        }
        //there is a car and room
        if (car  &&  room) {
            carflag = reserveCar(id, customerId, location);
            roomflag = reserveRoom(id, customerId, location);
            //failed to reserve car or room
            if (! (carflag && roomflag)) {
                return false;
            }

        }
        //there is a room
        else if (room){

            roomflag = reserveRoom(id, customerId, location);
            //fail to reserve room
            if (!roomflag) {
                return false;
            }
        }
        //if there is a car
        else if(car){
            carflag = reserveCar(id, customerId, location);
            if (!carflag) {
                return false;
            }
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
    public int start(){
        int txnId = txnManager.newTxn();
        Trace.info("Starting a new transaction with ID : "+txnId);

        return txnId;
    }

    @Override
    public boolean commit(int txnId) {
        //iterate through active RM list and release locks
        Vector RMlist = this.txnManager.activeTxnRM.get(txnId);
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
                        Trace.warn("ERROR IN FLIGHT RM COMMIT");
                        return false;
                    }
                    break;
                case CAR:
                    if(!carProxy.proxy.commit(txnId)){
                        Trace.warn("ERROR IN CAR RM COMMIT");
                        return false;
                    }
                    break;
                case ROOM:
                    if(!roomProxy.proxy.commit(txnId)){
                        Trace.warn("ERROR IN ROOM RM COMMIT");
                        return false;
                    }
                    break;
                case CUST:
                    //add lock manager to MIDDLEWARE
                    if (txnId > 0) {
                        Trace.info("RM::COMMIT TRANSACTION ID: " + txnId);
                        if (!this.MWLock.UnlockAll(txnId)) {
                            Trace.info("FAILED TO UNLOCK ALL CUSTOMER LOCKS");
                            return false;
                        }
                    }
                    else {
                        Trace.warn("INVALID TXNID: CANNOT COMMIT CUSTOMER");
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
            Trace.warn("ERROR WHEN REMOVING TXNMANAGER ENTRIES");
            e.printStackTrace();
        }
        /*
        if (txnId > 0) {
            if (flightProxy.proxy.commit(txnId) && carProxy.proxy.commit(txnId) && roomProxy.proxy.commit(txnId)) {
                Trace.info("RM::SUCCESSUFULLY COMMIT TRANSACTION ID: " + txnId);
                return true;
            }
            else
                return false;
        }else {
            return false;
        }
        */
        return true;
    }

    @Override
    public boolean abort(int txnId){
        //get the commands from the stack of commands and execute them
        Stack cmdList = this.txnManager.txnCmdList.get(txnId);
        while (!cmdList.isEmpty()){
            Vector cmd = (Vector) cmdList.pop();
            Integer RMType = (Integer) cmd.get(0);
            Integer queryType = (Integer) cmd.get(1);
            Integer location = (Integer) cmd.get(2);

            switch (RMType){
                case FLIGHT:
                    switch (queryType){
                        case ADD:
                            Integer seats = (Integer) cmd.get(3);
                            Integer price = (Integer) cmd.get(4);
                            try {
                                if (!flightProxy.proxy.addFlight(txnId,location,seats,price)){
                                    Trace.info("FAILED TO ADDFLIGHT UPON ABORT");
                                    return false;
                                }
                            } catch (DeadlockException_Exception e) {
                                Trace.info("DEADLOCK EXCEPTION UPON ADDFLIGHT IN ABORT");
                                e.printStackTrace();
                                return false;
                            }
                            break;
                        case DEL:
                            if(!flightProxy.proxy.deleteFlight(txnId,location)){
                                Trace.info("FAILED TO DELETEFLIGHT UPON ABORT");
                                return false;
                            }
                            break;
                        case UNRES:
                            //implement unreserve on server to continue
                            break;
                    }
                    break;
                case CAR:
                    switch (queryType){
                        case ADD:
                            Integer numCars = (Integer) cmd.get(3);
                            Integer price = (Integer) cmd.get(4);
                            try {
                                if (!carProxy.proxy.addCars(txnId, String.valueOf(location), numCars, price)) {
                                    Trace.info("FAILED TO ADDCAR UPON ABORT");
                                    return false;
                                }
                            }catch (Exception e){ //why isn't there an exception here?
                                Trace.info("DEADLOCK EXCEPTION UPON ADDFLIGHT IN ABORT");
                                e.printStackTrace();
                                return false;
                            }
                            break;
                        case DEL:
                            if(!carProxy.proxy.deleteCars(txnId,String.valueOf(location))){
                                Trace.info("FAILED TO DELETECAR UPON ABORT");
                                return false;
                            }
                            break;
                        case UNRES:
                            //implement unreserve on server to continue
                            break;
                    }
                    break;
                case ROOM:
                    switch (queryType){
                        case ADD:
                            Integer numRooms = (Integer) cmd.get(3);
                            Integer price = (Integer) cmd.get(4);
                            if (roomProxy.proxy.addRooms(txnId,String.valueOf(location),numRooms,price)){
                                Trace.info("FAILED TO ADDROOM UPON ABORT");
                                return false;
                            }
                            break;
                        case DEL:
                            if(!roomProxy.proxy.deleteRooms(txnId,String.valueOf(location))){
                                Trace.info("FAILED TO DELETEROOM UPON ABORT");
                                return false;
                            }
                            break;
                        case UNRES:
                            //implement unreserve on server to continue
                            break;
                    }
                    break;
                case CUST:
                    switch (queryType){
                        case ADD:
                            //location is the customerId. it gets set in newcustomer
                            if(!newCustomerId(txnId,location));
                            break;
                        case DEL:
                            //location is the customerId. it gets set in newcustomer
                            if(!deleteCustomer(txnId,location)){
                                Trace.info("FAILED TO DELETE CUSTOMER UPON ABORT");
                                return false;
                            }
                            break;
                        case RES:
                            break;
                        case UNRES:
                            break;
                    }
                    break;
            }
        }//endwhile
        //abort in specific RMs
        Vector RMList = this.txnManager.activeTxnRM.get(txnId);
        Iterator it = RMList.iterator();
        while(it.hasNext()){
            Integer RMType = (Integer) it.next();
            switch (RMType){
                case FLIGHT:
                    if (!flightProxy.proxy.abort(txnId)){
                        Trace.info("ERROR IN ABORT IN THE FLIGHT SERVER");
                        return false;
                    }
                    break;
                case CAR:
                    if (!carProxy.proxy.abort(txnId)){
                        Trace.info("ERROR IN ABORT IN THE CAR SERVER");
                        return false;
                    }
                    break;
                case ROOM:
                    if (!roomProxy.proxy.abort(txnId)){
                        Trace.info("ERROR IN ABORT IN THE ROOM SERVER");
                        return false;
                    }
                    break;
                case CUST:
                    if(!MWLock.UnlockAll(txnId)){
                        Trace.info("ERROR IN ABORT IN THE MIDDLEWARE FOR THE CUSTOMER RM");
                        return false;
                    }
                    break;
            }
        }

        return true;
    }

    @Override
    public boolean shutdown(){
        return true;
    }

}
