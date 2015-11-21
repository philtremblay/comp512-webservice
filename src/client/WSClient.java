package client;


//import org.apache.catalina.WebResource;

import java.net.URL;
import java.net.MalformedURLException;

//import server.ws.ResourceManager;


public class WSClient {

    public client.ResourceManagerImplService service;

    public client.ResourceManager proxy;
    public  URL wsdlLocation;
    //public URLConnection http;

    public WSClient(String serviceName, String serviceHost, int servicePort) 
    throws MalformedURLException {

        wsdlLocation = new URL("http", serviceHost, servicePort,
                "/" + serviceName + "/service?wsdl");

        service = new client.ResourceManagerImplService(wsdlLocation);

        proxy = service.getResourceManagerImplPort();

    }

}
