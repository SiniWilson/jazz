package common.util

import java.lang.*
import org.yaml.snakeyaml.*
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.representer.Represent;
import org.codehaus.groovy.runtime.GStringImpl

class Yaml {

    //Writing yaml data to the file
    public static def writeFile (filename, data) {
        DumperOptions options = new  org.yaml.snakeyaml.DumperOptions()
        options.setPrettyFlow(true)
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
        def yaml = new org.yaml.snakeyaml.Yaml(options)
        yaml.dump(data, new java.io.FileWriter(filename))
    }

    //Reading yaml data from file
    public static def readFile(filename) {
        def yaml = new org.yaml.snakeyaml.Yaml()
        def file = new java.io.File(filename)
        def yamlData = yaml.load(file.text)
        return yamlData  
    }

    //Load yaml data
    public static def loadYaml(data) {
        def yaml = new org.yaml.snakeyaml.Yaml()
        def yamlMap = yaml.load(data)
        return yamlMap
    }

     //Writing Map data to the file 
     /* @Param
     ** filename, data as type of Map
     */
    public static def writeYaml(filename, data) {
        DumperOptions options = new  org.yaml.snakeyaml.DumperOptions()
        options.setPrettyFlow(true)
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
        def yaml = new org.yaml.snakeyaml.Yaml(new GroovyRepresenter(), options)
        //def yamlData = yaml.dump(data)
        yaml.dump(data, new java.io.FileWriter(filename))
    }
}

public class GroovyRepresenter extends Representer {
    public GroovyRepresenter() {
        Represent stringRepresenter  = this.representers.get(String.class);
        this.representers.put(GStringImpl.class, stringRepresenter );
    }
}