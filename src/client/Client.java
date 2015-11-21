package client;


import middleware.TimeToLive;
import server.Trace;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.io.*;



public class Client {

    WSClient client = null;
    WSClient backup = null;
    WSClient primary = null;

    public Client(String serviceName, String serviceHost, int servicePort)
            throws Exception {

        int hasbackup = 0;

        primary = new WSClient(serviceName, serviceHost, servicePort);
        if (hasbackup == 1) {
            backup = new WSClient("middlerep", serviceHost, 6667);
        }
        else {
            backup = primary;
        }
        //primary proxy for the client
        client = primary;
    }

    public static void main(String[] args) {
        try {
        
            if (args.length != 3) {
                System.out.println("Usage: MyClient <service-name> " 
                        + "<service-host> <service-port>");
                System.exit(-1);
            }
            
            String serviceName = args[0];
            String serviceHost = args[1];
            int servicePort = Integer.parseInt(args[2]);
            
            Client client = new Client(serviceName, serviceHost, servicePort);
            
            client.run();
            
        } catch(Exception e) {
            e.printStackTrace();
        }
    }


    public void run() {
    
        int id = -1;
        int flightNumber = -1;
        int flightPrice = -1;
        int numSeats = -1;
        boolean room;
        boolean car;
        int price = -1;
        int numRooms = -1;
        int numCars = -1;
        String location = null;

        String command = "";
        Vector arguments = new Vector();

        BufferedReader stdin = 
                new BufferedReader(new InputStreamReader(System.in));
        
        System.out.println("Client Interface");
        System.out.println("Type \"help\" for list of supported commands");

        while (true) {





            try {
                //read the next command
                command = stdin.readLine();
            }
            catch (IOException io) {
                System.out.println("Unable to read from standard in");
                System.exit(1);
            }
            //remove heading and trailing white space
            command = command.trim();
            arguments = parse(command);
            
            //decide which of the commands this was
            switch(findChoice((String) arguments.elementAt(0))) {

            case 1: //help section
                if (arguments.size() == 1)   //command was "help"
                    listCommands();
                else if (arguments.size() == 2)  //command was "help <commandname>"
                    listSpecific((String) arguments.elementAt(1));
                else  //wrong use of help command
                    System.out.println("Improper use of help command. Type help or help, <commandname>");
                break;
                
            case 2:  //new flight
                if (arguments.size() != 5) {
                    wrongNumber();
                    break;
                }
                System.out.println("Adding a new Flight using id: " + arguments.elementAt(1));
                System.out.println("Flight number: " + arguments.elementAt(2));
                System.out.println("Add Flight Seats: " + arguments.elementAt(3));
                System.out.println("Set Flight Price: " + arguments.elementAt(4));
                System.out.println("Waiting for response from server...");

                try {
                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));
                    numSeats = getInt(arguments.elementAt(3));
                    flightPrice = getInt(arguments.elementAt(4));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    if (id  >= 0 && flightNumber >=0 && numSeats>=0 && flightNumber >=0) {
                        if (client.proxy.addFlight(id, flightNumber, numSeats, flightPrice))
                            System.out.println("Flight added");
                        else
                            System.out.println("Flight could not be added");
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");

                    try {
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id  >= 0 && flightNumber >=0 && numSeats>=0 && flightNumber >=0) {
                            if (client.proxy.addFlight(id, flightNumber, numSeats, flightPrice))
                                System.out.println("Flight added");
                            else
                                System.out.println("Flight could not be added");
                        }
                    } catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                                if (id  >= 0 && flightNumber >=0 && numSeats>=0 && flightNumber >=0) {
                                    if (client.proxy.addFlight(id, flightNumber, numSeats, flightPrice))
                                        System.out.println("Flight added");
                                    else
                                        System.out.println("Flight could not be added");
                                }
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        } catch (client.DeadlockException_Exception e2) {
                            e2.printStackTrace();
                        }
                    } catch (client.DeadlockException_Exception e1) {
                        e1.printStackTrace();
                    }

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
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    if (id  >= 0 && location !=null && numCars>=0 && price >=0) {
                        if (client.proxy.addCars(id, location, numCars, price))
                            System.out.println("cars added");
                        else
                            System.out.println("cars could not be added");
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //conect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id  >= 0 && location !=null && numCars>=0 && price >=0) {
                            if (client.proxy.addCars(id, location, numCars, price))
                                System.out.println("cars added");
                            else
                                System.out.println("cars could not be added");
                        }
                    } catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                                if (id  >= 0 && location !=null && numCars>=0 && price >=0) {
                                    if (client.proxy.addCars(id, location, numCars, price))
                                        System.out.println("cars added");
                                    else
                                        System.out.println("cars could not be added");
                                }
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }

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
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    if (id >= 0 && location != null && numRooms >=0 && price >= 0) {
                        if (client.proxy.addRooms(id, location, numRooms, price))
                            System.out.println("rooms added");
                        else
                            System.out.println("rooms could not be added");
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //conect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && location != null && numRooms >=0 && price >= 0) {
                            if (client.proxy.addRooms(id, location, numRooms, price))
                                System.out.println("rooms added");
                            else
                                System.out.println("rooms could not be added");
                        }
                    } catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            //try to conect to its primary again
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                                if (id >= 0 && location != null && numRooms >=0 && price >= 0) {
                                    if (client.proxy.addRooms(id, location, numRooms, price))
                                        System.out.println("rooms added");
                                    else
                                        System.out.println("rooms could not be added");
                                }
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
                }
                break;
                
            case 5:  //new Customer
                if (arguments.size() != 2) {
                    wrongNumber();
                    break;
                }
                System.out.println("Adding a new Customer using id: " + arguments.elementAt(1));
                System.out.println("Waiting for response from server...");
                try {
                    id = getInt(arguments.elementAt(1));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    int customer = client.proxy.newCustomer(id);
                    if (customer >= 0) {
                        System.out.println("new customer id: " + customer);
                    }
                    else {
                        Trace.warn("Fail to generate a new customer");
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //conect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        int customer = client.proxy.newCustomer(id);
                        if (customer >= 0) {
                            System.out.println("new customer id: " + customer);
                        }
                        else {
                            Trace.warn("Fail to generate a new customer");
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            int customer = client.proxy.newCustomer(id);
                            if (customer >= 0) {
                                System.out.println("new customer id: " + customer);
                            }
                            else {
                                Trace.warn("Fail to generate a new customer");
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }

                    }
                }

                break;
                
            case 6: //delete Flight
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Deleting a flight using id: " + arguments.elementAt(1));
                System.out.println("Flight Number: " + arguments.elementAt(2));
                System.out.println("Waiting for response from the server...");

                try {
                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    if (id >= 0 && flightNumber >=0) {
                        if (client.proxy.deleteFlight(id, flightNumber))
                            System.out.println("Flight Deleted");
                        else
                            System.out.println("Flight could not be deleted");
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //conect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && flightNumber >=0) {
                            if (client.proxy.deleteFlight(id, flightNumber))
                                System.out.println("Flight Deleted");
                            else
                                System.out.println("Flight could not be deleted");
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && flightNumber >=0) {
                                if (client.proxy.deleteFlight(id, flightNumber))
                                    System.out.println("Flight Deleted");
                                else
                                    System.out.println("Flight could not be deleted");
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {

                    if (id >=0 && location != null) {
                        if (client.proxy.deleteCars(id, location))
                            System.out.println("cars Deleted");
                        else
                            System.out.println("cars could not be deleted");
                    }

                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //conect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >=0 && location != null) {
                            if (client.proxy.deleteCars(id, location))
                                System.out.println("cars Deleted");
                            else
                                System.out.println("cars could not be deleted");
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >=0 && location != null) {
                                if (client.proxy.deleteCars(id, location))
                                    System.out.println("cars Deleted");
                                else
                                    System.out.println("cars could not be deleted");
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    if (id >= 0 && location != null) {
                        if (client.proxy.deleteRooms(id, location))
                            System.out.println("rooms Deleted");
                        else
                            System.out.println("rooms could not be deleted");
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //conect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && location != null) {
                            if (client.proxy.deleteRooms(id, location))
                                System.out.println("rooms Deleted");
                            else
                                System.out.println("rooms could not be deleted");
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && location != null) {
                                if (client.proxy.deleteRooms(id, location))
                                    System.out.println("rooms Deleted");
                                else
                                    System.out.println("rooms could not be deleted");
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
                }
                break;
                
            case 9: //delete Customer
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Deleting a customer from the database using id: " + arguments.elementAt(1));
                System.out.println("Customer id: " + arguments.elementAt(2));
                System.out.println("Waiting for response from the server...");

                int customer = -1;
                try {
                    id = getInt(arguments.elementAt(1));
                    customer = getInt(arguments.elementAt(2));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    if (id >= 0 && customer >= 0) {
                        if (client.proxy.deleteCustomer(id, customer))
                            System.out.println("Customer Deleted");
                        else
                            System.out.println("Customer could not be deleted");
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //conect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && customer >= 0) {
                            if (client.proxy.deleteCustomer(id, customer))
                                System.out.println("Customer Deleted");
                            else
                                System.out.println("Customer could not be deleted");
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && customer >= 0) {
                                if (client.proxy.deleteCustomer(id, customer))
                                    System.out.println("Customer Deleted");
                                else
                                    System.out.println("Customer could not be deleted");
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }

                }
                break;
                
            case 10: //querying a flight
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying a flight using id: " + arguments.elementAt(1));
                System.out.println("Flight number: " + arguments.elementAt(2));
                System.out.println("Waiting for response from the server...");

                try {
                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    if (id >= 0 && flightNumber >= 0) {
                        int seats = client.proxy.queryFlight(id, flightNumber);
                        if (seats >= 0) {
                            System.out.println("Number of seats available: " + seats);
                        } else {
                            System.out.println("ERROR: Other process is locking on that flight# " + flightNumber
                                    + "or invalid flight");
                        }
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //conect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && flightNumber >= 0) {
                            int seats = client.proxy.queryFlight(id, flightNumber);
                            if (seats >= 0) {
                                System.out.println("Number of seats available: " + seats);
                            } else {
                                System.out.println("ERROR: Other process is locking on that flight# " + flightNumber
                                        + "or invalid flight");
                            }
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && flightNumber >= 0) {
                                int seats = client.proxy.queryFlight(id, flightNumber);
                                if (seats >= 0) {
                                    System.out.println("Number of seats available: " + seats);
                                } else {
                                    System.out.println("ERROR: Other process is locking on that flight# " + flightNumber
                                            + "or invalid flight");
                                }
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    if (id >= 0 && location != null) {
                        numCars = client.proxy.queryCars(id, location);
                        if (numCars >= 0) {
                            System.out.println("number of cars at this location: " + numCars);
                        } else {
                            System.out.println("ERROR: some other process might lock on that car location " + location);
                        }
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //conect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && location != null) {
                            numCars = client.proxy.queryCars(id, location);
                            if (numCars >= 0) {
                                System.out.println("number of cars at this location: " + numCars);
                            } else {
                                System.out.println("ERROR: some other process might lock on that car location " + location);
                            }
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && location != null) {
                                numCars = client.proxy.queryCars(id, location);
                                if (numCars >= 0) {
                                    System.out.println("number of cars at this location: " + numCars);
                                } else {
                                    System.out.println("ERROR: some other process might lock on that car location " + location);
                                }
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {

                    if (id >=0 && numRooms >= 0) {
                        numRooms = client.proxy.queryRooms(id, location);
                        if (numRooms >= 0) {
                            System.out.println("number of rooms at this location: " + numRooms);
                        } else {
                            System.out.println("ERROR: Some other process is locking on the room location " + location);
                        }
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //conect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >=0 && numRooms >= 0) {
                            numRooms = client.proxy.queryRooms(id, location);
                            if (numRooms >= 0) {
                                System.out.println("number of rooms at this location: " + numRooms);
                            } else {
                                System.out.println("ERROR: Some other process is locking on the room location " + location);
                            }
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >=0 && numRooms >= 0) {
                                numRooms = client.proxy.queryRooms(id, location);
                                if (numRooms >= 0) {
                                    System.out.println("number of rooms at this location: " + numRooms);
                                } else {
                                    System.out.println("ERROR: Some other process is locking on the room location " + location);
                                }
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
                }
                break;
                
            case 13: //querying Customer Information
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying Customer information using id: " + arguments.elementAt(1));
                System.out.println("Customer id: " + arguments.elementAt(2));
                System.out.println("Waiting for response from the server...");

                customer = -1;
                try {
                    id = getInt(arguments.elementAt(1));
                    customer = getInt(arguments.elementAt(2));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {

                    if (id >= 0 && customer >= 0) {
                        String bill = client.proxy.queryCustomerInfo(id, customer);
                        if (!bill.isEmpty()) {
                            System.out.println("Customer info: \n" + bill);
                        } else {
                            System.out.println("ERROR: no such customer");
                        }
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //conect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && customer >= 0) {
                            String bill = client.proxy.queryCustomerInfo(id, customer);
                            if (!bill.isEmpty()) {
                                System.out.println("Customer info: \n" + bill);
                            } else {
                                System.out.println("ERROR: no such customer");
                            }
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && customer >= 0) {
                                String bill = client.proxy.queryCustomerInfo(id, customer);
                                if (!bill.isEmpty()) {
                                    System.out.println("Customer info: \n" + bill);
                                } else {
                                    System.out.println("ERROR: no such customer");
                                }
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
                }
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    if (id >= 0 && flightNumber >= 0) {
                        price = client.proxy.queryFlightPrice(id, flightNumber);
                        if (price >= 0) {
                            System.out.println("Price of a seat: " + price);
                        } else {
                            System.out.println("ERROR: other process is locking on flight# " + flightNumber);
                        }
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //conect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && flightNumber >= 0) {
                            price = client.proxy.queryFlightPrice(id, flightNumber);
                            if (price >= 0) {
                                System.out.println("Price of a seat: " + price);
                            } else {
                                System.out.println("ERROR: other process is locking on flight# " + flightNumber);
                            }
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && flightNumber >= 0) {
                                price = client.proxy.queryFlightPrice(id, flightNumber);
                                if (price >= 0) {
                                    System.out.println("Price of a seat: " + price);
                                } else {
                                    System.out.println("ERROR: other process is locking on flight# " + flightNumber);
                                }
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
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
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {

                    if (id >= 0 && location != null) {
                        price = client.proxy.queryCarsPrice(id, location);
                        if (price >= 0) {
                            System.out.println("Price of a car at this location: " + price);
                        } else {
                            System.out.println("ERROR: other process is locking on car location: " + location);
                        }
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //connect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && location != null) {
                            price = client.proxy.queryCarsPrice(id, location);
                            if (price >= 0) {
                                System.out.println("Price of a car at this location: " + price);
                            } else {
                                System.out.println("ERROR: other process is locking on car location: " + location);
                            }
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && location != null) {
                                price = client.proxy.queryCarsPrice(id, location);
                                if (price >= 0) {
                                    System.out.println("Price of a car at this location: " + price);
                                } else {
                                    System.out.println("ERROR: other process is locking on car location: " + location);
                                }
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    if (id >= 0 && location != null) {
                        price = client.proxy.queryRoomsPrice(id, location);
                        if (price >= 0) {
                            System.out.println("Price of rooms at this location: " + price);
                        } else {
                            System.out.println("ERROR: other process is locking on room location: " + location);
                        }
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //connect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && location != null) {
                            price = client.proxy.queryRoomsPrice(id, location);
                            if (price >= 0) {
                                System.out.println("Price of rooms at this location: " + price);
                            } else {
                                System.out.println("ERROR: other process is locking on room location: " + location);
                            }
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && location != null) {
                                price = client.proxy.queryRoomsPrice(id, location);
                                if (price >= 0) {
                                    System.out.println("Price of rooms at this location: " + price);
                                } else {
                                    System.out.println("ERROR: other process is locking on room location: " + location);
                                }
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
                }
                break;
                
            case 17:  //reserve a flight
                if (arguments.size() != 4) {
                    wrongNumber();
                    break;
                }
                System.out.println("Reserving a seat on a flight using id: " + arguments.elementAt(1));
                System.out.println("Customer id: " + arguments.elementAt(2));
                System.out.println("Flight number: " + arguments.elementAt(3));
                System.out.println("Waiting a response from the server...");
                customer = -1;
                try {
                    id = getInt(arguments.elementAt(1));
                    customer = getInt(arguments.elementAt(2));
                    flightNumber = getInt(arguments.elementAt(3));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    if (id >= 0 && customer >= 0 ) {
                        if (client.proxy.reserveFlight(id, customer, flightNumber))
                            System.out.println("Flight Reserved");
                        else
                            System.out.println("Flight could not be reserved.");
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //connect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && customer >= 0 ) {
                            if (client.proxy.reserveFlight(id, customer, flightNumber))
                                System.out.println("Flight Reserved");
                            else
                                System.out.println("Flight could not be reserved.");
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && customer >= 0 ) {
                                if (client.proxy.reserveFlight(id, customer, flightNumber))
                                    System.out.println("Flight Reserved");
                                else
                                    System.out.println("Flight could not be reserved.");
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
                }
                break;
                
            case 18:  //reserve a car
                if (arguments.size() != 4) {
                    wrongNumber();
                    break;
                }
                System.out.println("Reserving a car at a location using id: " + arguments.elementAt(1));
                System.out.println("Customer id: " + arguments.elementAt(2));
                System.out.println("Location: " + arguments.elementAt(3));
                System.out.println("Waiting a response from the server...");

                customer = -1;
                try {
                    id = getInt(arguments.elementAt(1));
                    customer = getInt(arguments.elementAt(2));
                    location = getString(arguments.elementAt(3));
                } catch (Exception e) {
                    e.printStackTrace();
                }


                try {
                    if (id >= 0 && customer >= 0 && location != null) {
                        if (client.proxy.reserveCar(id, customer, location))
                            System.out.println("car Reserved");
                        else
                            System.out.println("car could not be reserved.");
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //connect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && customer >= 0 && location != null) {
                            if (client.proxy.reserveCar(id, customer, location))
                                System.out.println("car Reserved");
                            else
                                System.out.println("car could not be reserved.");
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && customer >= 0 && location != null) {
                                if (client.proxy.reserveCar(id, customer, location))
                                    System.out.println("car Reserved");
                                else
                                    System.out.println("car could not be reserved.");
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
                }
                break;
                
            case 19:  //reserve a room
                if (arguments.size() != 4) {
                    wrongNumber();
                    break;
                }
                System.out.println("Reserving a room at a location using id: " + arguments.elementAt(1));
                System.out.println("Customer id: " + arguments.elementAt(2));
                System.out.println("Location: " + arguments.elementAt(3));
                System.out.println("Waiting a response from the server... ");


                customer = -1;
                try {
                    id = getInt(arguments.elementAt(1));
                    customer = getInt(arguments.elementAt(2));
                    location = getString(arguments.elementAt(3));
                } catch (Exception e) {
                    e.printStackTrace();
                }


                try {
                    if (id >= 0 && customer >= 0 && location != null) {
                        if (client.proxy.reserveRoom(id, customer, location))
                            System.out.println("room Reserved");
                        else
                            System.out.println("room could not be reserved.");
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //connect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && customer >= 0 && location != null) {
                            if (client.proxy.reserveRoom(id, customer, location))
                                System.out.println("room Reserved");
                            else
                                System.out.println("room could not be reserved.");
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && customer >= 0 && location != null) {
                                if (client.proxy.reserveRoom(id, customer, location))
                                    System.out.println("room Reserved");
                                else
                                    System.out.println("room could not be reserved.");
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
                }
                break;
                
            case 20:  //reserve an Itinerary
                if (arguments.size()<7) {
                    wrongNumber();
                    break;
                }
                System.out.println("Reserving an Itinerary using id: " + arguments.elementAt(1));
                System.out.println("Customer id: " + arguments.elementAt(2));
                for (int i = 0; i<arguments.size()-6; i++)
                    System.out.println("Flight number" + arguments.elementAt(3 + i));
                System.out.println("Location for car/room booking: " + arguments.elementAt(arguments.size()-3));
                System.out.println("car to book?: " + arguments.elementAt(arguments.size()-2));
                System.out.println("room to book?: " + arguments.elementAt(arguments.size()-1));

                boolean carBool = false;
                boolean roomBool = false;
                boolean reserveFlag = true;
                customer = -1;
                try {
                    id = getInt(arguments.elementAt(1));
                    customer = getInt(arguments.elementAt(2));
                    location = getString(arguments.elementAt(arguments.size()-3));
                    car = getBoolean(arguments.elementAt(arguments.size()-2));
                    carBool = car;
                    room = getBoolean(arguments.elementAt(arguments.size()-1));
                    roomBool = room;

                } catch (Exception e) {
                    reserveFlag = false;
                    e.printStackTrace();
                }
                Vector flightNumbers = new Vector();
                try {
                    for (int i = 0; i < arguments.size()-6; i++)
                        flightNumbers.addElement(arguments.elementAt(3 + i));
                    if (id >= 0 && customer >=0 && location != null && carBool && roomBool && reserveFlag) {
                        if (client.proxy.reserveItinerary(id, customer, flightNumbers, location, carBool, roomBool))
                            System.out.println("Itinerary Reserved");
                        else
                            System.out.println("Itinerary could not be reserved.");
                    }
                    else {
                        System.out.println("Itinerary could not be reserved: Invalid command");
                    }

                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //connect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && customer >=0 && location != null && carBool && roomBool && reserveFlag) {
                            if (client.proxy.reserveItinerary(id, customer, flightNumbers, location, carBool, roomBool))
                                System.out.println("Itinerary Reserved");
                            else
                                System.out.println("Itinerary could not be reserved.");
                        }
                        else {
                            System.out.println("Itinerary could not be reserved: Invalid command");
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && customer >=0 && location != null && carBool && roomBool && reserveFlag) {
                                if (client.proxy.reserveItinerary(id, customer, flightNumbers, location, carBool, roomBool))
                                    System.out.println("Itinerary Reserved");
                                else
                                    System.out.println("Itinerary could not be reserved.");
                            }
                            else {
                                System.out.println("Itinerary could not be reserved: Invalid command");
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
                }
                break;

            case 21:  //quit the client
                if (arguments.size() != 1) {
                    wrongNumber();
                    break;
                }
                System.out.println("Quitting client.");
                return;
                
            case 22:  //new Customer given id
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Adding a new Customer using id: "
                        + arguments.elementAt(1)  +  " and cid "  + arguments.elementAt(2));
                System.out.println("Waiting for response from server...");

                customer = -1;
                try {
                    id = getInt(arguments.elementAt(1));
                    customer = getInt(arguments.elementAt(2));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    if (id >= 0 && customer >= 0) {
                        if (client.proxy.newCustomerId(id, customer)) {
                            System.out.println("new customer id: " + customer);
                        } else {
                            Trace.warn("Customer already exists");
                        }
                    }
                }
                catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //connect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (id >= 0 && customer >= 0) {
                            if (client.proxy.newCustomerId(id, customer)) {
                                System.out.println("new customer id: " + customer);
                            } else {
                                Trace.warn("Customer already exists");
                            }
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (id >= 0 && customer >= 0) {
                                if (client.proxy.newCustomerId(id, customer)) {
                                    System.out.println("new customer id: " + customer);
                                } else {
                                    Trace.warn("Customer already exists");
                                }
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
                }
                break;

            case 23:

                try {
                    if (arguments.size() == 1) { //command was "start"
                        int transId = client.proxy.start();
                        System.out.println("TRANSACTION ID: " + transId);
                    } else {//wrong use of start command
                        System.out.println("Improper use of help command. Type help or help, <commandname>");
                    }
                }catch(Exception e) {
                    System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                    try {
                        //connect to its backup
                        if (getStatusCode(backup.wsdlLocation) == 200) {
                            client = backup;
                        }
                        System.out.println("connected to its replica");
                        if (arguments.size() == 1) { //command was "start"
                            int transId = client.proxy.start();
                            System.out.println("TRANSACTION ID: " + transId);
                        } else {//wrong use of start command
                            System.out.println("Improper use of help command. Type help or help, <commandname>");
                        }
                    }catch (IOException e1) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                        try {
                            if (getStatusCode(primary.wsdlLocation) == 200) {
                                client = primary;
                            }
                            System.out.println("connected to its replica2");
                            if (arguments.size() == 1) { //command was "start"
                                int transId = client.proxy.start();
                                System.out.println("TRANSACTION ID: " + transId);
                            } else {//wrong use of start command
                                System.out.println("Improper use of help command. Type help or help, <commandname>");
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            return;
                        }
                    }
                }
                break;

            case 24:
                if (arguments.size() != 2) { //command was "commit"
                    wrongNumber();
                    break;
                }
                else {

                    int transactionID = 0;
                    try {
                        transactionID = getInt(arguments.elementAt(1));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    try {
                        if (transactionID > 0) {
                            if (client.proxy.commit(transactionID)) {
                                Trace.info("Commit transaction " + transactionID + " successfully");

                            } else {
                                Trace.warn("Invalid transaction ID or failed to unlock");
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                        try {
                            //connect to its backup
                            if (getStatusCode(backup.wsdlLocation) == 200) {
                                client = backup;
                            }
                            System.out.println("connected to its replica");
                            if (transactionID > 0) {
                                if (client.proxy.commit(transactionID)) {
                                    Trace.info("Commit transaction " + transactionID + " successfully");

                                } else {
                                    Trace.warn("Invalid transaction ID or failed to unlock");
                                }
                            }
                        }catch (IOException e1) {
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                            try {
                                if (getStatusCode(primary.wsdlLocation) == 200) {
                                    client = primary;
                                }
                                System.out.println("connected to its replica2");
                                if (transactionID > 0) {
                                    if (client.proxy.commit(transactionID)) {
                                        Trace.info("Commit transaction " + transactionID + " successfully");
                                    } else {
                                        Trace.warn("Invalid transaction ID or failed to unlock");
                                    }
                                }
                            } catch (IOException e2) {
                                e2.printStackTrace();
                                return;
                            }
                        }
                    }
                }
                break;

                case 25:
                    if (arguments.size() != 2) { //command was "abort"
                        wrongNumber();
                        break;
                    }
                    else {

                        int transactionID = 0;
                        try {
                            transactionID = getInt(arguments.elementAt(1));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            if (transactionID >0) {
                                if (client.proxy.abort(transactionID)) {

                                    Trace.info("Abort transaction " + transactionID + " successfully");
                                } else {
                                    Trace.warn("Invalid transaction ID or failed to unlock");
                                }
                            }
                            else
                                System.out.println("Invalid Transaction ID :" + transactionID);
                        } catch (Exception e) {
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                            try {
                                //connect to its backup
                                if (getStatusCode(backup.wsdlLocation) == 200) {
                                    client = backup;
                                }
                                System.out.println("connected to its replica");
                                if (transactionID >0) {
                                    if (client.proxy.abort(transactionID)) {

                                        Trace.info("Abort transaction " + transactionID + " successfully");
                                    } else {
                                        Trace.warn("Invalid transaction ID or failed to unlock");
                                    }
                                }
                                else
                                    System.out.println("Invalid Transaction ID :" + transactionID);
                            }catch (IOException e1) {
                                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                                try {
                                    if (getStatusCode(primary.wsdlLocation) == 200) {
                                        client = primary;
                                    }
                                    System.out.println("connected to its replica2");
                                    if (transactionID >0) {
                                        if (client.proxy.abort(transactionID)) {

                                            Trace.info("Abort transaction " + transactionID + " successfully");
                                        } else {
                                            Trace.warn("Invalid transaction ID or failed to unlock");
                                        }
                                    }
                                    else
                                        System.out.println("Invalid Transaction ID :" + transactionID);
                                } catch (IOException e2) {
                                    e2.printStackTrace();
                                    return;
                                }
                            }
                        }
                    }

                    break;
                case 26:
                    if (arguments.size() == 1) { //command was "shutdown"
                        try{
                            if(!client.proxy.shutdown()){
                                System.out.println("CANT SHUTDOWN; SOME TRANSACTIONS ARE STILL ACTIVE");
                            }
                        }catch (Exception e){
                            System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica");
                            try {
                                //connect to its backup
                                if (getStatusCode(backup.wsdlLocation) == 200) {
                                    client = backup;
                                }
                                System.out.println("connected to its replica");
                                if(!client.proxy.shutdown()){
                                    System.out.println("CANT SHUTDOWN; SOME TRANSACTIONS ARE STILL ACTIVE");
                                }
                            }catch (IOException e1) {
                                System.out.println("EXCEPTION: fail to connect to PC, connecting to its replica2");
                                try {
                                    if (getStatusCode(primary.wsdlLocation) == 200) {
                                        client = primary;
                                    }
                                    System.out.println("connected to its replica2");
                                    if(!client.proxy.shutdown()){
                                        System.out.println("CANT SHUTDOWN; SOME TRANSACTIONS ARE STILL ACTIVE");
                                    }
                                } catch (IOException e2) {
                                    e2.printStackTrace();
                                    return;
                                }
                            }
                        }
                    }

                    else  //wrong use of shutdown command
                        System.out.println("Improper use of shutdown command. Type help or help, <commandname>");
                    break;

            default:
                System.out.println("The interface does not support this command.");
                break;
            }
        }
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

    public void listCommands() {
        System.out.println("\nWelcome to the client interface provided to test your project.");
        System.out.println("Commands accepted by the interface are: ");
        System.out.println("help");
        System.out.println("newflight\nnewcar\nnewroom\nnewcustomer\nnewcustomerid\ndeleteflight\ndeletecar\ndeleteroom");
        System.out.println("deletecustomer\nqueryflight\nquerycar\nqueryroom\nquerycustomer");
        System.out.println("queryflightprice\nquerycarprice\nqueryroomprice");
        System.out.println("reserveflight\nreservecar\nreserveroom\nitinerary");
        System.out.println("start\ncommit\nabort\nshutdown");
        System.out.println("quit");
        System.out.println("\ntype help, <commandname> for detailed info (note the use of comma).");
    }


    public void listSpecific(String command) {
        System.out.print("Help on: ");
        switch(findChoice(command)) {
            case 1:
            System.out.println("Help");
            System.out.println("\nTyping help on the prompt gives a list of all the commands available.");
            System.out.println("Typing help, <commandname> gives details on how to use the particular command.");
            break;

            case 2:  //new flight
            System.out.println("Adding a new Flight.");
            System.out.println("Purpose: ");
            System.out.println("\tAdd information about a new flight.");
            System.out.println("\nUsage: ");
            System.out.println("\tnewflight, <id>, <flightnumber>, <numSeats>, <flightprice>");
            break;
            
            case 3:  //new car
            System.out.println("Adding a new car.");
            System.out.println("Purpose: ");
            System.out.println("\tAdd information about a new car location.");
            System.out.println("\nUsage: ");
            System.out.println("\tnewcar, <id>, <location>, <numberofcars>, <pricepercar>");
            break;
            
            case 4:  //new room
            System.out.println("Adding a new room.");
            System.out.println("Purpose: ");
            System.out.println("\tAdd information about a new room location.");
            System.out.println("\nUsage: ");
            System.out.println("\tnewroom, <id>, <location>, <numberofrooms>, <priceperroom>");
            break;
            
            case 5:  //new Customer
            System.out.println("Adding a new Customer.");
            System.out.println("Purpose: ");
            System.out.println("\tGet the system to provide a new customer id. (same as adding a new customer)");
            System.out.println("\nUsage: ");
            System.out.println("\tnewcustomer, <id>");
            break;
            
            
            case 6: //delete Flight
            System.out.println("Deleting a flight");
            System.out.println("Purpose: ");
            System.out.println("\tDelete a flight's information.");
            System.out.println("\nUsage: ");
            System.out.println("\tdeleteflight, <id>, <flightnumber>");
            break;
            
            case 7: //delete car
            System.out.println("Deleting a car");
            System.out.println("Purpose: ");
            System.out.println("\tDelete all cars from a location.");
            System.out.println("\nUsage: ");
            System.out.println("\tdeletecar, <id>, <location>, <numCars>");
            break;
            
            case 8: //delete room
            System.out.println("Deleting a room");
            System.out.println("\nPurpose: ");
            System.out.println("\tDelete all rooms from a location.");
            System.out.println("Usage: ");
            System.out.println("\tdeleteroom, <id>, <location>, <numRooms>");
            break;
            
            case 9: //delete Customer
            System.out.println("Deleting a Customer");
            System.out.println("Purpose: ");
            System.out.println("\tRemove a customer from the database.");
            System.out.println("\nUsage: ");
            System.out.println("\tdeletecustomer, <id>, <customerid>");
            break;
            
            case 10: //querying a flight
            System.out.println("Querying flight.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain Seat information about a certain flight.");
            System.out.println("\nUsage: ");
            System.out.println("\tqueryflight, <id>, <flightnumber>");
            break;
            
            case 11: //querying a car Location
            System.out.println("Querying a car location.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain number of cars at a certain car location.");
            System.out.println("\nUsage: ");
            System.out.println("\tquerycar, <id>, <location>");        
            break;
            
            case 12: //querying a room location
            System.out.println("Querying a room Location.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain number of rooms at a certain room location.");
            System.out.println("\nUsage: ");
            System.out.println("\tqueryroom, <id>, <location>");        
            break;
            
            case 13: //querying Customer Information
            System.out.println("Querying Customer Information.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain information about a customer.");
            System.out.println("\nUsage: ");
            System.out.println("\tquerycustomer, <id>, <customerid>");
            break;               
            
            case 14: //querying a flight for price 
            System.out.println("Querying flight.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain price information about a certain flight.");
            System.out.println("\nUsage: ");
            System.out.println("\tqueryflightprice, <id>, <flightnumber>");
            break;
            
            case 15: //querying a car Location for price
            System.out.println("Querying a car location.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain price information about a certain car location.");
            System.out.println("\nUsage: ");
            System.out.println("\tquerycarprice, <id>, <location>");        
            break;
            
            case 16: //querying a room location for price
            System.out.println("Querying a room Location.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain price information about a certain room location.");
            System.out.println("\nUsage: ");
            System.out.println("\tqueryroomprice, <id>, <location>");        
            break;

            case 17:  //reserve a flight
            System.out.println("Reserving a flight.");
            System.out.println("Purpose: ");
            System.out.println("\tReserve a flight for a customer.");
            System.out.println("\nUsage: ");
            System.out.println("\treserveflight, <id>, <customerid>, <flightnumber>");
            break;
            
            case 18:  //reserve a car
            System.out.println("Reserving a car.");
            System.out.println("Purpose: ");
            System.out.println("\tReserve a given number of cars for a customer at a particular location.");
            System.out.println("\nUsage: ");
            System.out.println("\treservecar, <id>, <customerid>, <location>, <nummberofcars>");
            break;
            
            case 19:  //reserve a room
            System.out.println("Reserving a room.");
            System.out.println("Purpose: ");
            System.out.println("\tReserve a given number of rooms for a customer at a particular location.");
            System.out.println("\nUsage: ");
            System.out.println("\treserveroom, <id>, <customerid>, <location>, <nummberofrooms>");
            break;
            
            case 20:  //reserve an Itinerary
            System.out.println("Reserving an Itinerary.");
            System.out.println("Purpose: ");
            System.out.println("\tBook one or more flights.Also book zero or more cars/rooms at a location.");
            System.out.println("\nUsage: ");
            System.out.println("\titinerary, <id>, <customerid>, "
                    + "<flightnumber1>....<flightnumberN>, "
                    + "<LocationToBookcarsOrrooms>, <NumberOfcars>, <NumberOfroom>");
            break;
            

            case 21:  //quit the client
            System.out.println("Quitting client.");
            System.out.println("Purpose: ");
            System.out.println("\tExit the client application.");
            System.out.println("\nUsage: ");
            System.out.println("\tquit");
            break;
            
            case 22:  //new customer with id
            System.out.println("Create new customer providing an id");
            System.out.println("Purpose: ");
            System.out.println("\tCreates a new customer with the id provided");
            System.out.println("\nUsage: ");
            System.out.println("\tnewcustomerid, <id>, <customerid>");
            break;

            case 23:
                System.out.println("start");
                System.out.println("\nTyping start generates a unique transaction ID for the client.");
                System.out.println("Starts a new transaction for the client");
                break;
            case 24:
                System.out.println("commit");
                System.out.println("\nTyping commit unlocks all the locks related to a transaction.");
                System.out.println("\nUsage: ");
                System.out.println("\tcommit, <id>");
                break;
            case 25:
                System.out.println("abort");
                System.out.println("Aborts transaction");
                break;
            case 26:
                System.out.println("shutdown");
                System.out.println("graceful shutdown");

            default:
            System.out.println(command);
            System.out.println("The interface does not support this command.");
            break;
        }
    }

    public int getStatusCode(URL url) throws IOException {

        HttpURLConnection http = (HttpURLConnection)url.openConnection();
        http.connect();
        int statusCode = http.getResponseCode();
        return statusCode;
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
    
}
