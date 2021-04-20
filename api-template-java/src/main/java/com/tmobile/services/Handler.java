package com.tmobile.services;

import java.util.HashMap;
import java.util.Map;

import com.tmobile.util.ErrorUtil;
import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import com.amazonaws.services.lambda.runtime.Context;
import com.tmobile.model.Response;
import com.tmobile.exceptions.BadRequestException;

/**
 * Java template for authoring AWS lambda functions. 
 * This implementation is based on the interface 'RequestHandler' with custom POJO Input/Output
 *
 * @author
 * @version 1.2
 * @date
 */

public class Handler extends BaseJazzRequestHandler {

    private static final Logger LOGGER = Logger.getLogger(Handler.class);

    /**
     * Override and implement this method from BaseJazzRequestHandler. This method would have the main
     * processing logic to serve the request from User
     */
    public Response execute(Map<String, Object> input, Context context) {

        /* Read environment specific configurations from properties file */
        String configStr = configObject.getConfig("config_key");
        LOGGER.info("You are using the env key: " + configStr);

        /* Logger supports the following levels of logs */
        LOGGER.trace("Fine-grained informational events than DEBUG");
        LOGGER.info("Interesting runtime events (Eg. connection established, data fetched etc.)");
        LOGGER.warn("Runtime situations that are undesirable or unexpected, but not necessarily \"wrong\".");
        LOGGER.debug("Detailed information on the flow through the system.");
        LOGGER.error("Runtime errors or unexpected conditions.");
        LOGGER.fatal("Very severe error events that will presumably lead the application to abort");

        LOGGER.debug("The request stage: " + this.stage);
        LOGGER.debug("The request method: " + this.method);
        LOGGER.debug("The post request payload: " + this.body);
        LOGGER.debug("The query params : " + this.query);
        LOGGER.debug("The request headers: " + this.headers);
        LOGGER.debug("The resource path: " + this.resourcePath);

        /* Sample output data */
        Map<String, String> data = new HashMap();

        if ("GET".equalsIgnoreCase(this.method)) {
            data.put("message", "GET executed successfully");
            return new Response(data, this.query);
        } else if ("POST".equalsIgnoreCase(this.method)) {
            data.put("message", "POST executed successfully");
            return new Response(data, this.body);
        } else {
            throw new BadRequestException(ErrorUtil.createError(context, "Invalid/Empty Payload", HttpStatus.SC_BAD_REQUEST, "BAD_REQUEST"));
        }
    }
}
