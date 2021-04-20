package common.util

import groovy.json.*
import java.lang.*
import common.util.Props as PROPS
import common.util.File as FILE
import static common.util.Shell.sh as sh

class Json {
    //Parsing a json string and return a map    
    public static def parseJson(String jsonString) {
        def parsedJson = new groovy.json.JsonSlurperClassic().parseText(jsonString)
        def m = [:]
        m.putAll(parsedJson)
        return m
    }
    //Parsing a json string
    public static def jsonParse(String jsonString) {
        return new groovy.json.JsonSlurperClassic().parseText(jsonString)
    }

    //convert to json string
    public static def objectToJsonString(Object jsonObject) {
        return JsonOutput.toJson(jsonObject);
    }

    // convert to prettified json string
    public static def objectToJsonPrettyString(Object jsonObject) {
        return JsonOutput.prettyPrint(objectToJsonString(jsonObject));
    }

    // get a value from the properties file
    public static def getValueFromPropertiesFile(field){
        def data = readFile(PROPS.PROPERTIES_FILE)
        if (isFileExists(PROPS.JAZZ_CONFIG_FILE)) {
            def jazzConfig = readFile(PROPS.JAZZ_CONFIG_FILE)
            data["configData"] = jazzConfig.configData
            data["authToken"] = jazzConfig.authToken
        }       
        return data[field]
    }
    
    // get entire properties file - this will be useful if we have more than 2,3 keys needed at a time
    public static def getAllProperties(){
        def data = readFile(PROPS.PROPERTIES_FILE)
        if (isFileExists(PROPS.JAZZ_CONFIG_FILE)) {
            def jazzConfig = readFile(PROPS.JAZZ_CONFIG_FILE)
            data["configData"] = jazzConfig.configData
            data["authToken"] = jazzConfig.authToken
        } 
        return data
    }
    
    /*
    * Method to check if a file exists
    */
    public static def isFileExists(filePath){
        java.io.File file = new java.io.File(filePath)
        return file.exists();
    }

    // set the value to the properties file
    public static def setAllProperties(properties) {
       if( properties.configData ) properties.remove("configData")
       if( properties.authToken ) properties.remove("authToken")

       writeFile(PROPS.PROPERTIES_FILE, properties)   
    }

    // set the value to the properties file
    public static def setValueToPropertiesFile(key, value) {
        def properties = [:]
        if (isFileExists(PROPS.PROPERTIES_FILE)) {
            properties = readFile(PROPS.PROPERTIES_FILE)
        }
       
        properties[key] = value
        writeFile(PROPS.PROPERTIES_FILE, properties)   
    }

    // set the jazz configurations 
    public static def setJazzConfigurations(key, value) {
        def jazzConfigs = [:]
        //Creating jazz config directory
        if (isFileExists(PROPS.JAZZ_CONFIG_FILE)) {
            jazzConfigs = readFile(PROPS.JAZZ_CONFIG_FILE)
        } else {
            sh("mkdir -p JazzConfigDirectory")
        } 

        jazzConfigs[key] = value
        writeFile(PROPS.JAZZ_CONFIG_FILE, jazzConfigs)   
    }

    // write data in json format to file
    public static def writeFile (filename, data) {
        def jsonStr = JsonOutput.toJson(data)
        def jsonPrettyStr = JsonOutput.prettyPrint(jsonStr)
        FILE.writeFile(filename, jsonPrettyStr)
    }

    // read data from file in json format to object
    public static def readFile(filename) {
        return new JsonSlurperClassic().parse(new java.io.File(filename))
    }
}