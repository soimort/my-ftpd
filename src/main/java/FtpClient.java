import java.io.*;
import java.net.*;
import java.util.regex.*;
import org.apache.commons.cli.*;

/**
 * FtpClient class.
 *
 * @author  Mort Yao <mort.yao@gmail.com>
 */
public class FtpClient {
    /**
     * Host address for FTP (default: localhost).
     */
    private static String host = "localhost";
    
    /**
     * Port for FTP (default: 21).
     */
    private static int controlPort = 21;
    
    /**
     * Socket for FTP.
     */
    private static Socket connection;
    private static BufferedReader in;
    private static OutputStream out;
    private static PrintStream pout;
    
    /**
     * Is server data trasmission in passive mode.
     */
    private static boolean isServerPassive = false;
    
     /**
     * Socket for FTP data. Used only by server-active mode.
     */
    private static ServerSocket dataSocket;
    
    /**
     * Socket for FTP data. Used by both modes.
     */
    private static Socket dataConnection;
    
    /**
     * Sends an FTP request.
     *
     * @param  request      request string
     * @throws IOException
     */
    private static void sendRequest(String request) throws IOException {
        pout.print(request + "\r\n");
        out.flush();
    }
    
    /**
     * Reads an FTP response.
     *
     * @return              response string from the server
     * @throws IOException
     */
    private static String readResponse() throws IOException {
        String data = "", line = in.readLine();
        data += line + "\r\n";
        while (line.matches("^\\d+-.*")) {
            line = in.readLine();
            data += line + "\r\n";
        }
        return data;
    }
    
    /**
     * Receives FTP data and returns it as a string.
     *
     * @return              received data
     * @throws IOException
     */
    private static String receiveData() throws IOException {
        if (!isServerPassive)
            dataConnection = dataSocket.accept();
        BufferedReader dataIn = new BufferedReader(new InputStreamReader(dataConnection.getInputStream()));
        String data = "", line;
        while ((line = dataIn.readLine()) != null)
            data += line + "\r\n";
        dataConnection.close();
        return data;
    }
    
    /**
     * Receives FTP data and saves to a file.
     *
     * @param  file         file to save
     * @throws IOException
     */
    private static void receiveData(File file) throws IOException {
        if (!isServerPassive)
            dataConnection = dataSocket.accept();
        InputStream dataStream = dataConnection.getInputStream();
        OutputStream fout = new BufferedOutputStream(new FileOutputStream(file));
        byte[] buffer = new byte[1024];
        while (dataStream.available() > 0)
            fout.write(buffer, 0, dataStream.read(buffer));
        fout.flush();
        dataConnection.close();
    }
    
    /**
     * Handles an FTP request.
     *
     * @param  request      FTP request string
     * @throws IOException
     */
    private static void handleRequest(String request) throws IOException {
        String command[] = request.split("\\s+");
        sendRequest(request);
        String response = readResponse();
        System.out.print(response);
        int code = Integer.parseInt(response.split("[\\s-]+")[0]);
        
        switch (command[0]) {
        case "PORT": { // PORT <SP> <host-port> <CRLF>
            if (code == 200) {
                isServerPassive = false;
                String args[] = command[1].split(",");
                String dataHost = args[0] + "." + args[1] + "." + args[2] + "." + args[3];
                int dataPort = Integer.parseInt(args[4]) * 256 + Integer.parseInt(args[5]);
                dataSocket = new ServerSocket(dataPort);
            }
            break;
        }
        case "EPRT": { // EPRT <SP> <D> <net-port> <D> <net-addr> <D> <tcp-port> <D> <CRLF>
            if (code == 200) {
                isServerPassive = false;
                String args[] = command[1].split("\\|");
                String dataHost = args[2];
                int dataPort = Integer.parseInt(args[3]);
                dataSocket = new ServerSocket(dataPort);
            }
            break;
        }
        case "PASV": { // PASV <CRLF>
            if (code == 227) {
                isServerPassive = true;
                String pattern = ".*(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+).*";
                Matcher m = Pattern.compile(pattern).matcher(response);
                if (m.find()) {
                    int dataPort = Integer.parseInt(m.group(5)) * 256 + Integer.parseInt(m.group(6));
                    dataConnection = new Socket(host, dataPort);
                }
            }
            break;
        }
        case "EPSV": { // EPSV <CRLF>
            if (code == 229) {
                isServerPassive = true;
                String pattern = ".*\\|\\|\\|(\\d+)\\|";
                Matcher m = Pattern.compile(pattern).matcher(response);
                if (m.find()) {
                    int dataPort = Integer.parseInt(m.group(1));
                    dataConnection = new Socket(host, dataPort);
                }
            }
            break;
        }
        case "LIST": { // LIST [<SP> <pathname>] <CRLF>
            if (code == 150) {
                System.out.print(receiveData());
                System.out.print(readResponse());
            }
            break;
        }
        case "RETR": { // RETR <SP> <pathname> <CRLF>
            if (code == 150) {
                String[] filename = command[1].split("/");
                receiveData(new File(filename[filename.length - 1]));
                System.out.print(readResponse());
            }
            break;
        }
        case "QUIT": { // QUIT <CRLF>
            if (connection != null)
                connection.close();
            System.exit(0);
            break;
        }
        }
    }
    
    /**
     * Main method.
     */
    public static void main(String[] args) {
        Options options = new Options();
        
        // Option: --help
        Option optHelp = new Option("H", "help", false, "print this message");
        options.addOption(optHelp);

        // Option: --host=<host>
        Option optHost = OptionBuilder.withArgName("HOST")
            .withLongOpt("host")
            .withDescription("connect to a given host (default: " + host + ")")
            .hasArg()
            .create("h");
        options.addOption(optHost);
        
        // Option: --port=<PORT>
        Option optPort = OptionBuilder.withArgName("PORT")
            .withLongOpt("port")
            .withDescription("connect to a given port (default: " + controlPort + ")")
            .hasArg()
            .create("p");
        options.addOption(optPort);
        
        CommandLineParser parser = new GnuParser();
        try {
            CommandLine line = parser.parse(options, args);
            
            if (line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("my-ftp [OPTION...] [HOST [PORT]]", options);
                System.exit(-1);
            }
            
            if (line.hasOption("host"))
                host = line.getOptionValue("host");
            if (line.hasOption("port"))
                controlPort = Integer.parseInt(line.getOptionValue("port"));
            if (line.getArgs().length > 0)
                host = line.getArgs()[0];
            if (line.getArgs().length > 1)
                controlPort = Integer.parseInt(line.getArgs()[1]);
        } catch (ParseException e) {
            System.err.println("Command-line error: " + e.getMessage());
            System.exit(-1);
        }
        
        try {
            connection = new Socket(host, controlPort);
            in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            out = new BufferedOutputStream(connection.getOutputStream());
            pout = new PrintStream(out);
        } catch (IOException e) {
            System.err.println("Socket error: " + e.getMessage());
            System.exit(-1);
        }
        System.out.println("my-ftp connected to " + host + ":" + controlPort + ".");
        
        // Handles the first response (service ready)
        try {
            System.out.print(readResponse());
        } catch (IOException e) {
        }
        
        Console console = System.console();
        String request = null, response;
        while (true) {
            try {
                request = console.readLine("%% ");
                handleRequest(request);
            } catch (IOException e) {
                System.err.println("FTP error: " + e.getMessage());
            }
        }
    }
}
