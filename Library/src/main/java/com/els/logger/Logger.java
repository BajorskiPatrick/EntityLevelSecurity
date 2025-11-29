package com.els.logger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

public class Logger {
    private PrintWriter writer;
    private static Logger instance;
    private volatile boolean isClosed = false;

    private Logger() {
        try {
            // Open a file in append mode
            writer = new PrintWriter(new FileWriter("application.log", true));

            // Register shutdown hook to automatically close logger when application exits
            Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }

    public static Logger getInstance() {
        if (instance == null) {
            /*Thread safe implementation*/
            synchronized (Logger.class) {
                if (instance == null) {
                    instance = new Logger();
                }
            }
        }
        return instance;
    }

    public void log(String message) {
        if (isClosed || writer == null) {
            System.err.println("Logger is closed. Cannot log: " + message);
            return;
        }
        String fullMessage = "[" + LocalDateTime.now() + "] [ELS] " + message;
        printLog(fullMessage);
        writer.println(fullMessage);
        writer.flush();
    }

    public void logException(Exception exception){
        if (isClosed || writer == null) {
            System.err.println("Logger is closed. Cannot log exception: " + exception.getMessage());
            return;
        }
        String fullMessage = "[" + LocalDateTime.now() + "] " + exception.getMessage();
        writer.println(fullMessage);
        writer.flush();
    }

    private void printLog(String message){
        System.out.println(message);
    }

    public synchronized void close() {
        if (!isClosed && writer != null) {
            writer.flush();
            writer.close();
            isClosed = true;
        }
    }
    public void clearLogs(){
        try{
            PrintWriter overWriter = new PrintWriter(new FileWriter("application.log", false));
            overWriter.write("");
            overWriter.flush();
            overWriter.close();
        }
        catch(IOException e){
            System.err.println("Failed to clear logs: " + e.getMessage());
        }

    }
}
