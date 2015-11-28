package server;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.util.ArrayIterator;
import org.jgroups.util.Util;
import java.io.*;
import java.util.*;

import server.LockManager.LockManager;
import server.RMHashtable;


/**
 * Created by marcyang on 2015-11-22.
 */
public class Broadcast extends ReceiverAdapter implements Runnable {


    JChannel channel;


    public BitSet bit = new BitSet(2);

    public BitSet PCBit = new BitSet(1);
    String configFile = null;

    BitSet firstTime = new BitSet(1);

    ResourceManagerImpl s_rm = null;

    final List<String> state = new LinkedList<>();


    public Broadcast(String configFile, ResourceManagerImpl resourceManager) {
        System.setProperty("java.net.preferIPv4Stack" , "true");

        this.configFile = configFile;
        this.s_rm = resourceManager;

        firstTime.set(0);


    }

    public void receive(Message msg) {



        //line is newflight,1,1,1,1 (command as a whole)
        //command is only newflight (title)

        String line = msg.getObject().toString();
        line = line.trim();

        Vector arguments = new Vector();
        arguments = parse(line);


        String command = null;
        try {
            command = getString(arguments.elementAt(0));

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Command :" +line);
        if (!PCBit.get(0)) {
            synchronized(state) {
                state.add(line);
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
                System.out.println("history size : " + state.size());
            } catch (Exception e) {
                e.printStackTrace();
            }

        }



    }

    private void deleteHistory(int id) {
        Vector arg = new Vector();
        synchronized(state) {
            for (int i = state.size()-1; i>=0; i--) {
                String c = state.get(i);
                arg = parse(c);
                try {
                    if (getInt(arg.elementAt(1)) == id) {
                        state.remove(c);
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
        synchronized(state) {
            Util.objectToStream(state, new DataOutputStream(output));
        }
        /*
        //send the history one by one to the new member
        DataOutputStream out = null;
        synchronized(state) {
            for (int i = 0; i < state.size(); i++) {
                out = new DataOutputStream(output);
                out.writeUTF(state.get(i));
            }
        }
        */
    }

    public void setState(InputStream input) {

        DataInputStream d = new DataInputStream(input);
        List<String> list= null;
        try {
            list = (List<String>) Util.objectFromStream(d);
        } catch (Exception e) {
            e.printStackTrace();
        }
        synchronized(state) {
            state.clear();
            state.addAll(list);
            System.out.println("HISTORY OF COMMANDS: ");
            for (int i = 0; i<state.size(); i++) {
                if (state.get(i)!= null) {
                    System.out.println("\n" + state.get(i) + "\n");
                    executeCommand(state.get(i));
                }
            }
        }



        /*
        DataInputStream d = new DataInputStream(input);
        String commandhistory = null;
        try {
            commandhistory = d.readUTF();
        } catch (Exception e) {
            e.printStackTrace();
        }
        synchronized(state) {
            //clear the list if its first time joining
            if (firstTime.get(0)) {
                state.clear();
                firstTime.flip(0);
            }
            //add the history
            if (commandhistory != null) {
                state.add(commandhistory);
                executeCommand(commandhistory);
            }
        }
        */


    }


    private void multicast() {

        while(true) {
            try {
                if(bit.get(0) && !bit.get(1)) {

                    System.out.flush();
                    String line = state.get(state.size()-1);
                    System.out.println("REP: sending command: "+ line);
                    Message msg = new Message(null, null, line);
                    channel.send(msg);
                    //turn off after sending the message
                    bit.flip(0);

                    /**
                     * After executing abort,id
                     *
                     * Delete the abort transaction id from the list
                     * so that the replica will not need to execute upon recovery
                     *
                     *
                     * **/
                    line = line.trim();
                    Vector arguments = new Vector();

                    arguments = parse(line);
                    String command = getString(arguments.elementAt(0));

                    assert command != null;
                    if(command.compareToIgnoreCase("abort") == 0) {
                        try {

                            int id = getInt(arguments.elementAt(1));

                            deleteHistory(id);
                            System.out.println("deleted the abort transaction id : "+ id +" in history: ");
                            System.out.println("history size : " + state.size());
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
            channel.connect("Flight-Cluster");
            channel.getState(null, 10000);
            multicast();
            channel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void executeCommand(String command) {

        System.out.println("Execute: " + command);


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
                if (arguments.size() != 5) {
                    wrongNumber();
                    break;
                }
                try {
                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));
                    numSeats = getInt(arguments.elementAt(3));
                    flightPrice = getInt(arguments.elementAt(4));

                    s_rm.addFlight(id, flightNumber, numSeats, flightPrice);
                    System.out.println("REP: flight added");

                } catch (Exception e) {
                    System.out.println("EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                break;

            case 3:  //new car
                if (arguments.size() != 5) {
                    wrongNumber();
                    break;
                }
                System.out.println("Adding a new car using id: " + arguments.elementAt(1));
                System.out.println("car Location: " + arguments.elementAt(2));
                System.out.println("Add Number of cars: " + arguments.elementAt(3));
                System.out.println("Set Price: " + arguments.elementAt(4));
                System.out.println("Waiting for a response from the server...");
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    numCars = getInt(arguments.elementAt(3));
                    price = getInt(arguments.elementAt(4));

                    if (s_rm.addCars(id, location, numCars, price))
                        System.out.println("REP: cars added");
                    else
                        System.out.println("REP: cars could not be added");
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                break;
            case 4:  //new room
                if (arguments.size() != 5) {
                    wrongNumber();
                    break;
                }
                System.out.println("Adding a new room using id: " + arguments.elementAt(1));
                System.out.println("room Location: " + arguments.elementAt(2));
                System.out.println("Add Number of rooms: " + arguments.elementAt(3));
                System.out.println("Set Price: " + arguments.elementAt(4));
                System.out.println("Waiting for a response from the server...");
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    numRooms = getInt(arguments.elementAt(3));
                    price = getInt(arguments.elementAt(4));

                    if (s_rm.addRooms(id, location, numRooms, price))
                        System.out.println("REP: rooms added");
                    else
                        System.out.println("REP: rooms could not be added");
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                break;
            case 5: //new customer
                //dont care about this in the RM

                break;
            case 6: //delete flight
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("REP: Deleting a flight using id: " + arguments.elementAt(1));
                System.out.println("REP: Flight Number: " + arguments.elementAt(2));
                System.out.println("REP: Waiting for response from the server...");
                try {
                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));

                    if (s_rm.deleteFlight(id, flightNumber))
                        System.out.println("REP: Flight Deleted");
                    else
                        System.out.println("REP: Flight could not be deleted");

                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                break;

            case 7: //delete car
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Deleting the cars from a particular location  using id: " + arguments.elementAt(1));
                System.out.println("car Location: " + arguments.elementAt(2));
                System.out.println("Waiting for response from the server...");
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));

                    if (s_rm.deleteCars(id, location))
                        System.out.println("REP: cars Deleted");
                    else
                        System.out.println("REP: cars could not be deleted");
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                break;
            case 8: //delete room
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Deleting all rooms from a particular location  using id: " + arguments.elementAt(1));
                System.out.println("room Location: " + arguments.elementAt(2));
                System.out.println("Waiting for response from the server...");
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));

                    if (s_rm.deleteRooms(id, location))
                        System.out.println("rooms Deleted");
                    else
                        System.out.println("rooms could not be deleted");

                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                break;

            case 9: //delete customer

                //becomes new case --> updateDeleteCustomer
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
                    int seats = s_rm.queryFlight(id, flightNumber);
                    System.out.println("REP: Number of seats for flight " + flightNumber + ": " + seats);
                } catch (Exception e) {
                    System.out.println("REP: EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }

                break;
            case 11: //querying a car Location
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying a car location using id: " + arguments.elementAt(1));
                System.out.println("car location: " + arguments.elementAt(2));
                System.out.println("Waiting for response from the server...");
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    System.out.println("Waiting for a response from the server...");
                    numCars = s_rm.queryCars(id, location);
                    if (numCars >= 0) {
                        System.out.println("number of cars at this location: " + numCars);
                    }
                    else {
                    System.out.println("ERROR: some other process might lock on that car location " + location);
                    }

                }catch(Exception e) {
                    System.out.println("EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                break;
            case 12: //querying a room location
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying a room location using id: " + arguments.elementAt(1));
                System.out.println("room location: " + arguments.elementAt(2));
                System.out.println("Waiting for response from server...");
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));

                    numRooms = s_rm.queryRooms(id, location);
                    if (numRooms >= 0) {
                        System.out.println("number of rooms at this location: " + numRooms);
                    }
                    else {
                        System.out.println("ERROR: Some other process is locking on the room location " + location);
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                break;

            case 13: //query customer

                //dont care about this in the server
                break;
            case 14: //querying a flight Price
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying a flight Price using id: " + arguments.elementAt(1));
                System.out.println("Flight number: " + arguments.elementAt(2));
                System.out.println("Waiting for response from the server...");
                try {
                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));
                    price = s_rm.queryFlightPrice(id, flightNumber);
                    if (price >= 0) {
                        System.out.println("Price of a seat: " + price);
                    }
                    else {
                        System.out.println("ERROR: other process is locking on flight# " + flightNumber);
                    }
                }catch(Exception e) {
                    System.out.println("EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                break;
            case 15: //querying a car Price
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying a car price using id: " + arguments.elementAt(1));
                System.out.println("car location: " + arguments.elementAt(2));
                System.out.println("Waiting for response from server...");
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));

                    price = s_rm.queryCarsPrice(id, location);
                    if (price >= 0) {
                        System.out.println("Price of a car at this location: " + price);
                    }
                    else {
                        System.out.println("ERROR: other process is locking on car location: " + location);
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                break;
            case 16: //querying a room price
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying a room price using id: " + arguments.elementAt(1));
                System.out.println("room Location: " + arguments.elementAt(2));
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    price = s_rm.queryRoomsPrice(id, location);
                    if (price >= 0) {
                        System.out.println("Price of rooms at this location: " + price);
                    }
                    else {
                        System.out.println("ERROR: other process is locking on room location: " + location);
                    }

                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                break;


            case 17:  //reserve a flight --> goes to case updateItemInfo

                /*
                if (arguments.size() != 4) {
                    wrongNumber();
                    break;
                }

                try {
                    id = getInt(arguments.elementAt(1));
                    String key = getString(arguments.elementAt(2));
                    int resOrUnres = getInt(arguments.elementAt(3));

                    //directly call the updateItem info and add one item from the database
                    if (s_rm.updateItemInfo(id, key, resOrUnres)) {
                        System.out.println("REP: flight number "+ key + " is reserved");
                    }
                    else {
                        System.out.println("REP: flight number " + flightNumber + " is not reserved");
                    }
                }
                catch(Exception e) {
                    System.out.println("REP: EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
                */
                break;
            case 18:  //reserve a car ---> goes to case updateItemInfo

                break;
            case 19:  //reserve a room --> goes to case updateItemInfo

                break;
            case 20:  //reserve an Itinerary
                //dont care in the RM
                //only execute from the middleware

                break;

            case 22:  //new Customer given id
                //dont care about this in the server

                break;

            case 23: //start method
                //dont care about this in the server
                break;

            case 24: //commit method
                if (arguments.size() != 2) { //command was "commit"
                    wrongNumber();
                    break;
                }
                else {
                    try {
                        int transactionID = getInt(arguments.elementAt(1));
                        if (s_rm.commit(transactionID)){
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
                        s_rm.abort(transactionID);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            case 26:
                System.out.println("REP: Shutting down REP");
                s_rm.shutdown();
                break;
            case 27: //update deleted customer ==> delete customer
                if (arguments.size() != 4) { //command was deletecustomer
                    wrongNumber();
                    break;
                }
                //restore the rm data when deleting a customer
                else {
                    try {
                        id = getInt(arguments.elementAt(1));
                        String key = getString(arguments.elementAt(2));
                        int count = getInt(arguments.elementAt(3));
                        s_rm.updateDeleteCustomer(id, key, count);
                        System.out.println("REP: Restore RM database from deleting customer");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;

            case 28:
                if (arguments.size() != 4) { //command was for reservations
                    wrongNumber();
                    break;
                }
                else {
                    //reserve or unreserve
                    try {
                        id = getInt(arguments.elementAt(1));
                        String key = getString(arguments.elementAt(2));
                        int resOrUnres = getInt(arguments.elementAt(3));
                        s_rm.updateItemInfo(id, key, resOrUnres);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            default:
                System.out.println("REP: The interface does not support this command.");
                break;
        }

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
        else if (argument.compareToIgnoreCase("updatedeletecustomer") == 0)
            return 27;
        else if (argument.compareToIgnoreCase("updateItemInfo") == 0)
            return 28;
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



    public void addCommand(String command) {

        synchronized (state) {
            state.add(command);
        }
    }
}
