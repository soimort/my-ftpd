import java.io.*;
import java.net.*;
import java.util.*;
import org.apache.commons.cli.*;

/**
 * FtpServer class.
 *
 * @author  Mort Yao <mort.yao@gmail.com>
 *
 * @see     FtpServerConnection
 */
public class FtpServer {
    /**
     * Port for FTP (default: 21).
     */
    private static int controlPort = 21;
    
    /**
     * FTP home path on the server (default: current dir).
     */
    private static String ftpHome = System.getProperty("user.dir");
    
    /**
     * Main method.
     */
    public static void main(String[] args) {
        Options options = new Options();
        
        // Option: --help
        Option optHelp = new Option("H", "help", false, "print this message");
        options.addOption(optHelp);
        
        // Option: --ftp-home=<FTP_HOME>
        Option optFtpHome = OptionBuilder.withArgName("FTP_HOME")
            .withLongOpt("ftp-home")
            .withDescription("use a given path as FTP home dir (default: current dir)")
            .hasArg()
            .create("f");
        options.addOption(optFtpHome);
        
        // Option: --port=<PORT>
        Option optPort = OptionBuilder.withArgName("PORT")
            .withLongOpt("port")
            .withDescription("use a given port (default: " + controlPort + ")")
            .hasArg()
            .create("p");
        options.addOption(optPort);
        
        CommandLineParser parser = new GnuParser();
        try {
            CommandLine line = parser.parse(options, args);
            
            if (line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("my-ftpd [OPTION...]", options);
                System.exit(-1);
            }
            
            if (line.hasOption("ftp-home"))
                ftpHome = line.getOptionValue("ftp-home");
            if (line.hasOption("port"))
                controlPort = Integer.parseInt(line.getOptionValue("port"));
        } catch (ParseException e) {
            System.err.println("Command-line error: " + e.getMessage());
            System.exit(-1);
        }
        
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(controlPort);
        } catch (IOException e) {
            System.err.println("Socket error: " + e.getMessage());
            System.exit(-1);
        }
        System.out.println("my-ftpd running on port " + controlPort);
        
        while (true) {
            try {
                Socket connection = socket.accept();
                Thread thread = new Thread(new FtpServerConnection(ftpHome, connection));
                thread.start();
            } catch (IOException e) {
                System.err.print("FTP error: " + e.getMessage());
            }
        }
    }
}
