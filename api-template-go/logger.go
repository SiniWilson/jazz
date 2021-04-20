/**
	Jazz Golang template
	@module: logger
	@description: Logger module (with the custom formatter)
	@author:
	@version: 1.0
**/

package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/aws/aws-lambda-go/lambdacontext"
)

var loglevels = map[string]int{
	"ERROR":   4,
	"WARN":    3,
	"INFO":    2,
	"VERBOSE": 1,
	"DEBUG":   0,
}

var config = map[string]string{
	"curLogLevel": "INFO",
	"requestId":   "",
}

// Custom log formatter
type logWriter struct {
	levelName string
}

func (lw *logWriter) init(levelName string) {
	lw.levelName = levelName
}

func (writer *logWriter) Write(bytes []byte) (int, error) {
	return fmt.Print(time.Now().UTC().Format("2006-01-02T15:04:05.999Z") + " " + config["requestId"] + " " + writer.levelName + " " + string(bytes))
}

// Logger implementation
type Logger struct{}

func (l *Logger) init(level string, ctx context.Context) {
	setLevel(level)
	// Get RequestId
	lc, _ := lambdacontext.FromContext(ctx)
	config["requestId"] = lc.AwsRequestID
}

func (l *Logger) WARN(message string) {
	logthis("WARN", message)
}

func (l *Logger) INFO(message string) {
	logthis("INFO", message)
}

func (l *Logger) ERROR(message string) {
	logthis("ERROR", message)
}

func (l *Logger) VERBOSE(message string) {
	logthis("VERBOSE", message)
}

func (l *Logger) DEBUG(message string) {
	logthis("DEBUG", message)
}

// Set logger level
func setLevel(level string) string {
	// Default Log Level is INFO
	var log_level string
	_, isLevelPresent := loglevels[level]

	if isLevelPresent {
		log_level = level
	} else {
		// Use log level if available as environment variable
		log_level = os.Getenv("LOG_LEVEL")
	}
	config["curLogLevel"] = log_level
	return log_level
}

func logthis(level string, message string) {
	if loglevels[level] >= loglevels[config["curLogLevel"]] {
		if level == "VERBOSE" {
			logWithFormater("VERBOSE", message)
		}
		if level == "INFO" {
			logWithFormater("INFO", message)
		}
		if level == "WARN" {
			logWithFormater("WARN", message)
		}
		if level == "DEBUG" {
			logWithFormater("DEBUG", message)
		}
		if level == "ERROR" {
			logWithFormater("ERROR", message)
		}
	}
}

// This function uses the custom formatter to log the Messages
func logWithFormater(logLevel string, message string) {
	log.SetFlags(0)
	logger := new(logWriter)
	logger.init(logLevel)
	log.SetOutput(logger)
	log.Println(message)
}
