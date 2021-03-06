Backplane 2.0 README
====================


Backplane API
-------------

The Backplane Server exposes the following Backplane API endpoints, as defined by the
[Backplane v2.0 Section 13] [6].

All endpoints are available at paths starting with the `/v2/`.

### Authenticated Access Token Request

* Endpoint:  `/v2/token`

* Security: HTTPS POST, OAuth2 client credentials (accepted in request header only)

* Request header: Basic authorization `client_id` : `client_secret`

* Request parameters ([OAuth2 code or client_credential authorization request] [1]):
  Authorization: Basic header: `client_id`, `client_secret`
  POST body, www-form-urlencoded: `grant_type`, `code` (required for `authorization_code` grant type), `redirect_uri` (required for `authorization_code` grant type), `scope` (optional)

* Response body (OAuth2 Access Token Response):

  Example:

```json
{
  "access_token": "54BBThPAHwI5mBzmSE0Y",
  "token_type": "Bearer",
  "refresh_token": "DntMlWjISKzwNojGETpM"
}
```

Per OAuth2, the `scope` parameter will be returned if the granted scope differs from the requested scope.

### Anonymous Access Token Request

* Endpoint:  `/v2/token`

* Security: HTTPS GET

* Request parameters: `callback`, `bus`, `scope` (optional)

* Response body (similar to an OAuth2 Access Token Response):

  Example:

```json
callback(
    {
        "access_token": "w6LBpOjnulFBuqN8T5gB",
        "token_type": "Bearer",
        "expires_in": 604800,
        "scope": "bus:customer.example.com channel:0a92d76f54f7877a68abe19c5b3ffcf8",
        "refresh_token": "7fx74eIC1EwYE5BzeyAD",
    }
)
```

`bus` parameter and response field: the bus to which the (anonymous/browser/javascript) client requests the newly generated channel to be bound (and the server echoes-back the bound bus).
Authenticated clients wishing to post to this channel must have obtained authorization for this bus before posting.

### Refresh Tokens

Refresh tokens are issued with all token requests (privileged and anonymous) and are sent to the client in the JSON response field "refresh_token".

Refresh tokens are bound to the scope of the access tokens with which they were issued, and (for authenticated token requests) also to the client that issued the request.

Refresh tokens are invalidated on the first use, and a new refresh token is issued with each refresh token response.

#### Authenticated Refresh Token Request

* Endpoint:  `/v2/token`

* Security: HTTPS POST, OAuth2 client credentials (accepted in request header only)

* Request header: Basic authorization `client_id` : `client_secret`

* Request parameters ([OAuth2 Refreshing an Access Token] [3]):
  Authorization: Basic header: `client_id`, `client_secret`
  POST body, www-form-urlencoded: `refresh_token`, `grant_type` = `refresh_token`, `scope` (optional)

* Response body (OAuth2 Access Token Response): same as the Authenticated Token Response

#### Anonymous Refresh Token Request

* Endpoint:  `/v2/token`

* Security: HTTPS GET

* Request parameters: `callback`, `refresh_token`, `scope` (optional)

* Response body: same as the Anonymous Token Response


### Get Messages

* Endpoint:  `/v2/messages`

* Security: HTTPS GET, OAuth2 access token

* Request parameters: `block` (optional, default 0), `callback` (optional), `since` (optional)

An Authenticated Access Token MUST be presented in the header only, for example: `Authorization: Bearer vF9dft4qmT`

An Anonymous Access Token may be presented as the URL parameter `access_token`.

* Response body: JSON with the fields `nextUrl` (continuation URL) and `messages` (array of backplane messages).
 If the number of messages to return exceeds the pagination limit, the field `moreMessages` we be set to true and the
 `nextUrl` URL may be used to fetch the remainder of the messages until `moreMessages` returns false.

The `block` parameter sets the number of seconds the server should hold open the connection waiting for new messages to arrive.
This allows the client to limit the number of polling requests for greater efficiency.

  Example:

