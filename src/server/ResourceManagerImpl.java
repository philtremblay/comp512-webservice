// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package server;

import server.LockManager.DeadlockException;
import server.LockManager.LockManager;

import java.util.*;
import javax.jws.WebService;


@WebService(endpointInterface = "server.ws.ResourceManager")
public class ResourceManagerImpl implements server.ws.ResourceManager {

    protected RMHashtable m_itemHT = new RMHashtable();

    private static final int READ = 0;
    private static final int WRITE = 1;

    protected LockManager lockServer;
    Broadcast broadcaster;
    String configFile = "flightudp.xml";


    //constructor here: initialize the lock manager
    public ResourceManagerImpl() {

        //initialize the lock manager
        this.lockServer = new LockManager();
        try {
            this.broadcaster = new Broadcast(configFile,this);
            Thread t = new Thread(broadcaster);
            t.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


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

    protected void sendCommand(String command) {
        if (broadcaster.PCBit.get(0)) {
            broadcaster.addCommand(command);
            broadcaster.bit.set(0);
        }
    }


    // Basic operations on ReservableItem //

    // Delete the entire item.
    protected boolean deleteItem(int id, String key) {
        Trace.info("RM::deleteItem(" + id + ", " + key + ") called.");
        ReservableItem curObj = (ReservableItem) readData(id, key);
        // Check if there is such an item in the storage.
        if (curObj == null) {
            Trace.warn("RM::deleteItem(" + id + ", " + key + ") failed: "
                    + " item doesn't exist.");
            return false;
        } else {
            if (curObj.getReserved() == 0) {
                removeData(id, curObj.getKey());
                Trace.info("RM::deleteItem(" + id + ", " + key + ") OK.");
                return true;
            }
            else {
                Trace.info("RM::deleteItem(" + id + ", " + key + ") failed: "
                        + "some customers have reserved it.");
                return false;
            }
        }
    }

    // Query the number of available seats/rooms/cars.
    protected int queryNum(int id, String key) {
        Trace.info("RM::queryNum(" + id + ", " + key + ") called.");
        ReservableItem curObj = (ReservableItem) readData(id, key);
        int value = 0;
        if (curObj != null) {
            value = curObj.getCount();
        }
        Trace.info("RM::queryNum(" + id + ", " + key + ") OK: " + value);
        return value;
    }

    // Query the price of an item.
    protected int queryPrice(int id, String key) {
        Trace.info("RM::queryCarsPrice(" + id + ", " + key + ") called.");
        ReservableItem curObj = (ReservableItem) readData(id, key);
        int value = 0;
        if (curObj != null) {
            value = curObj.getPrice();
        }
        Trace.info("RM::queryPrice(" + id + ", " + key + ") OK: $" + value);
        return value;
    }

    // Reserve an item.
    protected boolean reserveItem(int id, int customerId,
                                  String key, String location) {
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
        ReservableItem item = (ReservableItem) readData(id, key);
        if (item == null) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") failed: item doesn't exist.");
            return false;
        } else if (item.getCount() == 0) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") failed: no more items.");
            return false;
        } else {
            // Do reservation.
            cust.reserve(key, location, item.getPrice());
            writeData(id, cust.getKey(), cust);

            // Decrease the number of available items in the storage.
            item.setCount(item.getCount() - 1);
            item.setReserved(item.getReserved() + 1);

            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", "
                    + key + ", " + location + ") OK.");
            return true;
        }
    }

    // Flight operations //

    // Create a new flight, or add seats to existing flight.
    // Note: if flightPrice <= 0 and the flight already exists, it maintains 
    // its current price.
    @Override
    public boolean addFlight(int id, int flightNumber,
                             int numSeats, int flightPrice) {
        try {
            //request the lock from the lock manager
            String strData = "flight,"+flightNumber;
            lockServer.Lock(id, strData, WRITE);

            Trace.info("RM::addFlight(" + id + ", " + flightNumber
                    + ", $" + flightPrice + ", " + numSeats + ") called.");
            Flight curObj = (Flight) readData(id, Flight.getKey(flightNumber));
            if (curObj == null) {
                // Doesn't exist; add it.
                Flight newObj = new Flight(flightNumber, numSeats, flightPrice);
                writeData(id, newObj.getKey(), newObj);
                Trace.info("RM::addFlight(" + id + ", " + flightNumber
                        + ", $" + flightPrice + ", " + numSeats + ") OK.");
            } else {
                // Add seats to existing flight and update the price.
                curObj.setCount(curObj.getCount() + numSeats);
                if (flightPrice > 0) {
                    curObj.setPrice(flightPrice);
                }
                writeData(id, curObj.getKey(), curObj);
                Trace.info("RM::addFlight(" + id + ", " + flightNumber
                        + ", $" + flightPrice + ", " + numSeats + ") OK: "
                        + "seats = " + curObj.getCount() + ", price = $" + flightPrice);
            }
            String command = String.format("newflight,%d,%d,%d,%d", id, flightNumber, numSeats, flightPrice);
            sendCommand(command);
            return true;
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::addFlight(" + id + ", " + flightNumber
                    + ", $" + flightPrice + ", " + numSeats + ") failed.");
            return false;
        }



    }

    @Override
    public boolean deleteFlight(int id, int flightNumber) {

        String strData = "flight,"+flightNumber;
        try {
            lockServer.Lock(id, strData, WRITE);
            return deleteItem(id, Flight.getKey(flightNumber));
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::deleteItem(" + id + ", flight " + flightNumber+ ") failed: ");
            return false;
        }


    }

    // Returns the number of empty seats on this flight.
    @Override
    public int queryFlight(int id, int flightNumber) {

        String strData = "flight,"+flightNumber;
        try {
            lockServer.Lock(id, strData, READ);

            int num = queryNum(id, Flight.getKey(flightNumber));

            String command = "queryflight,"+id+","+flightNumber;
            sendCommand(command);
            return num;
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::queryNum(" + id + ", flight " + flightNumber+ ") failed: ");
            return -1;
        }



    }

    // Returns price of this flight.
    public int queryFlightPrice(int id, int flightNumber) {

        String strData = "flight," + flightNumber;
        try {
            lockServer.Lock(id, strData, READ);
            return queryPrice(id, Flight.getKey(flightNumber));
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::queryPrice(" +id +", flight " +flightNumber+") failed: DeadlockException");
            return -1;

        }

    }

    /*
    // Returns the number of reservations for this flight. 
    public int queryFlightReservations(int id, int flightNumber) {
        Trace.info("RM::queryFlightReservations(" + id 
                + ", #" + flightNumber + ") called.");
        RMInteger numReservations = (RMInteger) readData(id, 
                Flight.getNumReservationsKey(flightNumber));
        if (numReservations == null) {
            numReservations = new RMInteger(0);
       }
        Trace.info("RM::queryFlightReservations(" + id + 
                ", #" + flightNumber + ") = " + numReservations);
        return numReservations.getValue();
    }
    */
    
    /*
    // Frees flight reservation record. Flight reservation records help us 
    // make sure we don't delete a flight if one or more customers are 
    // holding reservations.
    public boolean freeFlightReservation(int id, int flightNumber) {
        Trace.info("RM::freeFlightReservations(" + id + ", " 
                + flightNumber + ") called.");
        RMInteger numReservations = (RMInteger) readData(id, 
                Flight.getNumReservationsKey(flightNumber));
        if (numReservations != null) {
            numReservations = new RMInteger(
                    Math.max(0, numReservations.getValue() - 1));
        }
        writeData(id, Flight.getNumReservationsKey(flightNumber), numReservations);
        Trace.info("RM::freeFlightReservations(" + id + ", " 
                + flightNumber + ") OK: reservations = " + numReservations);
        return true;
    }
    */


    // Car operations //

    // Create a new car location or add cars to an existing location.
    // Note: if price <= 0 and the car location already exists, it maintains 
    // its current price.
    @Override
    public boolean addCars(int id, String location, int numCars, int carPrice) {


        String strData ="car,"+location;

        try {

            lockServer.Lock(id, strData, WRITE);
            Trace.info("RM::addCars(" + id + ", " + location + ", "
                    + numCars + ", $" + carPrice + ") called.");
            Car curObj = (Car) readData(id, Car.getKey(location));
            if (curObj == null) {
                // Doesn't exist; add it.
                Car newObj = new Car(location, numCars, carPrice);
                writeData(id, newObj.getKey(), newObj);
                Trace.info("RM::addCars(" + id + ", " + location + ", "
                        + numCars + ", $" + carPrice + ") OK.");
            } else {
                // Add count to existing object and update price.
                curObj.setCount(curObj.getCount() + numCars);
                if (carPrice > 0) {
                    curObj.setPrice(carPrice);
                }
                writeData(id, curObj.getKey(), curObj);
                Trace.info("RM::addCars(" + id + ", " + location + ", "
                        + numCars + ", $" + carPrice + ") OK: "
                        + "cars = " + curObj.getCount() + ", price = $" + carPrice);
            }
            return(true);

        }
        catch (DeadlockException dl) {
            Trace.warn("RM::addCars(" + id + ", " + location + ", "
                    + numCars + ", $" + carPrice + ") failed: ");
            return(false);
        }


    }

    // Delete cars from a location.
    @Override
    public boolean deleteCars(int id, String location) {
        String strData = "car," + location;
        try {

            lockServer.Lock(id, strData, WRITE);
            return deleteItem(id, Car.getKey(location));
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::deleteItem(" + id + ", car " + location + ") failed: ");
            return false;
        }

    }

    // Returns the number of cars available at a location.
    @Override
    public int queryCars(int id, String location) {

        String strData = "car,"+location;
        try {
            lockServer.Lock(id, strData, READ);
            return queryNum(id, Car.getKey(location));
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::queryNum(" + id + ", car " + location + ") failed: ");
            return -1;
        }
    }

    // Returns price of cars at this location.
    @Override
    public int queryCarsPrice(int id, String location) {

        String strData = "car,"+location;
        try {
            lockServer.Lock(id, strData, READ);
            return queryPrice(id, Car.getKey(location));
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::queryPrice(" + id + ", car " + location + ") failed: ");
            return -1;
        }
    }


    // Room operations //

    // Create a new room location or add rooms to an existing location.
    // Note: if price <= 0 and the room location already exists, it maintains 
    // its current price.
    @Override
    public boolean addRooms(int id, String location, int numRooms, int roomPrice) {

        String strData = "room,"+location;
        try {
            lockServer.Lock(id, strData, WRITE);
            Trace.info("RM::addRooms(" + id + ", " + location + ", "
                    + numRooms + ", $" + roomPrice + ") called.");
            Room curObj = (Room) readData(id, Room.getKey(location));
            if (curObj == null) {
                // Doesn't exist; add it.
                Room newObj = new Room(location, numRooms, roomPrice);
                writeData(id, newObj.getKey(), newObj);
                Trace.info("RM::addRooms(" + id + ", " + location + ", "
                        + numRooms + ", $" + roomPrice + ") OK.");
            } else {
                // Add count to existing object and update price.
                curObj.setCount(curObj.getCount() + numRooms);
                if (roomPrice > 0) {
                    curObj.setPrice(roomPrice);
                }
                writeData(id, curObj.getKey(), curObj);
                Trace.info("RM::addRooms(" + id + ", " + location + ", "
                        + numRooms + ", $" + roomPrice + ") OK: "
                        + "rooms = " + curObj.getCount() + ", price = $" + roomPrice);
            }

            return true;
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::addRooms(" + id + ", " + location + ", "
                    + numRooms + ", $" + roomPrice + ") failed");
            return false;
        }
    }

    // Delete rooms from a location.
    @Override
    public boolean deleteRooms(int id, String location) {

        String strData = "room," + location;
        try {
            lockServer.Lock(id, strData, WRITE);
            return deleteItem(id, Room.getKey(location));
        }
        catch (DeadlockException dl) {
            return false;
        }
    }

    // Returns the number of rooms available at a location.
    @Override
    public int queryRooms(int id, String location) {

        String strData = "room,"+location;
        try {
            lockServer.Lock(id, strData,READ);
            return queryNum(id, Room.getKey(location));
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::queryNum(" + id + ", room " + location + ") failed: ");
            return -1;
        }
    }

    // Returns room price at this location.
    @Override
    public int queryRoomsPrice(int id, String location) {

        String strData = "room,"+location;

        try {
            lockServer.Lock(id, strData, READ);
            return queryPrice(id, Room.getKey(location));
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::queryPrice(" + id + ", room " + location + ") failed: ");
            return -1;
        }

    }


    // Customer operations //

    @Override
    public int newCustomer(int id) {

        int customerId = Integer.parseInt(String.valueOf(id) +
                String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
                String.valueOf(Math.round(Math.random() * 100 + 1)));
        String strData = "customer," +customerId;

        try {
            lockServer.Lock(id, strData, WRITE);
            Trace.info("INFO: RM::newCustomer(" + id + ") called.");
            // Generate a globally unique Id for the new customer.

            Customer cust = new Customer(customerId);
            writeData(id, cust.getKey(), cust);
            Trace.info("RM::newCustomer(" + id + ") OK: " + customerId);
            return customerId;
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::newCustomer(" + id + ") failed: ");
            return -1;
        }
    }

    // This method makes testing easier.
    @Override
    public boolean newCustomerId(int id, int customerId) {

        Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") called.");
        String strData = "customer,"+customerId;
        try {
            lockServer.Lock(id, strData, WRITE);
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
        catch (DeadlockException dl) {
            Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") failed.");
            return false;
        }
    }

    // Delete customer from the database. 
    @Override
    public boolean deleteCustomer(int id, int customerId) {
        Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") called.");

        String strData = "customer," +customerId;

        try {
            lockServer.Lock(id, strData, WRITE);
            Customer cust = (Customer) readData(id, Customer.getKey(customerId));
            if (cust == null) {
                Trace.warn("RM::deleteCustomer(" + id + ", "
                        + customerId + ") failed: customer doesn't exist.");
                return false;
            } else {
                // Increase the reserved numbers of all reservable items that
                // the customer reserved.
                RMHashtable reservationHT = cust.getReservations();
                for (Enumeration e = reservationHT.keys(); e.hasMoreElements(); ) {
                    String reservedKey = (String) (e.nextElement());
                    ReservedItem reservedItem = cust.getReservedItem(reservedKey);
                    Trace.info("RM::deleteCustomer(" + id + ", " + customerId + "): "
                            + "deleting " + reservedItem.getCount() + " reservations "
                            + "for item " + reservedItem.getKey());
                    ReservableItem item =
                            (ReservableItem) readData(id, reservedItem.getKey());
                    item.setReserved(item.getReserved() - reservedItem.getCount());
                    item.setCount(item.getCount() + reservedItem.getCount());
                    Trace.info("RM::deleteCustomer(" + id + ", " + customerId + "): "
                            + reservedItem.getKey() + " reserved/available = "
                            + item.getReserved() + "/" + item.getCount());
                }
                // Remove the customer from the storage.
                removeData(id, cust.getKey());
                Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") OK.");
                return true;
            }
        }
        catch (DeadlockException dl) {
            Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") failed.");
            return false;
        }
    }

    // Return data structure containing customer reservation info. 
    // Returns null if the customer doesn't exist. 
    // Returns empty RMHashtable if customer exists but has no reservations.
    protected RMHashtable getCustomerReservations(int id, int customerId) {
        Trace.info("RM::getCustomerReservations(" + id + ", "
                + customerId + ") called.");
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.info("RM::getCustomerReservations(" + id + ", "
                    + customerId + ") failed: customer doesn't exist.");
            return null;
        } else {
            return cust.getReservations();
        }
    }

    // Return a bill.
    @Override
    public String queryCustomerInfo(int id, int customerId) {

        String strData = "customer,"+customerId;
        try {
            lockServer.Lock(id, strData, READ);
            Trace.info("RM::queryCustomerInfo(" + id + ", " + customerId + ") called.");
            Customer cust = (Customer) readData(id, Customer.getKey(customerId));
            if (cust == null) {
                Trace.warn("RM::queryCustomerInfo(" + id + ", "
                        + customerId + ") failed: customer doesn't exist.");
                // Returning an empty bill means that the customer doesn't exist.
                return "WARN: RM::queryCustomerInfo(" + id + ", "
                        + customerId + ") failed: customer doesn't exist.";
            } else {
                String s = cust.printBill();
                Trace.info("RM::queryCustomerInfo(" + id + ", " + customerId + "): \n");
                System.out.println(s);
                return s;
            }
        }
        catch (DeadlockException dl) {
            return "WARN: RM::queryCustomerInfo(" + id + ", "
                    + customerId + ") failed: DeadlockException";
        }
    }

    // Add flight reservation to this customer.  
    @Override
    public boolean reserveFlight(int id, int customerId, int flightNumber) {
        String flightData = "flight,"+flightNumber;
        String custData = "customer,"+customerId;

        try {
            lockServer.Lock(id, flightData, WRITE);
            lockServer.Lock(id, custData, WRITE);
            return reserveItem(id, customerId,
                    Flight.getKey(flightNumber), String.valueOf(flightNumber));
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::reserveItem(" + id + ", "
                    + customerId + "," + flightNumber + ") failed: DeadlockException.");
            return false;
        }
    }

    // Add car reservation to this customer. 
    @Override
    public boolean reserveCar(int id, int customerId, String location) {
        String strCar = "car,"+location;
        String strCustom = "customer," +customerId;
        try {
            lockServer.Lock(id, strCar, WRITE);
            lockServer.Lock(id, strCustom, WRITE);
            return reserveItem(id, customerId, Car.getKey(location), location);
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::reserveItem(" + id + ", "
                    + customerId + "," + location + ") failed: DeadlockException.");
            return false;
        }

    }

    // Add room reservation to this customer.
    @Override
    public boolean reserveRoom(int id, int customerId, String location) {

        String strRoom = "room," +location;
        String strCustomer = "customer," +customerId;
        try {
            lockServer.Lock(id, strRoom, WRITE);
            lockServer.Lock(id, strCustomer, WRITE);
            return reserveItem(id, customerId, Room.getKey(location), location);
        }
        catch (DeadlockException dl) {
            Trace.warn("RM::reserveItem(" + id + ", "
                    + customerId + "," + location + ") failed: DeadlockException.");
            return false;
        }

    }


    // Reserve an itinerary.
    @Override
    public boolean reserveItinerary(int id, int customerId, Vector flightNumbers,
                                    String location, boolean car, boolean room) {
        return false;
    }

    @Override
    public String getFlightKey(int flightNumber){
        return Flight.getKey(flightNumber);
    }
    @Override
    public String getCarKey(String location){
        return Car.getKey(location);
    }
    @Override
    public String getRoomKey(String location){
        return Room.getKey(location);
    }
    @Override
    public boolean updateItemInfo(int id, String key, int resOrUnres){
        ReservableItem item = (ReservableItem) readData(id, key);

        //the key is either flight-#, car-# or room-#

        //create a string flight,1 to lock it up

        String strData = key.substring(0,key.indexOf('-'))+','+key.substring(key.indexOf('-') + 1);

        System.out.println("HERE IS THE KEY:" + key);

        //reserve
        if (resOrUnres == 7) {
            if (item == null) {
                Trace.warn("RM::reserveItem(" + id + ", "
                        + key + ",) failed: item doesn't exist.");
                return false;
            } else {
                try {
                    lockServer.Lock(id, strData, WRITE);
                    item.setCount(item.getCount() - 1);
                    item.setReserved(item.getReserved() + 1);
                    writeData(id,key,item);
                    return true;
                } catch (DeadlockException e) {

                    Trace.warn("RM::reserveItem(" + id + ", "
                            + key + ",) failed: DeadlockException.");
                    return false;
                }

            }
        }
        //unreserve
        else if (resOrUnres == 8){
            if (item == null) {
                Trace.warn("RM::unreserveItem(" + id + ", "
                        + key + ",) failed: item doesn't exist.");
                return false;
            } else {
                try {
                    lockServer.Lock(id, strData, WRITE);
                    item.setCount(item.getCount() + 1);
                    item.setReserved(item.getReserved() - 1);
                    writeData(id,key,item);
                    return true;
                } catch (DeadlockException e) {

                    Trace.warn("RM::unreserveItem(" + id + ", "
                            + key + ",) failed: DeadlockException.");
                    return false;
                }

            }
        }
        else{
            //error
            return false;
        }
    }
    @Override
    public boolean updateDeleteCustomer(int id, String key,int count){
        ReservableItem item = (ReservableItem) readData(id,key);

        if (item == null){
//            error
            return false;
        }
        item.setReserved(item.getReserved() - count);
        item.setCount(item.getCount() + count);

        Trace.info(key + " reserved/available = "
                + item.getReserved() + "/" + item.getCount());
        return true;
    }

    //*************************************************************************************
    // Submission 2

    @Override
    public int start(){

        //you dont need to call this from the server
        return 0;
    }

    @Override
    public boolean commit(int txnId){
        /**testing purposes **/
        if (txnId > 0) {
            Trace.info("RM::COMMIT TRANSACTION ID: "+ txnId);
            return lockServer.UnlockAll(txnId);
        }
        else
            return false;


    }

    @Override
    public boolean abort(int txnId){
        return lockServer.UnlockAll(txnId);
    }

    @Override
    public boolean shutdown(){
        System.exit(0);
        return true;
    }

}