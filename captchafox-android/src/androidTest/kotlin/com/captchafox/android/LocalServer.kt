package com.captchafox.android

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** An HTTP server on the device that answers every request with the given page and records the request lines. */
class LocalServer(private val page: (port: Int) -> String) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val requestLines = mutableListOf<String>()

    val port: Int = server.localPort

    /** The first lines of the requests so far, such as `POST /away HTTP/1.1`. */
    val requests: List<String>
        get() = synchronized(requestLines) { requestLines.toList() }

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                // The web view may drop a connection while it answers, such as when the page loads again; an exception
                // here would end the test process.
                runCatching { answer(socket) }
            }
        }
    }

    private fun answer(socket: Socket) {
        socket.use {
            val reader = socket.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return@use
            synchronized(requestLines) { requestLines += requestLine }
            while (!reader.readLine().isNullOrEmpty()) {
                // The headers, which the page does not depend on.
            }
            val body = page(port).toByteArray()
            val head = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(head.toByteArray())
                write(body)
                flush()
            }
        }
    }

    override fun close() {
        server.close()
    }
}