```json
{
    "nextURL": "https://bp.example.com/v2/messages?since=958bfa2dd8aed82c86afbd54b4a314a5",
    "messages": [
        {
            "bus": "customer.com",
            "channel": "67dc880cc265b0dbc755ea959b257118",
            "messageURL": "https://bp.example.com/v2/message/097a5cc401001f95b45d37aca32a3bd2",
            "payload": {
            "role": "administrator"
        },
            "source": "http://aboutecho.com",
            "type": "identity/ack",
            "sticky": true
        }
    ],
    "moreMessages": false
}
```

* Error responses:

    If an Authenticated Access Token was presented with the request, the server returns a HTTP status code
    appropriate for the error cause (4xx or 5xx), and an error message.

    If an Anonymous Access Token was presented with the request, the server returns HTTP status code 200
    and a JSONP-padded response with the `callback` parameter value presented with the request.
    The JSON payload will contain and `error` field and optionally additional fields such as `error_description`.

    Note that there may be internal server errors that prevent the JSONP-padding of error responses to be applied;
    in such cases a 5xx HTTP status code is returned.


### Get Single Message

* Endpoint:  `/v2/message/<message_id>`

* Security: HTTPS GET, OAuth2 access token

* Request parameters: `callback` (optional)

[ An Authenticated Access Token MUST be presented in the header only, for example: `Authorization: Bearer vF9dft4qmT`
while an Anonymous Access Token may be presented as the URL parameter `access_token`. ]

* Response body: JSON backplane message

  Example:

```json
{
    "messageURL": "https://bp.example.com/v2/message/097a5cc401001f95b45d37aca32a3bd2",
    "source": "http://aboutecho.com",
    "type": "identity/ack"
    "sticky": true,
    "bus": "customer.com",
    "channel": "67dc880cc265b0dbc755ea959b257118",
    "payload": {
        "role": "administrator"
    }
}
```

### Post Message

* Endpoint:  `/v2/message`

* Security: HTTPS POST, OAuth2 access token, MUST be presented in the request header
* Request header: Authorization: Bearer <access_token>
* Request parameters: JSON data with a `message` object field of a single backplane message to be posted
* The channel used in the message MUST have been issued in the scope of an active (non-expired) regular access token.
* Response body: HTTP status code 201 Created or 403 Forbidden, empty body

  Example request:

```json
{
    "message": {
        "type": "identity/ack",
        "sticky": true,
        "bus": "customer.com",
        "channel": "67dc880cc265b0dbc755ea959b257118",
        "payload": {
            "role": "administrator"
        }
    }
}
```

#### Post Error Response

If the post exceeds the maximum number of message allowed (default of 100 per channel) an error is returned;
this value may be overridden by placing an entry in the `<backplane-instance>_bpserverconfig` table called
`DEFAULT_MESSAGES_MAX` with an appropriate value.

```json
{"ERR_MSG":"Message limit exceeded for this channel"}
```


Configuration
=============

All Bus Provisioning API calls are only available over HTTPS and are authenticated against the `<backplane-instance>_Admin` table.

Bus Configuration
-----------------

A (customer) bus is defined by the following Bus Configuration data, kept in the `<backplane-instance>_BusConfig2` table:

* `BUS_NAME`: bus name

* `OWNER`: the bus owner's username from the _User table

* `RETENTION_TIME_SECONDS`: message retention time (seconds)

* `RETENTION_STICKY_TIME_SECONDS`: message retention time for sticky messages (seconds)

Bus Owner Configuration
-----------------------

Bus owner user accounts are kept in the `<backplane-instance>_v2_bus_owners` table:

* `USER`: backplane username

* `PWDHASH`: backplane password (hash)

Backplane Client Configuration
------------------------------

Backplane OAuth2 client accounts are kept in the `<backplane-instance>_v2_clients` table:

* `USER`: backplane username

