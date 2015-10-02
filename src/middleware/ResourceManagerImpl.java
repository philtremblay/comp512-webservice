package middleware;

import client.Client;
import client.WSClient;

import javax.jws.WebService;
import java.net.MalformedURLException;
import java.util.Vector;

/**
 * Created by Phil Tremblay on 2015-10-01.
 */

@WebService(endpointInterface = "server.ws.ResourceManager",targetNamespace = "http://server")
public class ResourceManagerImpl implements server.ws.ResourceManager{

//    Client flightProxy = new Client("server","localhost",8081);
//    ResourceManagerImpl carProxy;
//    ResourceManagerImpl roomProxy;

    @Override
    public boolean addFlight(int id, int flightNumber, int numSeats, int flightPrice) {
        return false;
}

    @Override
    public boolean deleteFlight(int id, int flightNumber) {
        return false;
    }

    @Override
    public int queryFlight(int id, int flightNumber) {
        return 0;
    }

    @Override
    public int queryFlightPrice(int id, int flightNumber) {
        return 0;
    }

    @Override
    public boolean addCars(int id, String location, int numCars, int carPrice) {
        return false;
    }

    @Override
    public boolean deleteCars(int id, String location) {
        return false;
    }

    @Override
    public int queryCars(int id, String location) {
        return 0;
    }

    @Override
    public int queryCarsPrice(int id, String location) {
        return 0;
    }

    @Override
    public boolean addRooms(int id, String location, int numRooms, int roomPrice) {
        return false;
    }

    @Override
    public boolean deleteRooms(int id, String location) {
        return false;
    }

    @Override
    public int queryRooms(int id, String location) {
        return 0;
    }

    @Override
    public int queryRoomsPrice(int id, String location) {
        return 0;
    }

    @Override
    public int newCustomer(int id) {
        return 0;
    }

    @Override
    public boolean newCustomerId(int id, int customerId) {
        return false;
    }

    @Override
    public boolean deleteCustomer(int id, int customerId) {
        return false;
    }

    @Override
    public String queryCustomerInfo(int id, int customerId) {
        return null;
    }

    @Override
    public boolean reserveFlight(int id, int customerId, int flightNumber) {
        return false;
    }

    @Override
    public boolean reserveCar(int id, int customerId, String location) {
        return false;
    }

    @Override
    public boolean reserveRoom(int id, int customerId, String location) {
        return false;
    }

    @Override
    public boolean reserveItinerary(int id, int customerId, Vector flightNumbers, String location, boolean car, boolean room) {
        return false;
    }
}
