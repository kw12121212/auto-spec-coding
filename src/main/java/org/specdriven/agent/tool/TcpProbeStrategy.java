package org.specdriven.agent.tool;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP connect probe strategy.
 * <p>
 * Attempts a TCP connection to the target host:port. Returns true if the
 * connection is established within the probe's retry interval, false otherwise.
 */
public class TcpProbeStrategy implements ProbeStrategy {

    @Override
    public boolean probe(ReadyProbe probe) {
        try (Socket socket = new Socket()) {
            int timeoutMs = (int) Math.min(probe.retryInterval().toMillis(), Integer.MAX_VALUE);
            socket.connect(new InetSocketAddress(probe.host(), probe.port()), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