* `PWDHASH`: backplane password (hash)

* `SOURCE_URL`: the client's identifier for the backplane message `source` field

* `REDIRECT_URI`: the client's redirect_uri for the OAuth2 authorization_code grant type

Bus Owner Provisioning API
--------------------------

#### Create or Update Bus Owner Entry

* Endpoint: `/v2/provision/user/update`

* Security: HTTPS POST

Example HTTP API request:

POST `/v2/provision/user/update`
Host: backplanesample.com
Content-Type: application/json

```json
{
  "admin": "admin",
  "secret": "admin",
  "configs": [
    {
        "USER": "busowner1",
        "PWDHASH": "clear_password_1"
    },
    {
        "USER": "busowner2",
        "PWDHASH": "clear_password_2"
    }
  ]
}
```

Example curl command for the above HTTP API request:

        curl -i --data @json_data_file.txt -H "Content-type: application/json" https://backplanesample.com/v2/provision/user/update


#### List All Bus Owners

* Endpoint: `/v2/provision/user/list`

* Security: HTTPS POST

Example HTTP API request:

Content-Type: application/json

```json
{
  "admin": "admin",
  "secret": "admin",
  "entities": []
}
```

Example curl command for the above HTTP API request:

        curl -i --data @json_data_file.txt -H "Content-type: application/json" https://backplanesample.com/v2/provision/user/list

#### Delete Bus Owners

* Endpoint: `/v2/provision/user/delete`

* Security: HTTPS POST

Example HTTP API request:

`Content-Type: application/json`

```json
{
  "admin": "admin",
  "secret": "admin",
  "entities": [ "busowner1", "busowner2" ]
}
```

Example curl command for the above HTTP API request:

        curl -i --data @json_data_file.txt -H "Content-type: application/json" https://backplanesample.com/v1.1/provision/user/delete

Backplane Clients Provisioning API
----------------------------------

In addition to the automated OAuth2-based client registration mechanism described in [Backplane v2.0 Section 6.2] [4],
this API is provided for manual backplane clients registration.

#### Create or Update Backplane Client Entry

* Endpoint: `/v2/provision/client/update`

* Security: HTTPS POST

Example HTTP API request:

Content-Type: application/json

```json
{
  "admin": "admin",
  "secret": "admin",
  "configs": [
    {
        "USER": "client1",
        "PWDHASH": "clear_password_1",
        "SOURCE_URL": "url1",
        "REDIRECT_URI": "uri1"
    },
    {
        "USER": "client2",
        "PWDHASH": "clear_password_2",
        "SOURCE_URL": "url2",
        "REDIRECT_URI": "uri2"
    }
  ]
}
```

Example curl command for the above HTTP API request:

        curl -i --data @json_data_file.txt -H "Content-type: application/json" https://backplanesample.com/v2/provision/client/update


#### List All Backplane Clients

* Endpoint: `/v2/provision/client/list`

* Security: HTTPS POST

Example HTTP API request:

Content-Type: application/json

```json
{
  "admin": "admin",
  "secret": "admin",
  "entities": []
}
```

Example curl command for the above HTTP API request:

        curl -i --data @json_data_file.txt -H "Content-type: application/json" https://backplanesample.com/v2/provision/client/list

#### Delete Backplane Clients

* Endpoint: `/v2/provision/client/delete`

* Security: HTTPS POST

`Content-Type: application/json`

```json
{
  "admin": "admin",
  "secret": "admin",
  "entities": [ "client1", "client2" ]
}
```

Example curl command for the above HTTP API request:

        curl -i --data @json_data_file.txt -H "Content-type: application/json" https://backplanesample.com/v2/provision/client/delete


Bus Provisioning API
--------------------

#### List Buses

Returns a list of Bus Configuration data sets.

Request:

* Endpoint: `/v2/provision/bus/list`

* Security: HTTPS POST

* all entries are required

