import sys
import os
import json


sys.path.append(".")
sys.path.insert(0, 'library')

from components.logger import Logger

# import installed package
import requests

def hello(event, context):
    # initialize logger module
    logger = Logger(event, context)
   

    ## ==== Log message samples ====
    # logger.error('Runtime errors or unexpected conditions.')
    # logger.warn('Runtime situations that are undesirable, but not wrong')
    # logger.info('Interesting runtime events eg. connection established)')
    # logger.verbose('Generally speaking, most log lines should be verbose.')
    # logger.debug('Detailed information on the flow through the system.')

    logger.info('Sample response for function.')
    return {
        "message": "Your function executed successfully!",
        "event": event
    }

