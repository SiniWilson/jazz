import sys
import os
import json

#sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
#sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from .. import handler

class DotDict(dict):
   pass

def test():

    context = DotDict()
    context.function_name = "jazztest_sls-app-python-function_prod"
    response = handler.hello({}, context)    
    assert response == {"message": "Your function executed successfully!", "event":{}}
   