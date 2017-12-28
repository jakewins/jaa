package jaa.internal.allocation;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;

/** Tiny UDP server for starting/stopping the sampling. */
class ControlServer implements Runnable
{
    public static final int CONTROL_PORT = 9876;
    private volatile boolean run = true;

    private final Runnable onStartCommand;
    private final Runnable onStopCommand;

    ControlServer(Runnable onStartCommand, Runnable onStopCommand) {
        this.onStartCommand = onStartCommand;
        this.onStopCommand = onStopCommand;
    }

    public void stop()
    {
        this.run = false;
    }

    @Override
    public void run() {
        try {
            DatagramSocket serverSocket = new DatagramSocket(CONTROL_PORT);
            byte[] receiveData = new byte[1024];
            while (run)
            {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);
                String command = new String(Arrays.copyOfRange(receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength()));
                switch(command.trim())
                {
                    case "start":
                        this.onStartCommand.run();
                        break;
                    case "stop":
                        this.onStopCommand.run();
                        break;
                    default:
                        System.err.println("[Control] unknown command: " + command);

                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
