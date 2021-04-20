This service cleans up test services from JAZZ prod environment.

#### Service Details

Service deletes services from JAZZ prod environment using the configuration value:

```
"CLEANUP_DOMAIN_LIST": ["{domain1}", "{domain2}"]"
```

The *domain* has to be an existent domain in prod environment. 

The allowed values for services are **&ast;** - indicating all services in that *domain*. Alternatively you can provide a comma-separated list of *services* in that *domain*. 

You can also specify a list of domain-services combo by using the delimiter **;**.

A valid value for this configuration is:  
```
"CLEANUP_DOMAIN_LIST": ["domain1", "domain2"]"
```

In the example above, the service will remove all services in domain *delete* as well as *foo* and *bar* from domain *test*.
