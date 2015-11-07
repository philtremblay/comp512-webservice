package middleware;


import client.Client;
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

    public static final int FLIGHT = 1;
    public static final int CAR = 2;
    public static final int ROOM = 3;
    public static final int CUST = 4;
    public static final int DEL = 5;
    public static final int ADD = 6;
    public static final int RES = 7;
    public static final int UNRES = 8;

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
            // Do reservation
            cust.reserve(key, location, price,itemInfo,id); //change location maybe
            writeData(id, cust.getKey(), cust);

            // Decrease the number of available items in the storage.
            boolean update = true;
            switch(itemInfo){
                case 1: update = flightProxy.proxy.updateItemInfo(id,key,RES);
                    break;
                case 2: update = carProxy.proxy.updateItemInfo(id,key,RES);
                    break;
                case 3: update = roomProxy.proxy.updateItemInfo(id,key,RES);
                    break;
            }
            if (!update){
                Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                        + key + ", " + location + ") failed: update item info.");
                return false;
            }

            Trace.info("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") OK.");
            return true;
        }
    }

    // Reserve an item.
    protected boolean unreserveItem(int id, int customerId, String location, String key, int itemInfo) {
        //get item info
        int count = -1;
        //int price = -1;
        switch(itemInfo){
            case 1:
                count = flightProxy.proxy.queryFlight(id, Integer.parseInt(location));
                //price = flightProxy.proxy.queryFlightPrice(id,Integer.parseInt(location));
                break;
            case 2:
                count = carProxy.proxy.queryCars(id,location);
                //price = carProxy.proxy.queryCarsPrice(id,location);
                break;
            case 3:
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
            // Do unreservation
            cust.unreserve(key, location,itemInfo,id); //change location maybe
            writeData(id, cust.getKey(), cust);

            // Decrease the number of available items in the storage.
            boolean update = true;
            switch(itemInfo){
                case 1: update = flightProxy.proxy.updateItemInfo(id,key,UNRES);
                    break;
                case 2: update = carProxy.proxy.updateItemInfo(id,key,UNRES);
                    break;
                case 3: update = roomProxy.proxy.updateItemInfo(id,key,UNRES);
                    break;
            }
            if (!update){
                Trace.warn("RM::unreserveItem(" + id + ", " + customerId + ", "
                        + key + ", " + location + ") failed: update item info.");
                return false;
            }

            Trace.warn("RM::unreserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") OK.");
            return true;
        }
    }





    protected Vector cmdToVect(int queryType, int addOrDel, int itemNumOrLocation){
        Vector cmd = new Vector();
        cmd.add(queryType);
        cmd.add(addOrDel);
        cmd.add(itemNumOrLocation);

        return cmd;
    }

    @Override
    public boolean addFlight(int id, int flightNumber, int numSeats, int flightPrice) {

        boolean flightAdded = false;
        try {
            flightAdded = flightProxy.proxy.addFlight(id, flightNumber, numSeats, flightPrice);
        }
        catch (DeadlockException_Exception e) {
            e.printStackTrace();
        }
        if (flightAdded) {
            System.out.println("SENT the addFlight command to the flight server:" + f_host + ":" + f_port);

            //Set the cmd to delete because it needs to be deleted in the rollback
            Vector cmd = cmdToVect(FLIGHT,DEL,flightNumber);
            this.txnManager.setNewUpdateItem(id,cmd);

            //set active RM list
            this.txnManager.enlist(id, FLIGHT);
        }
        else {
            System.out.println("FAIL to sent to flight server");
        }


        return flightAdded;
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

        return flightNum;
    }

    @Override
    public int queryFlightPrice(int id, int flightNumber) {

        int flightPrice = flightProxy.proxy.queryFlightPrice(id, flightNumber);

        System.out.println("QUERY the flight price with ID: " + id);

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

        return carNum;
    }

    @Override
    public int queryCarsPrice(int id, String location) {

        int carPrice = carProxy.proxy.queryCarsPrice(id, location);

        System.out.println("QUERY the car price with ID: " + id);


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

        return roomquery;
    }

    @Override
    public int queryRoomsPrice(int id, String location) {
        int roomPrice = roomProxy.proxy.queryRoomsPrice(id,location);
        System.out.println("QUERY the room PRICE with ID:" + id);

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
            Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") OK.");
            return true;
        }
    }

    @Override
    public String queryCustomerInfo(int id, int customerId) {
        Trace.info("RM::queryCustomerInfo(" + id + ", " + customerId + ") called.");
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

        while(it.hasNext()){
            if(!(reserveFlight(id,customerId,Integer.parseInt((String)it.next())))){
                //error
                Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " + location + ") failed: no more seats available.");            }
        }
        //there is a car and room
        if (car  &&  room) {
            reserveCar(id, customerId, location);
            reserveRoom(id, customerId, location);
        }
        //there is a room
        else if (room){
            reserveRoom(id, customerId, location);
        }
        //if there is a car
        else if(car){
            reserveCar(id, customerId, location);
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
    public boolean commit(int txnId){
        //iterate through active RM list and release locks
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

    }

    @Override
    public boolean abort(int txnId){
        return true;
    }

    @Override
    public boolean shutdown(){
        return true;
    }

}
