package middleware;

import org.jgroups.*;
import org.jgroups.protocols.rules.SUPERVISOR;
import org.jgroups.util.Util;
import server.Trace;

import java.io.*;
import java.util.*;

/**
 * Created by marcyang on 2015-11-23.
 */
public class MidBroadcast extends ReceiverAdapter implements Runnable{



    JChannel channel;

    private ResourceManagerImpl m_rm;
    private String configFile = null;

    public BitSet bit = new BitSet(2);

    public BitSet PCBit = new BitSet(1);


    final List<String> stateCommand= new LinkedList<>();


    /**Constructor class **/
    public MidBroadcast(String configFile, ResourceManagerImpl resourceManager) {
        System.setProperty("java.net.preferIPv4Stack" , "true");

        this.configFile = configFile;
        this.m_rm = resourceManager;
    }


    public void addCommand(String command) {

        synchronized (stateCommand) {
            stateCommand.add(command);
        }
    }

    public void receive(Message msg) {


        String line = msg.getObject().toString();

        System.out.println("RECEIVED command: " + line);

        Vector arguments = new Vector();
        line = line.trim();
        arguments = parse(line);

        String command = null;
        try {
            command = getString(arguments.elementAt(0));
        } catch (Exception e) {
            e.printStackTrace();
        }

        //if its not PC, then it must be replica
        //Thus, add the command and execute the command
        if (!PCBit.get(0)) {
            synchronized(stateCommand) {
                stateCommand.add(line);
            }
            executeCommand(line);
        }

        /**
         * After executing abort,id
         *
         * Delete the abort transaction id from the list
         * so that the replica will not need to execute upon recovery
         *
         *
         * **/

        assert command != null;
        if(command.compareToIgnoreCase("abort") == 0) {
            try {

                int id = getInt(arguments.elementAt(1));

                deleteHistory(id);
                System.out.println("deleted the abort transaction id : "+ id +" in history: ");
                System.out.println("history size : " + stateCommand.size());
            } catch (Exception e) {
                e.printStackTrace();
            }

        }



    }

