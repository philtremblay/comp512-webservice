package middleware;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
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
    //private File history = new File("MiddleHist.ser");
    public BitSet bit = new BitSet(2);

    public BitSet PCBit = new BitSet(1);

    private RMHashtable mm_itemHT = null;
    private Hashtable<Integer, Vector> m_activeRM =null;
    private Hashtable<Integer, Stack> m_cmdList = null;

    final List<String> stateCommand=new LinkedList<String>();

    /**Constructor class **/
    public MidBroadcast(String configFile, ResourceManagerImpl resourceManager) {
        System.setProperty("java.net.preferIPv4Stack" , "true");

        this.m_rm = resourceManager;
    }

    //private TimeToLive[] m_ttl = null;
    

/*
    private void startTimer() {

        int i = 0;
        while (i < m_ttl.length) {

            if (m_ttl[i] != null) {
                Thread t = new Thread(m_ttl[i]);
                t.start();
            }
            i++;
        }


    }
*/

    public void addCommand(String command) {

        synchronized (stateCommand) {
            stateCommand.add(command);
        }
    }

    public void receive(Message msg) {
        System.out.println("RECEIVER SIDE!!!!");

        String line = msg.getObject().toString();
        //System.out.println(line);
        synchronized(stateCommand) {
            stateCommand.add(line);
        }
        String command = stateCommand.get(stateCommand.size() - 1);
        //System.out.println("\n\n\n\n\n");
        //System.out.println("COMMAND RECEIVED IS: " + command);
        //System.out.println("\n\n\n\n\n");

        //if its not PC, then it must be replica
        //Thus, execute the command
        if (!PCBit.get(0)) {
            executeCommand(command);
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

        synchronized(tempTable) {
            FileOutputStream fileOut = new FileOutputStream(history);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(tempTable);
            Util.objectToStream(history, out);
        }
    }

    public void setState(InputStream input) {
        server.RMHashtable list = null;
        try {
            input = new FileInputStream(history);
            ObjectInputStream d = new ObjectInputStream(input);
            list = (server.RMHashtable) d.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }

        synchronized(tempTable) {
            tempTable.clear();
            tempTable.putAll(list);
        }
        //System.out.println("\n\n\n\nreceived state (messages in chat history):\n\n\n\n");

    }

*/
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
        String serverConfig = workingDir+"/conf/middleudp.xml";
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
                    numSeats = getInt(arguments.elementAt(3));
                    flightPrice = getInt(arguments.elementAt(4));
                    status = getBoolean(arguments.elementAt(5));

                    if (status) {
                        //add to the transaction manager
                        Vector cmd = cmdToVect(m_rm.FLIGHT, m_rm.DEL, flightNumber);
                        this.m_rm.txnManager.setNewUpdateItem(id, cmd);
                        //set active RM list
                        this.m_rm.txnManager.enlist(id, m_rm.FLIGHT);

                        System.out.println("REP: Flight is added in the replica");

                    }
                    else {
                        System.out.println("REP: Flight is not added in the replica");
                    }

                    //start ttl
                    m_rm.ttl[id-1].pushItem(id);

                }catch(Exception e) {
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
                    int seats = m_rm.queryFlight(id, flightNumber);
                    Trace.info("REP: Number of seats for flight "+flightNumber+ ": "+ seats);
                }
                catch(Exception e) {
                    System.out.println("REP: EXCEPTION: ");
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }

                break;
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

                /**Dont need to start TTL right here because it will start once call the newcustomerid()**/
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

            default:
                System.out.println("The interface does not support this command.");
                break;

        }

    }//end of execute function

    /**This method writes the reservations to the customer database and add the record to transaction manager **/
    private boolean reserveItem(int id, int customerId, String location, String key, int price, int itemInfo, int querytype) {
        // Read customer object if it exists
        boolean reserved = false;
        Customer cust = (Customer) m_rm.readData(id, Customer.getKey(customerId));
        if (cust != null) {
            //customer reserves it
            cust.reserve(key, location, price, itemInfo, id); //change location maybe
            m_rm.writeData(id, cust.getKey(), cust);

            Vector cmd = cmdToVect(m_rm.CUST,m_rm.UNRES,customerId);
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
