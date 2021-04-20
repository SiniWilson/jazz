/**
	Nodejs Template Project
  @module: response.js
  @description: Defines reponse object
	@author:
	@version: 1.0
**/

module.exports = (response, input, projectId=null) => {
  var output = {
    "data": response,
    "input": input
  };
  if(projectId) {
    output['projectId'] = projectId
  }
  return output;
};
