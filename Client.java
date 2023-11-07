import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.Scanner;

public class Client {
    DatagramSocket socket;
    static final int RETRY_LIMIT = 4;
    static final int ACK_TIMEOUT = 1000;    /*
     * UTILITY METHODS PROVIDED FOR YOU
     * Do NOT edit the following functions:
     *      exitErr
     *      checksum
     *      checkFile
     *      isCorrupted
     *
     */

    /* exit unimplemented method */
    public void exitErr(String msg) {
        System.out.println("Error: " + msg);
        System.exit(0);
    }

    /* calculate the segment checksum by adding the payload */
    public int checksum(String content, Boolean corrupted) {
        if (!corrupted) {
            int i;
            int sum = 0;
            for (i = 0; i < content.length(); i++)
                sum += (int) content.charAt(i);
            return sum;
        }
        return 0;
    }


    /* check if the input file does exist */
    File checkFile(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            System.out.println("SENDER: File does not exists");
            System.out.println("SENDER: Exit ..");
            System.exit(0);
        }
        return file;
    }


    /*
     * returns true with the given probability
     *
     * The result can be passed to the checksum function to "corrupt" a
     * checksum with the given probability to simulate network errors in
     * file transfer
     */
    public boolean isCorrupted(float prob) {
        double randomValue = Math.random();
        return randomValue <= prob;
    }



    /*
     * The main method for the client.
     * Do NOT change anything in this method.
     *
     * Only specify one transfer mode. That is, either nm or wt
     */

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 5) {
            System.err.println("Usage: java Client <host name> <port number> <input file name> <output file name> <nm|wt>");
            System.err.println("host name: is server IP address (e.g. 127.0.0.1) ");
            System.err.println("port number: is a positive number in the range 1025 to 65535");
            System.err.println("input file name: is the file to send");
            System.err.println("output file name: is the name of the output file");
            System.err.println("nm selects normal transfer|wt selects transfer with time out");
            System.exit(1);
        }

        Client client = new Client();
        String hostName = args[0];
        int portNumber = Integer.parseInt(args[1]);
        InetAddress ip = InetAddress.getByName(hostName);
        File file = client.checkFile(args[2]);
        String outputFile = args[3];
        System.out.println("----------------------------------------------------");
        System.out.println("SENDER: File " + args[2] + " exists  ");
        System.out.println("----------------------------------------------------");
        System.out.println("----------------------------------------------------");
        String choice = args[4];
        float loss = 0;
        Scanner sc = new Scanner(System.in);


        System.out.println("SENDER: Sending meta data");
        client.sendMetaData(portNumber, ip, file, outputFile);

        if (choice.equalsIgnoreCase("wt")) {
            System.out.println("Enter the probability of a corrupted checksum (between 0 and 1): ");
            loss = sc.nextFloat();
        }

        System.out.println("------------------------------------------------------------------");
        System.out.println("------------------------------------------------------------------");
        switch (choice) {
            case "nm":
                client.sendFileNormal(portNumber, ip, file);
                break;

            case "wt":
                client.sendFileWithTimeOut(portNumber, ip, file, loss);
                break;
            default:
                System.out.println("Error! mode is not recognised");
        }


        System.out.println("SENDER: File is sent\n");
        sc.close();
    }


    /*
     * THE THREE METHODS THAT YOU HAVE TO IMPLEMENT FOR PART 1 and PART 2
     *
     * Do not change any method signatures
     */

    /* TODO: send metadata (file size and file name to create) to the server
     * outputFile: is the name of the file that the server will create
     */
    public void sendMetaData(int portNumber, InetAddress IPAddress, File file, String outputFile) {
        MetaData metadata = new MetaData();
        metadata.setName(outputFile);
        metadata.setSize((int) file.length());
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream outputStream = new ObjectOutputStream(bos);
            outputStream.writeObject(metadata);
            socket = new DatagramSocket();
            DatagramPacket datagramPacket = new DatagramPacket(bos.toByteArray(), bos.size(), IPAddress, portNumber);
            socket.send(datagramPacket);
            socket.close();

            System.out.println("SENDER: meta data is sent: (file name, size): (" + metadata.getName() + ", " + metadata.getSize() + ")");
        } catch (IOException e) {
            exitErr(e.toString());
        }
    }


    /* TODO: Send the file to the server without corruption*/
    public void sendFileNormal(int portNumber, InetAddress IPAddress, File file) {
        int size = 4;
        System.out.println("SENDER: Start Sending File");
        System.out.println("----------------------------------------------------");
        try (FileInputStream fileContent = new FileInputStream(file); BufferedInputStream bis = new BufferedInputStream(fileContent)) {
            socket = new DatagramSocket();
            int total_segments = 0;
            for (int i = 0; bis.available() != 0; i++) {
                byte sdata[] = new byte[Math.min(size, bis.available())];
                bis.read(sdata, 0, sdata.length);
                sendSegmentData(portNumber, IPAddress, sdata, i);

                isValidAck(i);
                total_segments++;
                System.out.println("----------------------------------------------------");
            }
            System.out.println("Total segments " + total_segments);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            exitErr(e.toString());
        } finally {
            socket.close();
        }
    }

    private boolean isValidAck(int i) throws IOException, ClassNotFoundException {

        try {
            System.out.println("SENDER: Waiting for an ack");
            byte[] incomingData = new byte[1024];
            DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
            socket.receive(incomingPacket);
            byte[] data = incomingPacket.getData();
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            ObjectInputStream is = new ObjectInputStream(in);
            Segment dataSeg = (Segment) is.readObject();
            System.out.println("SENDER: ACK sq= " + dataSeg.getSq() + " RECIEVED.");
            return dataSeg.getType() == SegmentType.Ack && dataSeg.getSq() == i;
        } catch (SocketTimeoutException e) {
            return false;
        }
    }

    private void sendSegmentData(int portNumber, InetAddress IPAddress, byte[] sdata, int i) throws IOException {
        sendSegmentData(portNumber, IPAddress, sdata, i, 0, false);
    }

    private void sendSegmentData(int portNumber, InetAddress IPAddress, byte[] sdata, int i, float loss, boolean isRetry) throws IOException {
        boolean isCorrupted = isCorrupted(loss);
        Segment segment = new Segment();
        String segmentString = new String(sdata);
        segment.setPayLoad(segmentString);
        segment.setSize(segmentString.length());
        segment.setChecksum(checksum(segmentString, isCorrupted));
        segment.setSq(i);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(4);
        ObjectOutputStream outputStream = new ObjectOutputStream(bos);
        outputStream.writeObject(segment);
        if (isCorrupted) {
            System.out.println("SENDER: segment: sq:" + segment.getSq() + " has corrupted");
        }
        if (isRetry) {
            System.out.println("SENDER: Resending segment: sq:" + segment.getSq() + ", size:" + segment.getSize() + ", checksum: " + segment.getChecksum() + ", content: (" + segment.getPayLoad() + ")");
        } else {
            System.out.println("SENDER: Sending segment: sq:" + segment.getSq() + ", size:" + segment.getSize() + ", checksum: " + segment.getChecksum() + ", content: (" + segment.getPayLoad() + ")");
        }
        DatagramPacket datagramPacket = new DatagramPacket(bos.toByteArray(), bos.size(), IPAddress, portNumber);
        socket.send(datagramPacket);
    }

    /* TODO: This function is essentially the same as the sendFileNormal function
     *      except that it resends data segments if no ACK for a segment is
     *      received from the server.*/
    public void sendFileWithTimeOut(int portNumber, InetAddress IPAddress, File file, float loss) {
        int size = 4;

        int total_segments = 0;
        try (FileInputStream fileContent = new FileInputStream(file)) {
            socket = new DatagramSocket();
            socket.setSoTimeout(ACK_TIMEOUT);
            try (fileContent; BufferedInputStream bis = new BufferedInputStream(fileContent)) {
                for (int i = 0; bis.available() != 0; i++) {
                    byte sdata[] = new byte[Math.min(size, bis.available())];
                    bis.read(sdata, 0, sdata.length);
                    sendSegmentData(portNumber, IPAddress, sdata, i, loss, false);
                    total_segments++;
                    boolean isValidAck = isValidAck(i);
                    System.out.println("----------------------------------------------------");
                    int retryCount = 1;
                    while (!isValidAck) {
                        if (retryCount >= RETRY_LIMIT) {
                            throw new RuntimeException("Total retries excited");
                        }
                        sendSegmentData(portNumber, IPAddress, sdata, i, loss, true);
                        total_segments++;
                        retryCount++;
                        isValidAck = isValidAck(i);
                        System.out.println("----------------------------------------------------");
                    }
                }
            } catch (RuntimeException e) {
                System.out.println("Total segments " + total_segments);
                exitErr(e.getMessage());
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            exitErr(e.toString());
        } finally {
            System.out.println("Total segments " + total_segments);
            socket.close();
        }
    }


}