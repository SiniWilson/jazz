package common.util
import groovy.json.*
import java.lang.*

class Status {
    static enum DeploymentStatus {
        started,
        failed,
        approval_expired,
        approval_pending,
        approval_rejected,
        successful
    }
}
