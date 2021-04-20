
+++
date = "2017-11-27T14:00:00-07:00"
draft = true
title = "Handling secrets in your code"
author = "Deepak Babu"
weight = 7

[menu.main]
Name = "Handling secrets in your code"
parent = "start-here"

pre = "Developers can now encrypt secrets using the utility that we are providing. Secrets are fetched from the secret store only during runtime and are never persisted."

+++
<!-- Add a short description in the pre field inside menu

Handling secrets in your code
============================= -->

Developers can now encrypt secrets using the utility that we are providing. Secrets are decrypted during runtime and are never persisted.

### Step 1: Encrypt your secret(s)

Developers can use [*/encrypt-secret*](http://editor.cloud-api.corporate.t-mobile.com/?url=https://cloud-api-doc.corporate.t-mobile.com/encrypt-secret/swagger.json) API to encrypt the secret and record the associated *cipher* and *secret\_id* (this functionality will be a part of Jazz UI/CLI in the next set of versions). You can use your favorite REST client or our swagger editor [here](http://editor.cloud-api.corporate.t-mobile.com/?url=https://cloud-api-doc.corporate.t-mobile.com/encrypt-secret/swagger.json) to call this API and generate the *cipher* & *secret\_id.* If you are using the swagger editor, you can follow the the steps below to get your secret encrypted.

* Encryption service requires you to sign in. Please use the login service through swagger editor [here](http://editor.cloud-api.corporate.t-mobile.com/?url=https://cloud-api-doc.corporate.t-mobile.com/jazz_login/prod/swagger.json). Plugin in your Jazz credentials (CORP AD credentials) & hit the API. Extract the *token* from the service response. You will need this token to use the encryption service.

* Next, you can use the swagger editor [here](http://editor.cloud-api.corporate.t-mobile.com/?url=https://cloud-api-doc.corporate.t-mobile.com/encrypt-secret/swagger.json) to call the encryption service.

* Hit 'AUTHORIZE' and supply the token that you have just obtained after calling the login service.  Hit 'AUTHORIZE' again to save the token locally.

* You are now set to encrypt your secret text! Click 'TRY IT OUT' button, provide your secret text in the service request & hit the API using the 'EXECUTE' button in the editor. If the API call is successful, you should now see the cipher & secret\_id in the service response.

### Step 2: Use the secret handler in your code

You are now ready to use this secret metadata in your code. The standard service template provides all the libraries required to work with the secret management framework to decrypt secrets. Following are examples on how to use this code in a service for different runtimes. Each template will have environment specific files that will be picked up based on the environment that the code is running in. For example, configurations that you specify in prod-config.json (for NodeJs runtime) will be available for the code when it is running in production environment.

#### Runtime: NodeJs

* Include your secret metadata (cipher & secret\_id) that you have generated in step #1 in components/$env-config.json file. Give them appropriate key names that you can refer them in your actual code. Sample config.json below - 

	{{< highlight json  >}}
    
    {
		"my_secret": {
			"secret_id": "045d6a79-d731-fa58-dd03-0e29886c2a4d",
			"cipher": "AQECAHhwMp8jTNax4="
		},
		"another_secret": {
			"secret_id": "98b91c4e-a80f-4cf3-b5c3-e89ba5dba838",
			"cipher": "AhdsYGiUA8jTNax4="
		},
		"some-config-key": "config-value"
	}

    {{< /highlight >}}

* Include the secret management module in your code and initialize the module as shown below.

    {{< highlight javascript  >}}
    
    const secretHandler = require("./components/secret-handler.js");
    {{< /highlight >}}

* Decrypt the secret in runtime by calling the decryptSecret utlility as shown below. Remember not to log the secrets in your code after they are decrypted! They will show up in plain text in the logs which is not something that you want.

    {{< highlight javascript  >}}
    
    var decryptObj = secretHandler.decryptSecret(config.my_secret);

    var plainText = "";
    
    var decryptionError = "";
    
    if (!decryptObj.error) {
    
        plainText = decryptObj.message; // Plain text! 
    
    } else {
    
        decryptionError = decryptObj.message;
    }
    {{< /highlight >}}


#### Runtime: Python

* Include your secret metadata (cipher & secret\_id) that you have generated in step #1 in components/$env-config.ini file. Give them appropriate key names that you can refer them in your actual code. Sample config.json below - 

	{{< highlight python  >}}
    
    [mysecret]
    secret_id=045d6a79-d731-fa58-dd03-0e29886c2a4d
    cipher=AQECAHhwMpPx4=
    
    
    [another_secret]
    secret_id=e877d2d4-eef4-4857-9286-790ec4f09e34
    cipher=AQETYADHAadY8c=

    {{< /highlight >}}

* Include the secret management module in your code and initialize the module as shown below.

    {{< highlight python  >}}
    
    from components.secret_handler import SecretHandler

    secret_handler = SecretHandler()
    {{< /highlight >}}

* Decrypt the secret in runtime by calling the decryptSecret utlility as shown below. Remember not to log the secrets in your code after they are decrypted! They will show up in plain text in the logs which is not something that you want.

    {{< highlight python  >}}
    
    # get secret information(mysecret) from the config
    encrypted_secret = config.get_config('mysecret')
    
    # Decrypt the secret.
    secret_res = secret_handler.decrypt_secret(encrypted_secret)
    
    # Check if error exists & if not get the decrypted secret
    if 'error' in secret_res and secret_res['error'] is not None:
        decryptionerror = secret_res['message']
    else:
        plaintext = secret_res['message']
    {{< /highlight >}}


#### Runtime: Java

* Include your secret metadata (cipher & secret\_id) that you have generated in step #1 in resources/$env.properties file. Give them appropriate key names that you can refer them in your actual code. Sample config.json below - 

	{{< highlight java  >}}
    
    mysecret={"secret_id":"045d6a79-d731-fa58-dd03-0e29886c2a4d","cipher":"AQETYADHAadY8c/TY/TQriofoq10oaawRw/gF5Ub8feAqQ+yejFcxNKg7/Y1Pc1PQ8jTNax4="}

    {{< /highlight >}}

* Include the secret management module in your code and initialize the module as shown below.

    {{< highlight java  >}}
    
    // Import secret handler class
    import com.tmobile.components.SecretHandler;
    {{< /highlight >}}

* Decrypt the secret in runtime by calling the decryptSecret utlility as shown below. Remember not to log the secrets in your code after they are decrypted! They will show up in plain text in the logs which is not something that you want.

    {{< highlight java  >}}
    
    String secret;
    try {
        configObject = new EnvironmentConfig(input);
        secret = configObject.getConfig("mysecret");
    } catch (Exception ex) {
        throw new InternalServerErrorException("Could not load env properties file "+ex.getMessage());
    }

    SecretHandler secretHandler = new SecretHandler(input);
    String plaintext;
    HashMap<String, Object> secretObj = secretHandler.decryptSecret(secret);
    if ((Boolean)secretObj.get("error") != true) {
        plaintext = secretObj.get("message").toString();
    } else{
        logger.error("encountered error while trying to decrypt secret, error message: " + secretObj.get("message"));
    }   
    {{< /highlight >}}
