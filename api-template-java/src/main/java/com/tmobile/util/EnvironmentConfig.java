package com.tmobile.util;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import com.amazonaws.services.lambda.runtime.Context;
import com.tmobile.exceptions.BadRequestException;

/**
 * The environment configuration reader class. Environment configurations can be specified in a properties file.
 * Each environment will be having a separate properties file. For ex. dev.properties for 'DEV'
 *
 * Usage:
 * EnvironmentConfig configObject = new EnvironmentConfig(stage, context);
 * String restUri = configObject.getConfig("ES_URL");

 * @version
 *
 */
public class EnvironmentConfig {

	private static Properties props = new Properties();
	private String stage = null;
    private String configFile = null;

    public EnvironmentConfig(Map<String, Object> input, Context context) throws IOException {
        super();

        if(null != input.get("stage")) {
            stage = ((String) input.get("stage")).toLowerCase();
        } else {
            String fnName = context.getFunctionName();
            if(null != fnName) {
                int lastIndx = fnName.lastIndexOf('_');
                stage = fnName.substring(lastIndx+1);
            }
        }

        if(stage.isEmpty()) {
            throw new BadRequestException("Invalid Stage. Can't load ENV configurations");
        }

        if(stage.equals("prod") || stage.equals("stg")) {
            configFile = "/"+stage+".properties";
        } else {
            configFile = "/dev.properties";
        }

		props.load(this.getClass().getResourceAsStream(configFile));
	}

    public String getConfig(String key) {
        if(props != null) {
            return props.getProperty(key);
        }
        return null;
    }

	@Override
	public String toString() {
		return "Loaded config for "+stage;
	}
}
