# Pulsar Manager

Once the Docker containers are up, you can access the Web UI at http://localhost:9527. Before being able to login, we need to create a user and password, which requires authentication.

First, get a token (note: this is Fish shell syntax, not bash).

```fish
set CSRF_TOKEN (curl http://localhost:7750/pulsar-manager/csrf-token)
```

Next, set up a superuser. The password must have at least 8 characters.

```
curl \
            -H 'X-XSRF-TOKEN: $CSRF_TOKEN' \
            -H 'Cookie: XSRF-TOKEN=$CSRF_TOKEN;' \
            -H "Content-Type: application/json" \
            -X PUT http://localhost:7750/pulsar-manager/users/superuser \
            -d '{"name": "admin", "password": "apachepulsar", "description": "test", "email": "username@test.org"}'
```

Once in the UI, we log-in with `admin` and `apachepulsar`. Then we create a new environment named "localhost" pointing at http://pulsar:8080.
