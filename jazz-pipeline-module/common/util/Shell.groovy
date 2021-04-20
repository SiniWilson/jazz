package common.util
import groovy.json.*
import java.lang.*

class Shell {
    // If  it succeeds it return the output from command, if it fails, it throws exception
    public static def sh(String command, boolean test=false, boolean printCommand=true) {
        if (printCommand)
        {
            println "Running shell command: " + command
        }
        def revisedCommand =  ['bash', '-c', command]
        def out = new StringBuffer(), err = new StringBuffer()
        def proc = revisedCommand.execute()
        proc.waitForProcessOutput(out, err);
        int exitCode = proc.exitValue();
        if (exitCode == 0) {
            return out.toString();
        } else {
            throw new Exception("Error running command: ${command} Exit Code: ${exitCode} Error: ${err.toString()}");
        }
    }
}