    private void deleteHistory(int id) {
        Vector arg = new Vector();
        synchronized(stateCommand) {
            for (int i = stateCommand.size()-1; i>=0; i--) {
                String c = stateCommand.get(i);
                arg = parse(c);
                try {
                    if (getInt(arg.elementAt(1)) == id) {
                        stateCommand.remove(c);
                    }
                } catch (Exception e) {
                    System.out.println("REP: fail to parse");
                }
            }
        }
    }



    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
        if (channel.getView().getMembers().get(0).toString().equals(channel.getName().toString())) {
            System.out.println("** coordinator = this channel: set the bit ");
            PCBit.set(0);
        }

    }


    public void getState(OutputStream output) throws Exception {

        synchronized(stateCommand) {
            Util.objectToStream(stateCommand, new DataOutputStream(output));
        }

    }

    public void setState(InputStream input) {

        DataInputStream d = new DataInputStream(input);
        List<String> list= null;
        try {
            list = (List<String>) Util.objectFromStream(d);
        } catch (Exception e) {
            e.printStackTrace();
        }
        synchronized(stateCommand) {
            stateCommand.clear();
            stateCommand.addAll(list);
            System.out.println("HISTORY OF COMMANDS: ");

            for (int i = 0; i<stateCommand.size(); i++) {
                if (stateCommand.get(i)!= null) {
                    System.out.println("\n" + stateCommand.get(i) + "\n");
                    executeCommand(stateCommand.get(i));
                }
            }
        }


    }

    private void multicast() {

        while(true) {
            try {
                if(bit.get(0) && !bit.get(1)) {

                    System.out.flush();
                    String line = stateCommand.get(stateCommand.size()-1);
                    System.out.println("SENDING THE PACKET");
                    Message msg = new Message(null, null, line);
                    channel.send(msg);
                    //turn off after sending the message
                    bit.flip(0);

                    line = line.trim();

                    Vector arguments = new Vector();

                    arguments = parse(line);
                    String command = getString(arguments.elementAt(0));

                    /**
                     * After executing abort,id
                     *
                     * Delete the abort transaction id from the list
                     * so that the replica will not need to execute upon recovery
                     *
                     *
                     * **/
                    assert command != null;
                    if(command.compareToIgnoreCase("abort") == 0) {
                        try {

                            int id = getInt(arguments.elementAt(1));

                            deleteHistory(id);
                            System.out.println("deleted the abort transaction id : "+ id +" in history: ");
                            System.out.println("history size : " + stateCommand.size());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }

                }
                else if (bit.get(1)) {
                    break;
                }
            }
            catch(Exception e) {

            }
        }
    }


    @Override
    public void run() {
        String workingDir = System.getProperty("user.dir");
        String serverConfig = workingDir+"/conf/" + configFile;
        try {
            channel = new JChannel(serverConfig);
            channel.setReceiver(this);
            channel.connect("Middleware-Cluster");
            channel.getState(null, 10000);
            multicast();
            System.out.println("CHANNEL CLOSED???");
            channel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void executeCommand(String command) {

        System.out.println("execute: " + command);
        int id = -1;
        int flightNumber = -1;
        int flightPrice = -1;
        int numSeats = -1;
        boolean room = false;
        boolean car = false;
        boolean status = false;
        int price = -1;
        int numRooms = -1;
        int numCars = -1;
        String location = "";
        Vector arguments = new Vector();

        command = command.trim();
        arguments = parse(command);

        //decide which of the commands this was
        int choice = findChoice((String) arguments.elementAt(0));
        //System.out.println("\n\n\nOPTION: " + choice);
        switch(choice) {
            case 2:  //new flight
                if (arguments.size() != 6) {
                    wrongNumber();
                    break;
                }
                try {

                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));
                    //not used
                    //numSeats = getInt(arguments.elementAt(3));
                    //flightPrice = getInt(arguments.elementAt(4));
                    status = getBoolean(arguments.elementAt(5));

                    if (status) {
                        //add to the transaction manager
                        Vector cmd = cmdToVect(this.m_rm.FLIGHT, this.m_rm.DEL, flightNumber);
                        this.m_rm.txnManager.setNewUpdateItem(id, cmd);
                        //set active RM list
                        this.m_rm.txnManager.enlist(id, m_rm.FLIGHT);

                        System.out.println("REP: Flight is added in the replica");
                    } else {
                        System.out.println("REP: Flight " + flightNumber + " is not added in the replica");
                        break;
                    }

                    //start ttl
                    m_rm.ttl[id - 1].pushItem(id);

                } catch (Exception e) {
                    System.out.println("EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                break;
            case 3: //newcar
                if (arguments.size() != 6) {
                    wrongNumber();
                    break;
                }
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    status = getBoolean(arguments.elementAt(5));

                    if (status) {
                        //add to txnmanager and enlist the rm
                        Vector cmd = cmdToVect(this.m_rm.CAR, this.m_rm.DEL, Integer.parseInt(location));
                        this.m_rm.txnManager.setNewUpdateItem(id, cmd);
                        this.m_rm.txnManager.enlist(id, this.m_rm.CAR);

                        System.out.println("REP: Car " + location + "is added in the replica");
                    } else {
                        System.out.println("REP: Car could not be added to the replica");
                        break;
                    }
                    //start ttl
                    m_rm.ttl[id - 1].pushItem(id);

                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;

            case 4: //newroom
                if (arguments.size() != 6) {
                    wrongNumber();
                    break;
                }
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    status = getBoolean(arguments.elementAt(5));

                    if (status) {
                        Vector cmd = cmdToVect(this.m_rm.ROOM, this.m_rm.DEL, Integer.parseInt(location));
                        this.m_rm.txnManager.setNewUpdateItem(id, cmd);
                        this.m_rm.txnManager.enlist(id, this.m_rm.ROOM);

                        System.out.println("REP: Room " + location + " added to the replica");
                    } else {
                        System.out.println("REP: Room could not be added to the replica");
                        break;
                    }
                    //start ttl
                    m_rm.ttl[id - 1].pushItem(id);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;

            case 5: //new customer
                if (arguments.size() != 2) {
                    wrongNumber();
                    break;
                }
                try {
                    id = getInt(arguments.elementAt(1));
                    if (id > 0) {
                        int custId = this.m_rm.newCustomer(id);
                        if (custId > 0) {
                            System.out.println("REP: Added Customer with customer id: " + custId);
                        } else {
                            System.out.println("REP: Could not create new customer");
                            break;
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                m_rm.ttl[id - 1].pushItem(id);

                break;

            case 6: //deleteflight
                if (arguments.size() != 4) {
                    wrongNumber();
                    break;
                }
                System.out.println("REP: Deleting flight number" + arguments.elementAt(2) +
                        " with customer id: " + arguments.elementAt(1));
                System.out.println("REP: Waiting for response from server");
                try {
                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));
                    numSeats = this.m_rm.queryFlight(id, flightNumber);
                    flightPrice = this.m_rm.queryFlightPrice(id, flightNumber);
                    status = getBoolean(arguments.elementAt(3));

                    if (status) {
                        Vector cmd = cmdToVect(this.m_rm.FLIGHT, this.m_rm.ADD, flightNumber);
                        cmd.add(numSeats);
                        cmd.add(flightPrice);
                        this.m_rm.txnManager.setNewUpdateItem(id, cmd);
                        this.m_rm.txnManager.enlist(id, m_rm.FLIGHT);

                        System.out.println("REP: Deleted flight: " + flightNumber);
                    } else {
                        System.out.println("REP: Could not delete fight: " + flightNumber);
                    }
                    //start TTL
                    this.m_rm.ttl[id - 1].pushItem(id);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                break;
            case 7: //deletecar
                if (arguments.size() != 4) {
                    wrongNumber();
                    break;
                }
                System.out.println("REP: Deleting car " + arguments.elementAt(2) +
                        " with customer id: " + arguments.elementAt(1));
                System.out.println("REP: Waiting for response from server");
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    status = getBoolean(arguments.elementAt(3));
                    numCars = this.m_rm.queryCars(id, location);
                    price = this.m_rm.queryCarsPrice(id, location);

                    if (status) {
                        Vector cmd = cmdToVect(this.m_rm.CAR, this.m_rm.ADD, Integer.parseInt(location));
                        cmd.add(numCars);
                        cmd.add(price);
                        this.m_rm.txnManager.setNewUpdateItem(id, cmd);
                        this.m_rm.txnManager.enlist(id, this.m_rm.CAR);

                        System.out.println("REP: Deleted car: " + location);
                    }

                    //start TTL
                    this.m_rm.ttl[id - 1].pushItem(id);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case 8: //deleteroom
                if (arguments.size() != 4) {
                    wrongNumber();
                    break;
                }
                System.out.println("REP: Deleting car " + arguments.elementAt(2) +
                        " with customer id: " + arguments.elementAt(1));
                System.out.println("REP: Waiting for response from server");
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    status = getBoolean(arguments.elementAt(3));
                    numRooms = this.m_rm.queryRooms(id, location);
                    price = this.m_rm.queryRoomsPrice(id, location);

                    if (status) {
                        Vector cmd = cmdToVect(this.m_rm.ROOM, this.m_rm.ADD, Integer.parseInt(location));
                        cmd.add(numRooms);
                        cmd.add(price);
                        this.m_rm.txnManager.setNewUpdateItem(id, cmd);
                        this.m_rm.txnManager.enlist(id, this.m_rm.ROOM);

                        System.out.println("REP: Deleted room:" + location);
                    }
                    //start TTL
                    this.m_rm.ttl[id - 1].pushItem(id);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case 9: //delete customer
                if (arguments.size() != 2){
                    wrongNumber();
                    break;
                }
                System.out.println("REP: Deleting customer");
                try {
                    id = getInt(arguments.elementAt(1));
                    int customerId = getInt(arguments.elementAt(2));

                    if (this.m_rm.deleteCustomer(id,customerId)){
                        System.out.println("REP: Customer "+ id + "was deleted");
                    }
                    else {
                        System.out.println("REP: Customer "+ id + "was NOT deleted");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;

            case 10: //query flight

                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("REP: Querying a flight using id: " + arguments.elementAt(1));
                System.out.println("REP: Flight number: " + arguments.elementAt(2));
                System.out.println("REP: Waiting for response from the server...");
                try {
                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));
                    int seats = m_rm.queryFlight(id, flightNumber);
                    Trace.info("REP: Number of seats for flight " + flightNumber + ": " + seats);
                    //start TTL
                    this.m_rm.ttl[id - 1].pushItem(id);
                } catch (Exception e) {
                    System.out.println("REP: EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }

                break;
            case 11: //query car location
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("REP: Querying a car using id: " + arguments.elementAt(1));
                System.out.println("REP: Car location: " + arguments.elementAt(2));
                System.out.println("REP: Waiting for response from the server...");
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    numCars = m_rm.queryCars(id, location);
                    System.out.println("REP: Number of cars for car " + location + ": " + numCars);
                    //start TTL
                    this.m_rm.ttl[id - 1].pushItem(id);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case 12: //query room location
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("REP: Querying a room using id: " + arguments.elementAt(1));
                System.out.println("REP: Room location: " + arguments.elementAt(2));
                System.out.println("REP: Waiting for response from the server...");
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    numRooms = m_rm.queryRooms(id, location);
                    System.out.println("REP: Number of rooms for room " + location + ": " + numRooms);
                    //start TTL
                    this.m_rm.ttl[id - 1].pushItem(id);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case 13: //query customer
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                try {
                    id = getInt(arguments.elementAt(1));
                    int customer = getInt(arguments.elementAt(2));
                    String info = this.m_rm.queryCustomerInfo(id, customer);
                    System.out.println("REP: Customer info for id " + id + ": " + info);
                    m_rm.txnManager.enlist(id, m_rm.CUST);

                } catch (Exception e) {
                    System.out.println("REP: EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                m_rm.ttl[id - 1].pushItem(id);

                break;
            case 14: //query flight price
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                try {
                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));
                    flightPrice = this.m_rm.queryFlightPrice(id, flightNumber);
                    System.out.println("REP: Flight price for flight " + flightNumber + ": " + flightPrice);

                } catch (Exception e) {
                    e.printStackTrace();
                }
                m_rm.ttl[id - 1].pushItem(id);

                break;
            case 15: //query car price
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    price = this.m_rm.queryCarsPrice(id, location);
                    System.out.println("REP: Car price for car " + location + ": " + price);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                m_rm.ttl[id - 1].pushItem(id);

                break;
            case 16: //query room price
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    price = this.m_rm.queryRoomsPrice(id, location);
                    System.out.println("REP: Room price for room " + location + ": " + price);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                m_rm.ttl[id - 1].pushItem(id);

                break;

            case 17:  //reserve a flight
                if (arguments.size() != 5) {
                    wrongNumber();
                    break;
                }
                try {
                    id = getInt(arguments.elementAt(1));
                    int customerId = getInt(arguments.elementAt(2));
                    flightNumber = getInt(arguments.elementAt(3));
                    String key = getString(arguments.elementAt(4));

                    price = this.m_rm.queryFlightPrice(id, flightNumber);
                    location = String.valueOf(flightNumber);

                    //directly write to the customer database
                    if (reserveItem(id, customerId, location, key, price, this.m_rm.FLIGHT)) {
                        System.out.println("REP: flight number " + flightNumber + " is reserved");
                    } else {
                        System.out.println("REP: flight number " + flightNumber + " is not reserved");
                    }
                    m_rm.ttl[id - 1].pushItem(id);
                } catch (Exception e) {
                    System.out.println("REP: EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                break;
            case 18: //reserve car
                if (arguments.size() != 5) {
                    wrongNumber();
                    break;
                }
                try{
                    id = getInt(arguments.elementAt(1));
                    int customerId = getInt(arguments.elementAt(2));
                    location = getString(arguments.elementAt(3));
                    String key = getString(arguments.elementAt(4));
                    price = this.m_rm.queryCarsPrice(id,location);

                    if (reserveItem(id,customerId,location,key,price,this.m_rm.CAR)){
                        System.out.println("REP: car location: "+ location + " is reserved");
                    }
                    else{
                        System.out.println("REP: car location: "+ location + " is not reserved");
                    }
                    m_rm.ttl[id - 1].pushItem(id);

                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case 19: //reserve room
                if (arguments.size() != 5 ){
                    wrongNumber();
                    break;
                }
                try{
                    id = getInt(arguments.elementAt(1));
                    int customerId = getInt(arguments.elementAt(2));
                    location = getString(arguments.elementAt(3));
                    String key = getString(arguments.elementAt(4));
                    price = this.m_rm.queryRoomsPrice(id,location);

                    if (reserveItem(id,customerId,location,key,price,this.m_rm.ROOM)){
                        System.out.println("REP: room location: "+ location + " is reserved");
                    }
                    else{
                        System.out.println("REP: room location: "+ location + " is not reserved");
                    }
                    m_rm.ttl[id - 1].pushItem(id);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case 20: //reserve itinerary
                //should we pass it as a vector?
                if (arguments.size() < 13){
                    wrongNumber();
                    break;
                }
                try {
                    id = getInt(arguments.elementAt(1));
                    int custId =  getInt(arguments.elementAt(2));
                    Vector flights = new Vector();
                    int i;
                    for ( i =3 ; i<arguments.size() ;i ++ ){
                        if (getInt(arguments.elementAt(i)) == -1 ) break;
                        else {
                            flights.add(arguments.elementAt(i));
                        }
                    }
                    location = getString(arguments.elementAt(i+1));
                    car = getBoolean(arguments.elementAt(i+2));
                    room = getBoolean(arguments.elementAt(i+4));
                    String carKey = getString(arguments.elementAt(i+5));
                    int carPrice = getInt(arguments.elementAt(i+6));
                    String roomKey = getString(arguments.elementAt(i+7));
                    int roomPrice = getInt(arguments.elementAt(i+8));

                    if (car && room){
                        boolean cpass = reserveItem(id,custId,location,carKey,carPrice,this.m_rm.CAR);
                        boolean rpass = reserveItem(id,custId,location,roomKey,roomPrice,this.m_rm.ROOM);
                        if (cpass && rpass){
                            System.out.println("REP: Reserved car and room at location: " + location+ " with id "+id);
                        }
                        else{
                            System.out.println("REP: Could not reserve car and room at location: " + location + " with id "+id);
                            break;
                        }
                    }
                    else if (car){
                        boolean cpass = reserveItem(id,custId,location,carKey,carPrice,this.m_rm.CAR);
                        if (cpass){
                            System.out.println("REP: Reserved car at location: " + location+ " with id "+id);
                        }
                        else{
                            System.out.println("REP: Could not reserve car at location: " + location + " with id "+id);
                            break;
                        }

                    }
                    else if (room){
                        boolean rpass = reserveItem(id,custId,location,roomKey,roomPrice,this.m_rm.ROOM);

                        if (rpass){
                            System.out.println("REP: Reserved room at location: " + location+ " with id "+id);
                        }
                        else{
                            System.out.println("REP: Could not reserve room at location: " + location + " with id "+id);
                            break;
                        }

                    }

                    Iterator it = flights.iterator();
                    while (it.hasNext()){
                        String flightNum = (String) it.next();
                        String flightKey = (String) it.next();
                        Integer fprice = (Integer) it.next();
                        if (reserveItem(id,custId,flightNum,flightKey,fprice,this.m_rm.FLIGHT)){
                            System.out.println("REP: Could not reserve flight: "+flightNum + " with if " + id);
                            break;
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;

            case 22:  //new Customer given id
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("REP: Adding a new Customer using id: "
                        + arguments.elementAt(1)  +  " and cid "  + arguments.elementAt(2));
                System.out.println("REP: Waiting for response from server...");

                /**Dont need to start TTL right here because it will start once call the newcustomerid()**/
                try {
                    id = getInt(arguments.elementAt(1));
                    int customer = getInt(arguments.elementAt(2));
                    if (id >= 0 && customer >= 0) {
                        if (this.m_rm.newCustomerId(id, customer)) {
                            System.out.println("REP: new customer id: " + customer);
                        } else {
                            System.out.println("REP: Customer already exists");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                m_rm.ttl[id-1].pushItem(id);

                break;

            case 23: //start method
                this.m_rm.start();
                break;

            case 24: //commit method
                if (arguments.size() != 2) { //command was "commit"
                    wrongNumber();
                    break;
                }
                else {
                    try {
                        int transactionID = getInt(arguments.elementAt(1));
                        if (m_rm.commit(transactionID)){
                            System.out.println("REP: Commit transaction " + transactionID + " successfully");
                        }
                        else {
                            System.out.println("REP: Invalid transaction ID or failed to unlock");
                        }
                    } catch (Exception e) {
                        System.out.println("REP: EXCEPTION: ");
                        e.printStackTrace();
                    }
                }
                break;
            case 25: // abort method
                if (arguments.size() != 2) { //command was "abort"
                    wrongNumber();
                    break;
                }
                else {
                    try {
                        int transactionID = getInt(arguments.elementAt(1));
                        abort(transactionID);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            case 26: //shutdown
                this.m_rm.shutdown();
                break;

            default:
                System.out.println("REP: The interface does not support this command.");
                break;
        }

    }//end of execute function

    /**This method writes the reservations to the customer database and add the record to transaction manager **/
    private boolean reserveItem(int id, int customerId, String location, String key, int price, int itemInfo) {
        // Read customer object if it exists
        boolean reserved = false;
        Customer cust = (Customer) this.m_rm.readData(id, Customer.getKey(customerId));
        if (cust != null) {
            //customer reserves it
            cust.reserve(key, location, price, itemInfo, id); //change location maybe
            this.m_rm.writeData(id, cust.getKey(), cust);

            Vector cmd = cmdToVect(this.m_rm.CUST,this.m_rm.UNRES,customerId);
            cmd.add(Integer.parseInt(location));
            cmd.add(key);
            cmd.add(itemInfo);
            m_rm.txnManager.setNewUpdateItem(id,cmd);
            reserved = true;
        }

        return reserved;
    }

    /** abort only needs to focus on the customer data,
     * all the RM data is supposed to be handled by the Primary Copy
     *
     * **/
    private boolean abort(int txnId) {
        //turn on the transaction bit
        m_rm.ttl[txnId-1].pushAbort(txnId);
        m_rm.transactionBit.set(txnId);
        //this.transactionBit.set(txnId);
        //get the commands from the stack of commands and execute them
        Stack cmdList;
        try {
            cmdList = m_rm.txnManager.txnCmdList.get(txnId);
        }
        catch (NullPointerException e){
            e.printStackTrace();
            Trace.warn("NEED TO START TRANSCATION BEFORE CALLING ABORT");
            return false;
        }
        while (!cmdList.isEmpty()) {
            Vector cmd = (Vector) cmdList.pop();
            Integer RMType = (Integer) cmd.get(0);
            Integer queryType = (Integer) cmd.get(1);
            Integer location = (Integer) cmd.get(2);

            switch (RMType) {

                case ResourceManagerImpl.CUST:
                    switch (queryType) {
                        case ResourceManagerImpl.ADD:
                            //location is the customerId. it gets set in newcustomer
                            if (!m_rm.newCustomerId(txnId, location)) {
                                Trace.warn("FAILED TO CREATE NEW CUSTOMER UPON ABORT: " + txnId);
                                return false;
                            }
                            break;
                        case ResourceManagerImpl.DEL:
                            //location is the customerId. it gets set in newcustomer
                            boolean isDeleted = m_rm.deleteCustomer(txnId, location);
                            if (!isDeleted) {
                                Trace.warn("FAILED TO DELETE CUSTOMER UPON ABORT: " + txnId);
                                return false;
                            }
                            break;
                        case ResourceManagerImpl.UNRES:
                            String objLocation = String.valueOf(cmd.get(3));
                            String objKey = (String) cmd.get(4);
                            Integer itemInfo = (Integer) cmd.get(5);
                            if (!m_rm.unReserveItem(txnId, location, objLocation, objKey, itemInfo)) {
                                Trace.warn("REP: FAILED UNRERSERVE CUSTOMER UPON ABORT: " + txnId);
                                return false;
                            }
                            break;
                    }
                    break;
            }
        }

        return true;
    }




    public int findChoice(String argument) {
        if (argument.compareToIgnoreCase("help") == 0)
            return 1;
        else if (argument.compareToIgnoreCase("newflight") == 0)
            return 2;
        else if (argument.compareToIgnoreCase("newcar") == 0)
            return 3;
        else if (argument.compareToIgnoreCase("newroom") == 0)
            return 4;
        else if (argument.compareToIgnoreCase("newcustomer") == 0)
            return 5;
        else if (argument.compareToIgnoreCase("deleteflight") == 0)
            return 6;
        else if (argument.compareToIgnoreCase("deletecar") == 0)
            return 7;
        else if (argument.compareToIgnoreCase("deleteroom") == 0)
            return 8;
        else if (argument.compareToIgnoreCase("deletecustomer") == 0)
            return 9;
        else if (argument.compareToIgnoreCase("queryflight") == 0)
            return 10;
        else if (argument.compareToIgnoreCase("querycar") == 0)
            return 11;
        else if (argument.compareToIgnoreCase("queryroom") == 0)
            return 12;
        else if (argument.compareToIgnoreCase("querycustomer") == 0)
            return 13;
        else if (argument.compareToIgnoreCase("queryflightprice") == 0)
            return 14;
        else if (argument.compareToIgnoreCase("querycarprice") == 0)
            return 15;
        else if (argument.compareToIgnoreCase("queryroomprice") == 0)
            return 16;
        else if (argument.compareToIgnoreCase("reserveflight") == 0)
            return 17;
        else if (argument.compareToIgnoreCase("reservecar") == 0)
            return 18;
        else if (argument.compareToIgnoreCase("reserveroom") == 0)
            return 19;
        else if (argument.compareToIgnoreCase("itinerary") == 0)
            return 20;
        else if (argument.compareToIgnoreCase("quit") == 0)
            return 21;
        else if (argument.compareToIgnoreCase("newcustomerid") == 0)
            return 22;
        else if (argument.compareToIgnoreCase("start") == 0)
            return 23;
        else if (argument.compareToIgnoreCase("commit") == 0)
            return 24;
        else if (argument.compareToIgnoreCase("abort") == 0)
            return 25;
        else if (argument.compareToIgnoreCase("shutdown") == 0)
            return 26;
        else
            return 666;
    }

    public Vector parse(String command) {
        Vector arguments = new Vector();
        StringTokenizer tokenizer = new StringTokenizer(command, ",");
        String argument = "";
        while (tokenizer.hasMoreTokens()) {
            argument = tokenizer.nextToken();
            argument = argument.trim();
            arguments.add(argument);
        }
        return arguments;
    }


    public void wrongNumber() {
        System.out.println("The number of arguments provided in this command are wrong.");
        System.out.println("Type help, <commandname> to check usage of this command.");
    }

    public int getInt(Object temp) throws Exception {
        try {
            return (new Integer((String)temp)).intValue();
        }
        catch(Exception e) {
            throw e;
        }
    }

    public boolean getBoolean(Object temp) throws Exception {
        try {
            return (new Boolean((String)temp)).booleanValue();
        }
        catch(Exception e) {
            throw e;
        }
    }

    public String getString(Object temp) throws Exception {
        try {
            return (String)temp;
        }
        catch (Exception e) {
            throw e;
        }
    }

    protected Vector cmdToVect(int RMType, int queryType, int itemNumOrLocation){
        Vector cmd = new Vector();
        cmd.add(RMType);
        cmd.add(queryType);
        cmd.add(itemNumOrLocation);

        return cmd;
    }

}