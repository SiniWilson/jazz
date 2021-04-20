import spock.lang.*
import common.util.Json as JSON
import static common.util.Shell.sh as sh

class FirstSpecification extends Specification {

    def checkAcmModuleFileExists() {
        println "TEST: checking AWSLambdaEventsModule.groovy exists"
        given:
            def file;
        
        when:
            file = new File("./AWSLambdaEventsModule.groovy")

        then:
            file.exists() == true
    }

    def splitAndGetResourceName() {
        println "TEST: testing splitAndGetResourceName method"
        given:
            def dynamodbResource
            def kinesisResource
            def prodResource
            def shell = new GroovyShell()
            def file = shell.parse(new File('./AWSLambdaEventsModule.groovy'))
        
        when:
            dynamodbResource = file.splitAndGetResourceName('arn:aws:dynamodb:us-west-2:12345:table/tablename', 'dev')
            kinesisResource = file.splitAndGetResourceName('arn:aws:kinesis:us-west-2:12345:stream/kinesisqueue', 'dev')
            prodResource = file.splitAndGetResourceName('arn:aws:dynamodb:us-west-2:12345:table/prodtable', 'prod')

        then:
            dynamodbResource == 'tablename_dev'
            kinesisResource == 'kinesisqueue_dev'
            prodResource == 'prodtable'
    }

    def getSqsQueueName() {
        println "TEST: testing getSqsQueueName method"
        given:
            def sqsResource
            def shell = new GroovyShell()
            def file = shell.parse(new File('./AWSLambdaEventsModule.groovy'))

        when:
            sqsResource = file.getSqsQueueName('arn:aws:sqs:us-west-2:12345:sqsqueue', 'dev')

        then:
            sqsResource == 'sqsqueue_dev'
    }

    def checkAndConvertEvents() {
        println "TEST: testing checkAndConvertEvents method"
        given:
            def newEvents
            def expectedNewEvents = ['s3:ObjectCreated:*']
            def shell = new GroovyShell()
            def file = shell.parse(new File('./AWSLambdaEventsModule.groovy'))
        
        when:
            newEvents = file.checkAndConvertEvents(['s3:ObjectCreated:*'])

        then:
            newEvents == expectedNewEvents

    }
}
