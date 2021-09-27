function wsPortsHook(app) {
  app.ports.connect.subscribe(function(url) {
    var socket = new WebSocket(url);
    var timerId = 0;

    function keepAlive() {
      var timeout = 20000;
      if (socket.readyState == socket.OPEN) {
        console.log('[info] Sending heartbeat 💓');
        socket.send('{ "Heartbeat": {} }');
      }
      timerId = setTimeout(keepAlive, timeout);
    }

    function cancelKeepAlive() {
      if (timerId) {
        clearTimeout(timerId);
      }
    }

    app.ports.send.subscribe(function(message) {
      console.log(`[info] sending message ${message}, ws status: ${socket.readyState}`);
      socket.send(message);
    });

    socket.onmessage = function(event) {
      console.log(`[info] receiving message ${event.data}, ws status: ${socket.readyState}`);
      app.ports.receive.send(event.data);
    };

    socket.onopen = function(event) {
      keepAlive();
    };

    socket.onclose = function(event) {
      if (event.wasClean) {
        console.log(`[close] Connection closed cleanly: code=${event.code}, reason=${event.reason}`);
      } else {
        console.log(`[close] Connection closed abrubtly: code=${event.code}, reason=${event.reason}`);
      }

      cancelKeepAlive();

      app.ports.receive.send('{ "SocketClosed": {} }');
    };

    socket.onerror = function(error) {
      console.log(`[error] ${error.message}`);
    };
  });
}