* the value for the entities entry can be empty, in which case all Backplane Bus configurations are returned

* body format:

```json
{
    "admin": "<adminUsername>",
    "secret": "<adminPassword>",
    "entities": [ "customer1.com", "customer2.net" ]
}
```

Response:

* status 200 for requests with valid format that were processed successfully

* requested bus configurations that were not found will result in a `"ERR_MSG": "NOT_FOUND"` entry

* requested bus configurations that encountered retrieval errors will result in a `"ERR_MSG": "<error_message>"` entry

* body format:

```json
{
    "customer1":
        {
            "BUS_NAME": "customer1",
            "OWNER": "busowner1",
            "RETENTION_TIME_SECONDS": 600,
            "RETENTION_STICKY_TIME_SECONDS": 28800,
        },
    ...
}
```

        curl -i --data @json_data_file.txt -H "Content-type: application/json" https://backplanesample.com/v2/provision/bus/list

#### Delete Bus

Removes the specified bus configuration(s).

Request:

* Endpoint: `/v2/provision/bus/delete`

* Security: HTTPS POST

* all entries are required

* body format:

```json
{
    "admin": "<adminUsername>",
    "secret": "<adminPassword>",
    "entities": [ "customer1.com", "customer2.net" ]
}
```

Response:

* status 200 for requests with valid format that were processed successfully
* an entry is returned for each bus configuration with the status of the delete operation - either "`BACKPLANE_DELETE_SUCCESS`" or an error message if the deletion was not completed successfully
* body format:

```json
{
    "customer1.com": "BACKPLANE_DELETE_SUCCESS",
    "customer2.net": "<delete error message>"
}
```

        curl -i --data @json_data_file.txt -H "Content-type: application/json" https://backplanesample.com/v2/provision/bus/delete

#### Update Buses

Updates Bus configuration data. If a bus in the provided list does not exist, it is added to the Bus Configuration table.

Request:

* Endpoint: `/v2/provision/bus/update`

* Security: HTTPS POST

* all entries are required

* provided Bus configurations must be in the valid format for the specified endpoint type

* body format:

```json
{
    "admin": "<adminUsername>",
    "secret": "<adminPassword>",
    "configs": [
        {
            "BUS_NAME": "customer1",
            "OWNER": "busowner1",
            "RETENTION_TIME_SECONDS": "600",
            "RETENTION_STICKY_TIME_SECONDS": "28800"
        },
            ...
    ]
}
```

Response / success:

* status 200 for requests with valid format that were processed successfully
* an entry is returned for each bus configuration with the status of the update operation - either `"BACKPLANE_UPDATE_SUCCESS"` or an error message if the deletion was not completed successfully.
* body format:

```json
{
    "customer1.com": "BACKPLANE_UPDATE_SUCCESS",
    "customer2.net": "<update error message>"
}
```

Grant Provisioning API
----------------------

In addition to the OAuth2 authorization mechanism described in [Backplane v2.0 Section 6.3] [5],
this API is provided for manual client_credentials grant type authorization.

#### Add Authorization Grant

Adds an non-expiring authorization grant for the provided Backplane client_id(s) for the specified buses.
The grant's issuer is set to the admin username used for this provisioning operation.

* Endpoint: `/v2/provision/grant/add`

* Security: HTTPS POST

* all entries are required

* body format:

```json
{
    "admin": "<adminUsername>",
    "secret": "<adminPassword>",
    "grants":
        {
            "client_id1": "customer_bus_A customer_bus_B ...",
            "client_id2": "customer_bus_X customer_bus_Y ...",
            ...
        }
}
```

* response format:

```json
{
    "client_id1": "GRANT_UPDATE_SUCCESS",
    "client_id2": "<grant error message>",
    ...
}
```

#### Revoke Authorization Grant

Removes the specified buses from all of Backplane client's grants.

* Endpoint: `/v2/provision/grant/revoke`

* Security: HTTPS POST

