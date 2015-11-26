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


    ResourceManagerImpl s_rm = null;

    final List<String> state = new LinkedList<>();



    public Broadcast(String configFile, ResourceManagerImpl resourceManager) {
        System.setProperty("java.net.preferIPv4Stack" , "true");

        this.configFile = configFile;
        this.s_rm = resourceManager;

    }

    public void receive(Message msg) {


        Vector arguments = new Vector();

        String line = msg.getObject().toString();


        line = line.trim();
        arguments = parse(line);

        String command = null;

        try {
            command = getString(arguments.elementAt(0));

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Command :" +line);
        synchronized(state) {
            state.add(line);
        }

        if (!PCBit.get(0)) {
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
            Vector arg = new Vector();
            try {

                int id = getInt(arguments.elementAt(1));

                synchronized(state) {
                    for (int i = state.size()-1; i>=0; i--) {
                        String c = state.get(i);
                        arg = parse(c);
                        if (getInt(arg.elementAt(1)) == id) {
                            state.remove(c);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
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


    /*
    public void getState(OutputStream output) throws Exception {
        //System.out.println("\n\n\nSENDING THE STATE!!!!\n\n\n");

        synchronized(tempPacket) {
            FileOutputStream fileOut = new FileOutputStream(history);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(tempPacket);
            Util.objectToStream(history, out);
        }
    }

    public void setState(InputStream input) {
        DataPacket list = null;
        try {
            input = new FileInputStream(history);
            ObjectInputStream d = new ObjectInputStream(input);
            list = (DataPacket) d.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
        synchronized (tempTable) {
            tempTable.clear();
            tempTable.putAll(list.mm_itemHT);
        }
        synchronized (tempLock) {
            tempLock = list.m_lock;
        }
    }
*/

    private void multicast() {

        while(true) {
            try {
                if(bit.get(0) && !bit.get(1)) {

                    System.out.flush();
                    String line = state.get(state.size()-1);
                    System.out.println("SENDING THE PACKET");
                    Message msg = new Message(null, null, line);
                    channel.send(msg);
                    //turn off after sending the message
                    bit.flip(0);
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
                    Trace.info("REP: Number of seats for flight " + flightNumber + ": " + seats);
                } catch (Exception e) {
                    System.out.println("REP: EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }

                break;
            /*
            case 13: //query customer

                try {


                    id = getInt(arguments.elementAt(1));
                    int customer = getInt(arguments.elementAt(2));
                    m_rm.queryCustomerInfo(id, customer);
                    m_rm.ttl[id-1].pushItem(id);
                    m_rm.txnManager.enlist(id, m_rm.CUST);

                    m_rm.ttl[id-1].pushItem(id);
                }
                catch(Exception e) {
                    System.out.println("REP: EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }

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

                    price = m_rm.queryFlightPrice(id, flightNumber);


                    location = String.valueOf(flightNumber);

                    //directly write to the customer database
                    if (reserveItem(id, customerId, location, key, price, m_rm.FLIGHT, m_rm.UNRES)) {
                        System.out.println("REP: flight number " + flightNumber + " is reserved");
                    }
                    else {
                        System.out.println("REP: flight number " + flightNumber + " is not reserved");
                    }

                    //made this a universial method --> see below:
                    // Read customer object if it exists
                    //Customer cust = (Customer) m_rm.readData(id, Customer.getKey(customerId));
                    //if (cust != null) {
                    //customer reserves it
                    //    cust.reserve(key, location, price, itemInfo, id); //change location maybe
                    //    m_rm.writeData(id, cust.getKey(), cust);

                    //    Vector cmd = cmdToVect(m_rm.CUST,m_rm.UNRES,customerId);
                    //    cmd.add(Integer.parseInt(location));
                    //    cmd.add(key);
                    //    cmd.add(itemInfo);
                    //    m_rm.txnManager.setNewUpdateItem(id,cmd);
                    // }

                    //start ttl
                    m_rm.ttl[id-1].pushItem(id);


                }
                catch(Exception e) {
                    System.out.println("REP: EXCEPTION: ");
                    System.out.println(e.getMessage());
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


                try {
                    id = getInt(arguments.elementAt(1));
                    int customer = getInt(arguments.elementAt(2));
                    if (id >= 0 && customer >= 0) {
                        if (m_rm.newCustomerId(id, customer)) {
                            System.out.println("REP: new customer id: " + customer);
                        } else {
                            System.out.println("REP: Customer already exists");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
            */
            default:
                System.out.println("The interface does not support this command.");
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
