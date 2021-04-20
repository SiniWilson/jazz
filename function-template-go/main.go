/**
	Jazz Golang template
	@description:
	@author:
	@version: 1.0
**/

package main

import (
	"context"

	"github.com/aws/aws-lambda-go/lambda"
	"github.com/spf13/viper"
)

// Response Model
type Response struct {
	Data  map[string]string      `json:"data,omitempty"`
	Input map[string]interface{} `json:"input,omitempty"`
}

// Declare logger variable
var logger *Logger

// Core handler function
func Handler(ctx context.Context, event map[string]interface{}) (Response, error) {

	// Initialize logger module
	logger := new(Logger)
	logger.init("INFO", ctx)

	// Initialize config module
	configModule := new(Config)
	configModule.LoadConfiguration(ctx, event)

	// Examples for sample log messages with different log levels
	logger.INFO("Interesting runtime events (Eg. connection established, data fetched etc.)")
	/*
		logger.ERROR('Runtime errors or unexpected conditions.');
		logger.WARN('Runtime situations that are undesirable or unexpected, but not necessarily "wrong".');
		logger.TRACE('Generally speaking, most lines logged by your application should be written as verbose.');
		logger.DEBUG('Detailed information on the flow through the system.');
	*/

	// Get Config values
	configValue := viper.Get("configKey").(string)

	sampleResponse := map[string]string{
		"foo":       "foo-value",
		"bar":       "bar-value",
		"configKey": configValue,
	}

	// Sample response
	return Response{
		Data:  sampleResponse,
		Input: event,
	}, nil
}

// Main function
func main() {
	// Start function
	lambda.Start(Handler)
}