* all entries are required

* body format:

```json
{
    "admin": "<adminUsername>",
    "secret": "<adminPassword>",
    "grants":
        {
            "client_id1": "customer_bus_A customer_bus_B ...",
            "client_id2": "customer_bus_X customer_bus_Y ...",
            ...
        }
}
```

* response format:

```json
{
    "client_id1": "GRANT_UPDATE_SUCCESS",
    "client_id2": "<grant error message>",
    ...
}
```

#### List Authorization Grants

Lists existing authorization grants for the Backplane client identified by the provided client_id.

* Endpoint: `/v2/provision/grant/list`

* Security: HTTPS POST

* all entries are required

* body format:

```json
{
  "admin": "admin",
  "secret": "admin",
  "entities": [ "client_id1", "client_id2" ]
}
```

* response format:

```json
{
    "client_id1": {
        "grant_id1": "<buses_in_grant>",
        "grant_id2": "<buses_in_grant>",
        ...
    },

    "client_id2": {
        "grant_idA": "<buses_in_grant>",
        "grant_idB": "<buses_in_grant>",
        ...
    },
    ...
}
```

Authorization and Authentication
--------------------------------

#### Authentication

* Endpoint: `/v2/authenticate`

* Security: HTTPS

GET:

 * request: no parameters

 * response: authentication form HTML

POST:

 * request: form submit of authentication credentials

 * response: redirects back to /authorize (or blank page if the bus owner did not hit this endpoint from a /authorize redirect)

Web form where bus owners authenticate before granting authorization acess (via OAuth2) to Backplane clients.

#### Authorization

* Endpoint: `/v2/authorize`

* Security: HTTPS

OAuth2 authorization endpoint handling `authorization_code` grant type. [2]

 * Request1: OAuth2 authorization request.

 * Response1: Web form HTML where an authorization prompt is presented to authenticated bus owners.

 * Request2: Form submit with bus owner's authorization decision.

 * Response2: OAuth2 authorization response, sent via redirect to the Backplane client's redirect_uri

If the bus owner is not yet authenticated, the authorization request is persisted and bound to the user making
the request with a cookie, then the user is redirected to /authenticate where they can prove they are a bus owner.
After authentication they are redirected back to /authorize where the authorization request is retrieved
and the authenticated bus owner can complete the OAuth2 authorization flow.

Metrics
-----------

#### Retrieve Metrics

Backplane server publishes usages statistics.  Interesting data points include the number of channel gets,
posts and how long it takes the server to retrieve a payload.  Metrics exist in memory only, so if the node
is restarted, the metrics will be reset.

Request:

* Endpoint: `/backplane_metrics`
* HTTP method: GET
* Security: access restricted to localhost and white listed IPs

Metric data may optionally be pushed into Graphite.
Metric data will periodically be sent to stdout.
To configure white-listed IPs and Graphite server settings, please see the build instructions.


### Error Responses

status 401 Unauthorized - API authentication failure
status 400 Bad Request - invalid request format / syntax

Each error response body will contain an error message in the following format:

```json
{
    "ERR_MSG": "<message>"
}
```


[1]: http://tools.ietf.org/html/draft-ietf-oauth-v2#section-4.1.3 "Access Token Request"
[2]: http://tools.ietf.org/html/draft-ietf-oauth-v2#section-4.1 "Authorization Code Grant"
[3]: http://tools.ietf.org/html/draft-ietf-oauth-v2#section-6 "Refreshing an Access Token"
[4]: https://sites.google.com/site/backplanespec/documentation/backplane2-0-draft08#client.registration "Backplane v2.0 Section 6.2"
[5]: https://sites.google.com/site/backplanespec/documentation/backplane2-0-draft08#authorization "Backplane v2.0 Section 6.3"
[6]: https://sites.google.com/site/backplanespec/documentation/backplane2-0-draft08#server.api "Backplane v2.0 Section 13"

