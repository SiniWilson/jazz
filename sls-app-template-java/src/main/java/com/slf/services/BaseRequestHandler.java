package com.slf.services;

import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.slf.exceptions.InternalServerErrorException;
import com.slf.model.Response;

/**
 * Abstract BaseJazzHandler for all handlers.
 * Provides a template for authoring AWS Serverless Application with its own deployment descriptor.
 * This implementation is based on the interface
 * 'RequestHandler' with custom POJO Input/Output
 *
 * @author
 * @version 1.2
 * @date
 *
 */

public abstract class BaseRequestHandler implements RequestHandler<Map<String, Object>, Response> {

	static final Logger logger = Logger.getLogger(BaseRequestHandler.class);

	protected Map<String, Object> body = null;

	public Response handleRequest(Map<String, Object> input, Context context) {

        if(input instanceof Map) {
        	body = input;
        }
		
		 return execute(input, context);
	}

	/**
	 * Implement this method in the sub class to handle business logic
	 *
	 * @param input
	 * @param context
	 * @return Response
	 */
	abstract Response execute(Map<String, Object> input, Context context);

}
