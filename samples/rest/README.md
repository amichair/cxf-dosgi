# CXF DOSGi example REST

This example shows how to expose and use a REST service using declarative services.

The API module defines the TaskResource interface which is annotated using JAXRS annotations.

The impl module implements the TaskService using a simple HashMap internally. It allows to manage Task objects which represent items of a to do list.


## Installation

Unpack karaf 4 into a server and client directory.

### Install server

Start the server karaf

```
feature:repo-add cxf-dosgi-samples 2.4.0
feature:install cxf-dosgi-sample-rest-impl
rsa:endpoints
```

The last command should show one endpoint with a URI as id. You should be able to open the url in the browser. The browser should show the predefined tasks as xml.

```
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' -d '{task:{id: 3, title: "Another task"}}' 'http://localhost:8181/cxf/tasks/'
```

Check that task was added

```
curl --header "Accept:application/json" http://localhost:8181/cxf/tasks/3
```

### Install client

Start the client karaf

```
feature:repo-add cxf-dosgi-samples 2.4.0
feature:install cxf-dosgi-sample-rest-client
```
Use commands to test

```
rsa:endpoints
task:list
task:add 4 Mytask
task:list
```

### Browse Swagger documentation

The jaxrs sample also creates swagger documentation for the REST endpoint.

[Get the swagger documentation for the jaxrs sample](http://localhost:8181/cxf/tasks/api-docs?url=../swagger.json).

### Add logging intent

Starting with CXF 3.1.9 the CXF logging feature is exported as an intent by
default this makes it very easy to add logging to the rest example.

```
config:property-set -p TaskResource service.exported.intents logging
endpoint http://localhost:8181/cxf/tasks
```

This installs the CXF logging feature and adds the logging intent to the
rest sample component. The command endpoint should then show that the intent
logging is applied.

Any http requests to the service should now show as a logging message in the
karaf log.
