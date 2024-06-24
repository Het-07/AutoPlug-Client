/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.network.online.connections.ConAutoPlugConsoleSend;
import com.osiris.autoplug.client.tasks.BeforeServerStartupTasks;
import com.osiris.autoplug.client.utils.*;
import com.osiris.autoplug.client.utils.io.AsyncInputStream;
import com.osiris.dyml.SmartString;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.*;
import com.osiris.jlib.logger.AL;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.fusesource.jansi.Ansi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;


public final class Server {

    private static final AtomicBoolean isKill = new AtomicBoolean(false);
    @Nullable
    public static AsyncInputStream ASYNC_SERVER_IN;
    private static Process process;
    private static Thread threadServerAliveChecker;
    private static boolean colorServerLog;

    public static File getServerExecutable() throws NotLoadedException, YamlReaderException, YamlWriterException, IOException, IllegalKeyException, DuplicateKeyException, IllegalListException {
        File serverExe = null;
        while (true) {
            serverExe = new UtilsJar().determineServerJar();
            if (serverExe == null) {
                serverExe = new FileManager().serverExecutable(GD.WORKING_DIR);
                if (serverExe == null || !serverExe.exists()) {
                    AL.info("Failed to determine the server executable and start-command.");
                    AL.info("Examples: 'java -jar server.jar' or '.\\server.exe'.");
                    AL.info("Please enter your start-command and press enter:");
                    GeneralConfig generalConfig = new GeneralConfig();
                    generalConfig.server_start_command.setValues(new Scanner(System.in).nextLine());
                    generalConfig.save();
                } else {
                    if (serverExe.getName().endsWith(".jar")) {
                        GeneralConfig generalConfig = new GeneralConfig();
                        try {
                            generalConfig.lockFile();
                            generalConfig.server_start_command.setValues("java -jar \"" + serverExe.getAbsolutePath() + "\"");
                            generalConfig.save();
                        } catch (Exception e) {
                            generalConfig.unlockFile();
                            throw e;
                        } finally {
                            generalConfig.unlockFile();
                        }
                        break;
                    } else {
                        AL.info("Determined the server executable but not the start-command.");
                        AL.info("Executable: " + serverExe.getAbsolutePath());
                        AL.info("Examples: 'java -jar server.jar' or '.\\server.exe'.");
                        AL.info("Please enter your start-command and press enter:");
                        GeneralConfig generalConfig = new GeneralConfig();
                        try {
                            generalConfig.lockFile();
                            generalConfig.server_start_command.setValues(new Scanner(System.in).nextLine());
                            generalConfig.save();
                        } catch (Exception e) {
                            generalConfig.unlockFile();
                            throw e;
                        } finally {
                            generalConfig.unlockFile();
                        }
                    }
                }
            } else {
                if (!serverExe.exists())
                    AL.warn("Note that the server executable \"" + serverExe.getName() + "\" doesn't seem to exist yet, which may cause issues. Full path: " + serverExe.getAbsolutePath());
                break;
            }
        }
        return serverExe;
    }

    public static synchronized void start() {
        try {
            try {
                colorServerLog = new LoggerConfig().color_server_log.asBoolean();
            } catch (Exception e) {
                AL.warn(e);
            }

            if (isRunning()) throw new Exception("Server already running!");

            // Runs all processes before starting the server
            new BeforeServerStartupTasks();

            // Find server jar
            File serverExe = getServerExecutable();
            if (serverExe == null || !serverExe.exists())
                throw new Exception("Failed to find your server executable! " +
                        "Please check your config, you may need to specify its name/path! " +
                        "Searched dir: '" + GD.WORKING_DIR + "'");

            AL.info("Starting server: " + serverExe.getName());
            createProcess();
            AL.debug(Server.class, "process: " + process);
            AL.debug(Server.class, "ASYNC_SERVER_IN: " + ASYNC_SERVER_IN);
        } catch (Exception e) {
            AL.warn("Failed to start server: " + e.getMessage(), e);
        }
    }

    public static void restart() {
        //Before starting make backups and check for updates
        AL.info("Restarting server...");
        try {
            stop();
            start();
        } catch (Exception e) {
            AL.warn(e);
        }
    }

    /**
     * Blocks until the server was stopped.
     */
    public static synchronized void stop() throws IOException, InterruptedException, YamlWriterException, NotLoadedException, IllegalKeyException, DuplicateKeyException, YamlReaderException, IllegalListException {

        AL.info("Stopping server...");
        AL.debug(Server.class, "process: " + process);
        AL.debug(Server.class, "ASYNC_SERVER_IN: " + ASYNC_SERVER_IN);

        if (isRunning()) {
            YamlSection stopCommand = new GeneralConfig().server_stop_command;
            List<SmartString> values = stopCommand.getValues();
            if (values.isEmpty()) {
                AL.warn("No stop command provided in " + new UtilsLists().toString(stopCommand.getKeys()));
                return;
            }
            for (SmartString v : values) {
                AL.debug(Server.class, "Stopping server with command: \"" + v.asString() + "\"");
                submitCommand(v.asString());
            }
            int maxSeconds = 60 * 10;
            int seconds = 0;
            boolean inKillMode = false;
            while (Server.isRunning()) {
                Thread.sleep(1000);
                seconds++;
                if (seconds >= maxSeconds) {
                    if (inKillMode)
                        throw new IOException("Failed to stop and kill server (waited 20 minutes in total).");
                    inKillMode = true;
                    AL.warn("10 minutes have passed and the server is still running, killing it...");
                    kill();
                }
            }

            ASYNC_SERVER_IN = null;
        } else {
            AL.warn("Server not running!");
        }

    }

