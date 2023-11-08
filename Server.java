import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

public class Server {
    DatagramSocket socket = null;
    long totalBytes = 0;
    String outputFileName;

    /*
     * UTILITY METHODS PROVIDED FOR YOU
     * Do NOT edit the following functions:
     *      exitErr
     *      checksum
     *      isLost
     *      ReceiveMetaData
     *      receiveFileNormal
     */

    /* exit unimplemented method */
    public void exitErr(String msg) {
        System.out.println("Error: " + msg);
        System.exit(0);
    }

    /* calculate the segment checksum by adding the payload */
    public int checksum(String content) {
        int i;
        int sum = 0;
        for (i = 0; i < content.length(); i++)
            sum += (int) content.charAt(i);
        return sum;
    }


    /*
     * returns true with the given probability 	 *
     */
    public boolean isLost(float prob) {
        double randomValue = Math.random();
        return randomValue <= prob;
    }


    /* Received meta data (expected size and name to write output to) from the client */
    public void ReceiveMetaData() throws IOException, InterruptedException {
        MetaData metaData = new MetaData();

        byte[] receive = new byte[65535];
        DatagramPacket receiveMetaData = new DatagramPacket(receive, receive.length);
        socket.receive(receiveMetaData);
        byte[] data = receiveMetaData.getData();
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is = new ObjectInputStream(in);
        try {
            metaData = (MetaData) is.readObject();

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        totalBytes = metaData.getSize();
        outputFileName = metaData.getName();
        System.out.println("SERVER: Meta info are received successfully: (file name, size): ( " + metaData.getName() + ", " + metaData.getSize() + ")");

    }


    /* Receive the file in chuncks from the client */
    public void receiveFileNormal() throws IOException {
        FileWriter myWriter = new FileWriter(outputFileName);
        int currentTotal = 0;
        byte[] incomingData = new byte[1024];
        Segment dataSeg = new Segment();

        /* while still receiving segments */
        while (currentTotal < totalBytes) {
            DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
            //receive from the client
            socket.receive(incomingPacket);

            byte[] data = incomingPacket.getData();
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            ObjectInputStream is = new ObjectInputStream(in);


            try {
                dataSeg = (Segment) is.readObject();
                System.out.println("SERVER: A Segment with sq " + dataSeg.getSq() + " is received: ");
                System.out.println("\tINFO: size " + dataSeg.getSize() + ", checksum " + dataSeg.getChecksum() + ", content (" + dataSeg.getPayLoad() + ")");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }


            InetAddress IPAddress = incomingPacket.getAddress();
            int port = incomingPacket.getPort();

            int x = checksum(dataSeg.getPayLoad());

            //If the calculated checksum is same as that of received checksum then send corrosponding ack
            if (x == dataSeg.getChecksum()) {
                System.out.println("SERVER: Calculated checksum is " + x + "  VALID");
                Segment ackSeg = new Segment();

                /* Prepare the Ack segment */
                ackSeg.setSq(dataSeg.getSq());
                ackSeg.setType(SegmentType.Ack);
                System.out.println("SERVER: Sending an ACK with sq " + ackSeg.getSq());

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ObjectOutputStream os = new ObjectOutputStream(outputStream);
                os.writeObject(ackSeg);
                byte[] dataAck = outputStream.toByteArray();
                DatagramPacket replyPacket = new DatagramPacket(dataAck, dataAck.length, IPAddress, port);

                /* Send the Ack segment */
                socket.send(replyPacket);

                /* write the payload of the data segment to output file */
                myWriter.write(dataSeg.getPayLoad());
                currentTotal = currentTotal + dataSeg.getSize();

                System.out.println("\t\t>>>>>>> NETWORK: ACK is sent successfully <<<<<<<<<");
                System.out.println("------------------------------------------------");
                System.out.println("------------------------------------------------");

            } else {
                System.out.println("SERVER: Calculated checksum is " + x + "  INVALID");
                System.out.println("SERVER: Not sending any ACK ");
                System.out.println("*************************** ");
            }

        }

        System.out.println("SERVER: File copying complete\n");
        myWriter.close();
    }


    /*
     * The main method for the server.
     * Do NOT change anything in this method.
     *
     * Only specify one transfer mode. That is, either nm or wt
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 2) {
            System.err.println("Usage: java Server <port number> <nm|wl>");
            System.err.println("port number: is a positive number in the range 1025 to 65535");
            System.err.println("nm selects normal transfer|wl selects transfer with lost Ack");
            System.exit(1);
        }

        Server server = new Server();
        String choice = args[1];
        float loss = 0;
        server.socket = new DatagramSocket(Integer.parseInt(args[0]));

        Scanner sc = new Scanner(System.in);
        if (choice.equalsIgnoreCase("wl")) {
            System.out.println("Enter the probability of a lost ack (between 0 and 1): ");
            loss = sc.nextFloat();
        }

        System.out.println("SERVER: binding ... Ready to receive meta info from the client ");
        System.out.println("------------------------------------------------------------------");
        System.out.println("------------------------------------------------------------------");
        server.ReceiveMetaData();

        System.out.println("------------------------------------------------------------------");
        System.out.println("------------------------------------------------------------------");
        System.out.println("SERVER: Waiting for the actual file ..");
        System.out.println("------------------------------------------------------------------");

        switch (choice) {
            case "nm":
                server.receiveFileNormal();
                break;

            case "wl":
                server.receiveFileWithAckLost(loss);
                break;
            default:
                System.out.println("Error! mode is not recognised");
        }

        sc.close();

    }


    /*
     * THE THREE METHOD THAT YOU HAVE TO IMPLEMENT FOR PART 3
     *
     * Do not change the method signature
     */
    public void receiveFileWithAckLost(float loss) throws IOException {
        FileWriter myWriter = new FileWriter(outputFileName);
        int currentTotal = 0;
        byte[] incomingData = new byte[1024];
        Segment dataSeg = new Segment();
        int lastSegment = -1;

        /* while still receiving segments */
        while (currentTotal < totalBytes) {
            DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
            //receive from the client
            socket.receive(incomingPacket);

            byte[] data = incomingPacket.getData();
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            ObjectInputStream is = new ObjectInputStream(in);


            try {
                dataSeg = (Segment) is.readObject();
                System.out.println("SERVER: A Segment with sq " + dataSeg.getSq() + " is received: ");
                System.out.println("\tINFO: size " + dataSeg.getSize() + ", checksum " + dataSeg.getChecksum() + ", content (" + dataSeg.getPayLoad() + ")");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }


            InetAddress IPAddress = incomingPacket.getAddress();
            int port = incomingPacket.getPort();

            int x = checksum(dataSeg.getPayLoad());

            //If the calculated checksum is same as that of received checksum then send corrosponding ack
            if (x == dataSeg.getChecksum()) {
                System.out.println("SERVER: Calculated checksum is " + x + "  VALID");
                Segment ackSeg = new Segment();

                /* Prepare the Ack segment */
                ackSeg.setSq(dataSeg.getSq());
                ackSeg.setType(SegmentType.Ack);
                System.out.println("SERVER: Sending an ACK with sq " + ackSeg.getSq());

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ObjectOutputStream os = new ObjectOutputStream(outputStream);
                os.writeObject(ackSeg);
                byte[] dataAck = outputStream.toByteArray();
                DatagramPacket replyPacket = new DatagramPacket(dataAck, dataAck.length, IPAddress, port);
                boolean lost = isLost(loss);
                System.out.println("SERVER: LOST" + lost);
                if (!lost) {
                    /* Send the Ack segment */
                    socket.send(replyPacket);
                }
                if (lastSegment == dataSeg.getSq()) {
                    System.out.println("SERVER: Duplicate sequence number received. resending ack");
                } else {
                    /* write the payload of the data segment to output file */
                    myWriter.write(dataSeg.getPayLoad());
                    currentTotal = currentTotal + dataSeg.getSize();
                    lastSegment = dataSeg.getSq();
                }
                System.out.println("\t\t>>>>>>> NETWORK: ACK is sent successfully <<<<<<<<<");
                System.out.println("------------------------------------------------");
                System.out.println("------------------------------------------------");

            } else {
                System.out.println("SERVER: Calculated checksum is " + x + "  INVALID");
                System.out.println("SERVER: Not sending any ACK ");
                System.out.println("*************************** ");
            }

        }

        System.out.println("SERVER: File copying complete\n");
        myWriter.close();

    }
}