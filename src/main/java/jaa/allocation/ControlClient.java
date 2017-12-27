package jaa.allocation;

import java.io.IOException;
import java.net.*;

/** Client for {@link jaa.allocation.ControlServer} */
public class ControlClient {
    public static void main(String ... argv) throws IOException {
        DatagramSocket serverSocket = new DatagramSocket(0);

        if(argv[0].equals("start"))
        {
            String sendString = "start";
            byte[] sendData = sendString.getBytes("UTF-8");
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                    InetAddress.getLocalHost(), ControlServer.CONTROL_PORT);
            serverSocket.send(sendPacket);
        }
        else if(argv[0].equals("stop"))
        {
            String sendString = "stop";
            byte[] sendData = sendString.getBytes("UTF-8");
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                    InetAddress.getLocalHost(), ControlServer.CONTROL_PORT);
            serverSocket.send(sendPacket);
        }
    }
}
