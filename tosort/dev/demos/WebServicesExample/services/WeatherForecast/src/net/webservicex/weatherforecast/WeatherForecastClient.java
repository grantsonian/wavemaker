
package net.webservicex.weatherforecast;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceFeature;


/**
 * Get one week weather forecast for valid zip code or Place name in USA
 * 
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.1.5-b03-
 * Generated source version: 2.1
 * 
 */
@WebServiceClient(name = "WeatherForecast", targetNamespace = "http://www.webservicex.net", wsdlLocation = "temp.wsdl")
public class WeatherForecastClient
    extends Service
{

    private final static URL WEATHERFORECAST_WSDL_LOCATION;
    private final static Logger logger = Logger.getLogger(net.webservicex.weatherforecast.WeatherForecastClient.class.getName());

    static {
        URL url = null;
        try {
            URL baseUrl;
            baseUrl = net.webservicex.weatherforecast.WeatherForecastClient.class.getResource(".");
            url = new URL(baseUrl, "temp.wsdl");
        } catch (MalformedURLException e) {
            logger.warning("Failed to create URL for the wsdl Location: 'temp.wsdl', retrying as a local file");
            logger.warning(e.getMessage());
        }
        WEATHERFORECAST_WSDL_LOCATION = url;
    }

    public WeatherForecastClient(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public WeatherForecastClient() {
        super(WEATHERFORECAST_WSDL_LOCATION, new QName("http://www.webservicex.net", "WeatherForecast"));
    }

    /**
     * 
     * @return
     *     returns WeatherForecastSoap
     */
    @WebEndpoint(name = "WeatherForecastSoap")
    public WeatherForecastSoap getWeatherForecastSoap() {
        return super.getPort(new QName("http://www.webservicex.net", "WeatherForecastSoap"), WeatherForecastSoap.class);
    }

    /**
     * 
     * @param features
     *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns WeatherForecastSoap
     */
    @WebEndpoint(name = "WeatherForecastSoap")
    public WeatherForecastSoap getWeatherForecastSoap(WebServiceFeature... features) {
        return super.getPort(new QName("http://www.webservicex.net", "WeatherForecastSoap"), WeatherForecastSoap.class, features);
    }

}