#   Python Template Project
#   @Author:
#   @version: 1.0
import sys
import os
import json

sys.path.insert(0, 'library')

from components.logger import Logger
from components.config import Config

# import your dependencies
import requests


def handler(event, context):
    # initialze logger module
    logger = Logger(event, context)

    # Load config handler
    config = Config(event)

    ## ==== Sample code to fetch environment specific configurations ====
    #myconfig = config.get_config('default')
    #logger.info ('One of the environment configuration: config_key => ' + myconfig['config_key'])

    ## ====  Logger usage ====
    # logger.error('Runtime errors or unexpected conditions.')
    # logger.warn('Runtime situations that are undesirable, but not wrong')
    # logger.info('Interesting runtime events eg. connection established)')
    # logger.verbose('Generally speaking, most log lines should be verbose.')
    # logger.debug('Detailed information on the flow through the system.')

    logger.info('Your function got executed successfully!')
    return {
        "message": "Your function got executed successfully!",
        "event": event
    }