    /**
     * Blocks until server was killed.
     */
    public static synchronized boolean kill() {
        isKill.set(true);
        AL.info("Killing server!");
        try {

            if (isRunning()) {
                process.destroyForcibly().waitFor();
            } else {
                AL.warn("Server is not running!");
            }

            int maxSeconds = 60 * 10;
            int seconds = 0;
            while (Server.isRunning()) {
                Thread.sleep(1000);
                seconds++;
                if (seconds >= maxSeconds) {
                    throw new RuntimeException("Failed to kill server (waited 10 minutes).");
                }
            }
            AL.info("Server killed!");
            //isKill.set(false); // Gets set to false in thread alive checker
            return true;
        } catch (InterruptedException e) {
            AL.warn(e);
            //isKill.set(false);
            return false;
        }
    }

    public static boolean isRunning() {
        return process != null && process.isAlive();
    }

    private static void createProcess() throws Exception {
        GeneralConfig config = new GeneralConfig();
        String startCommand = config.server_start_command.asString();

        // 1. Which java version should be used
        UpdaterConfig updaterConfig = new UpdaterConfig();
        if (updaterConfig.java_updater.asBoolean()
                && (updaterConfig.java_updater_profile.asString().equals("AUTOMATIC"))) {
            try {
                if (updaterConfig.java_updater_build_id.asInt() == 0) // Throws nullpointer if value if empty
                    throw new Exception();
            } catch (Exception e) {
                throw new Exception("Java-Updater is enabled, but Java-Installation was not found! Enter '.check java' to install Java.");
            }
            String javaPath = config.server_start_command.asString();
            if (javaPath.contains(" -jar ")) { // jar file
                javaPath = new UtilsString().splitBySpacesAndQuotes(javaPath).get(0);

                FileManager fileManager = new FileManager();
                File jreFolder = new File(GD.WORKING_DIR + "/autoplug/system/jre");
                List<File> folders = fileManager.getFoldersFrom(jreFolder);
                if (folders.isEmpty())
                    throw new Exception("No Java-Installation was found in '" + jreFolder.getAbsolutePath() + "'!");
                File javaInstallationFolder = folders.get(0);
                File javaBinFolder = new File(javaInstallationFolder+"/bin");
                if(!javaBinFolder.exists()) {
                    AL.debug(Server.class, "Failed to find Java bin in: "+javaBinFolder);
                    javaBinFolder = new File(javaInstallationFolder + "/Contents/Home/bin"); // For macos support
                }
                if(!javaBinFolder.exists()) {
                    AL.debug(Server.class, "Failed to find Java bin in: "+javaBinFolder);
                    javaBinFolder = new File(javaInstallationFolder + "/Contents/HOME/bin"); // For macos support
                }
                if (!javaBinFolder.exists()){
                    AL.debug(Server.class, "Failed to find Java bin in: "+javaBinFolder);
                    throw new Exception("No Java 'bin' folder found inside of Java installation at path: '" + jreFolder.getAbsolutePath() + "'");
                }
                File javaFile = null;
                if (SystemUtils.IS_OS_WINDOWS) {
                    for (File file :
                            fileManager.getFilesFrom(javaBinFolder)) {
                        if (file.getName().equals("java.exe")) {
                            javaFile = file;
                            break;
                        }
                    }
                } else {
                    for (File file :
                            fileManager.getFilesFrom(javaBinFolder)) {
                        if (file.getName().equals("java")) {
                            javaFile = file;
                            break;
                        }
                    }
                }

                if (javaFile == null)
                    throw new Exception("No 'java' file found inside of Java installation at path: '" + javaBinFolder.getAbsolutePath() + "'");

                startCommand = startCommand.replace(javaPath, "\"" + javaFile.getAbsolutePath() + "\"");
            }
        }


        // The stuff below fixes https://github.com/Osiris-Team/AutoPlug-Client/issues/32
        // but messes input up, because there are 2 scanners on the same stream.
        // That's why we pause the current Terminal, which disables the user from entering console commands.
        // If AutoPlug-Plugin is installed the user can executed AutoPlug commands through in-game or console.
        List<String> commands = new UtilsString().splitBySpacesAndQuotes(startCommand);
        for (int i = 0; i < commands.size(); i++) {
            commands.set(i, commands.get(i).replaceAll("\"", "")); // Processbuilder does not support quotes
        }
        AL.debug(Server.class, "Starting server with commands: " + commands);
        //TERMINAL.pause(true);
        //startCommand = startCommand.replaceAll("\\\\", "/");
        ProcessBuilder processBuilder = new ProcessBuilder(commands); // The commands list contains all we need.
        processBuilder.redirectErrorStream(true);
        //processBuilder.inheritIO(); // BACK TO PIPED, BECAUSE OF MASSIVE ERRORS LIKE COMMANDS NOT BEEING EXECUTED, which affects the restarter
        processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        process = processBuilder.start();

        // Server OutputStream writes to our process InputStream, thus we can read its output:
        ASYNC_SERVER_IN = new AsyncInputStream(process.getInputStream());
        ASYNC_SERVER_IN.listeners.add(line -> {
            try {
                Ansi ansi = Ansi.ansi();
                if (colorServerLog) {
                    if (StringUtils.containsIgnoreCase(line, "error") ||
                            StringUtils.containsIgnoreCase(line, "critical") ||
                            StringUtils.containsIgnoreCase(line, "exception")) {
                        ansi.fgRed().a(line).reset();
                    } else if (StringUtils.containsIgnoreCase(line, "warn") ||
                            StringUtils.containsIgnoreCase(line, "warning")) {
                        ansi.fgYellow().a(line).reset();
                    } else if (StringUtils.containsIgnoreCase(line, "debug")) {
                        ansi.fgCyan().a(line).reset();
                    } else {
                        ansi.a(line).reset();
                    }
                } else {
                    ansi.a(line).reset();
                }
                System.out.println(ansi);
                ConAutoPlugConsoleSend.send(String.valueOf(ansi));
            } catch (Exception e) {
                AL.warn(e);
            }
        });

        // Also create a thread which checks if the server is running or not.
        if (threadServerAliveChecker == null) {
            threadServerAliveChecker = new Thread(() -> {
                try {
                    boolean lastIsRunningCheck = false;
                    boolean currentIsRunningCheck;
                    while (true) {
                        Thread.sleep(2000);
                        currentIsRunningCheck = Server.isRunning();
                        if (!currentIsRunningCheck && lastIsRunningCheck) {
                            AL.info("Server was stopped.");
                            if (new GeneralConfig().autoplug_auto_stop.asBoolean()) {
                                AL.info("Stopping AutoPlug too, since 'autoplug-stop' is enabled.");
                                System.exit(0);
                            } else {
                                AL.info("To stop AutoPlug too, enter '.stop both'.");
                            }

                            if (process.exitValue() != 0) {
                                if (isKill.get()) {
                                    isKill.set(false);
                                } else {
                                    AL.warn("Server crash was detected! Exit-Code should be 0, but is '" + process.exitValue() + "'!");
                                    if (new GeneralConfig().server_restart_on_crash.asBoolean()) {
                                        AL.info("Restart on crash is enabled, thus the server is restarting...");
                                        Server.start();
                                    }
                                }

                            }
                        }
                        lastIsRunningCheck = currentIsRunningCheck;
                    }
                } catch (Exception e) {
                    AL.error("Thread for checking if Server is alive was stopped due to an error.", e);
                }
            });
            threadServerAliveChecker.start();
        }
    }

