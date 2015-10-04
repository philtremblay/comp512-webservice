package middleware;


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

    // Reserve an item.
    protected boolean reserveItem(int id, int customerId, String location, String key, int itemInfo, int itemId) {
        //get item info
        List<String> item = null;
        int count = -1;
        int price = -1;
        switch(itemInfo){
            case 1:
                count = flightProxy.proxy.queryFlight(id, Integer.parseInt(location));
                price = flightProxy.proxy.queryFlightPrice(id,Integer.parseInt(location));
                break;
            case 2:
                count = carProxy.proxy.queryCars(id,location);
                price = carProxy.proxy.queryCarsPrice(id,location);
                break;
            case 3:
                count = roomProxy.proxy.queryRooms(id,location);
                price = roomProxy.proxy.queryRoomsPrice(id,location);
                break;
        }
        /*info[0] = item.getLocation();
            info[1] = String.valueOf(item.getCount());
            info[2] = item.getKey();
            info[3] = String.valueOf(item.getPrice());
            info[4] = String.valueOf(true);
        */
        if (count == -1){
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

        // Check if the item is available.
        if (count == -1) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") failed: item doesn't exist.");
            return false;
        } else if (count == 0) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") failed: no more items.");
            return false;
        } else {
            // Do reservation
            cust.reserve(key, location, price,itemInfo,itemId); //change location maybe
            writeData(id, cust.getKey(), cust);

            // Decrease the number of available items in the storage.
            boolean update = true;
            switch(itemInfo){
                case 1: update = flightProxy.proxy.updateItemInfo(id,key);
                    break;
                case 2: update = carProxy.proxy.updateItemInfo(id,key);
                    break;
                case 3: update = roomProxy.proxy.updateItemInfo(id,key);
                    break;
            }
            if (!update){
                Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                        + key + ", " + location + ") failed: update item info.");
                return false;
            }

            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") OK.");
            return true;
        }
    }

    //constructor that creates proxies to each server
    public ResourceManagerImpl() {

        try {
            flightProxy = new WSClient(f_name, f_host, f_port);
            System.out.println("middleware is connected to the flight server: " +f_host + " " +f_port);

        } catch (MalformedURLException e) {
            System.out.println("Connecting to the flight server");
        }

        try {
            carProxy = new WSClient(c_name, c_host, c_port);
        } catch (MalformedURLException e) {
            System.out.println("Connecting to the car server " + c_host + " "+ c_port);
        }
/*
        try {
            roomProxy = new WSClient(r_name, r_host, r_port);
        } catch (MalformedURLException e) {
            System.out.println("Connecting to the room server");
        }
*/

    }

    @Override
    public boolean addFlight(int id, int flightNumber, int numSeats, int flightPrice) {

        boolean flightAdded;

        flightAdded = flightProxy.proxy.addFlight(id, flightNumber, numSeats, flightPrice);
        if (flightAdded) {
            System.out.println("SENT the addFlight command to the flight server:" + f_host + ":" + f_port);
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

        System.out.println("QUERY the flight with ID:" + id);

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

                Trace.info("RM::deleteCustomer(" + id + ", " + customerId + "): ");
                //car
                if(reservedItem.getType() == 1){
                    if(!(flightProxy.proxy.updateDeleteCustomer(itemId,reservedItem.getKey(),count))){
                        return false;
                        //error
                    }
                }
                //room
                else if (reservedItem.getType() == 2){
                    if(!(carProxy.proxy.updateDeleteCustomer(itemId,reservedItem.getKey(),count))){
                        return false;
                        //error
                    }
                }
                else if (reservedItem.getType() == 3){
                    if(!(roomProxy.proxy.updateDeleteCustomer(itemId,reservedItem.getKey(),count))){
                        return false;
                        //error
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
        return reserveItem(id,customerId,String.valueOf(flightNumber),key,1,id);
    }

    @Override
    public boolean reserveCar(int id, int customerId, String location) {
        /** call methods from the car server to execute actions **/
        String key = carProxy.proxy.getCarKey(location);
        return reserveItem(id,customerId,location,key,2,id);
    }

    @Override
    public boolean reserveRoom(int id, int customerId, String location) {
        /** call methods from the room server to execute actions **/
        String key = roomProxy.proxy.getRoomKey(location);
        return reserveItem(id, customerId,location, key, 3,id);
    }

    @Override
    public boolean reserveItinerary(int id, int customerId, Vector flightNumbers, String location, boolean car, boolean room) {
        /** call methods from all three servers to execute actions **/
        Iterator it = flightNumbers.iterator();

        while(it.hasNext()){
            if(!(reserveFlight(id,customerId,Integer.parseInt((String)it.next())))){
                //error
                return false;
            }
        }
        //there is a car
        if(!car){
            reserveCar(id,customerId,location);
        }
        //there is a room
        else if (!room){
            reserveRoom(id,customerId,location);
        }
        return true;
    }

    @Override
    public String getFlightKey(int customerId){
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
    public boolean updateItemInfo(int id, String key){
        Trace.warn("Error: Has to be called from middleware");

        return false;
    }
    @Override
    public boolean updateDeleteCustomer(int id, String key, int count){
        Trace.warn("Error: Has to be called from middleware");

        return false;
    }

}
