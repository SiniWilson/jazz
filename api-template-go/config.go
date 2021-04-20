/**
	Jazz Golang template
	@module: config
	@description: Config module (with the custom formatter) to load environment configurations available in config/
	@author:
	@version: 1.0
**/

package main

import (
	"context"
	"os"
	"strings"

	"github.com/spf13/viper"
)

type Config struct {
	ctx   context.Context
	event map[string]interface{}
}

// Load configuration file
func (c *Config) LoadConfiguration(ctx context.Context, event map[string]interface{}) {
	c.ctx = ctx
	c.event = event

	var environment string
	var functionName string

	if value, ok := event["stage"]; ok {
		// get current stage value
		environment = value.(string)
	} else {
		// get AWS function name from environment variables
		functionName = os.Getenv("AWS_LAMBDA_FUNCTION_NAME")
		fnName := string(functionName[strings.LastIndex(functionName, "_")+1 : len(functionName)])
		if strings.HasPrefix(fnName, "stg") {
			environment = "stg"
		} else if strings.HasPrefix(fnName, "prod") {
			environment = "prod"
		} else {
			environment = "dev"
		}
	}

	if len(environment) > 0 {
		viper.SetConfigFile("./bin/config/" + environment + "-config.json")
		// Look for environment specific config file
		if err := viper.ReadInConfig(); err != nil {
			logger.ERROR("Error while loading configurations for environment: " + environment)
		}
	} else {
		logger.ERROR("Unknown environment! Cannot load configurations!")
	}
}