    public static String getFileNameWithoutExt(String fileNameWithExt) throws NotLoadedException {
        return fileNameWithExt.replaceFirst("[.][^.]+$", ""); // Removes the file extension
    }


    private static boolean hasColorSupport(@NotNull String path) throws IOException {
        ZipFile zipFile = new ZipFile(path);
        FileHeader fileHeader = zipFile.getFileHeader("fileNameInZipToRemove");

        if (fileHeader == null) {
            // file does not exist
        }

        zipFile.removeFile(fileHeader);
        return false;
    }

    public static void submitCommand(@NotNull String command) throws IOException {
        if (isRunning()) {
            OutputStream os = process.getOutputStream();
            // Since the command won't be executed if it doesn't end with a new line char we do the below:
            if (command.contains(System.lineSeparator()))
                os.write(command.getBytes(StandardCharsets.UTF_8));//TERMINAL.writer().write(command);
            else
                os.write((command + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));//TERMINAL.writer().write(command + System.lineSeparator());
            os.flush();
        } else {
            if (command != null && !command.isEmpty()){
                AL.warn("Failed to submit command '" + command + "' because server is not running! Use '.start' to start the server.");
            } else {
                AL.warn("Server is not running! Use '.start' to start the server.");
            }
        }
    }

    public static String getMCVersion() throws Exception {
        String mcVersion = new GeneralConfig().server_version.asString();
        if (mcVersion == null) mcVersion = new UtilsMinecraft().getInstalledVersion();
        if (mcVersion == null) throw new NullPointerException(GD.errorMsgFailedToGetMCVersion());
        return mcVersion;
    }
}


