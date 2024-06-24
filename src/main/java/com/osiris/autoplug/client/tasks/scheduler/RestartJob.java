/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.scheduler;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.RestarterConfig;
import com.osiris.dyml.YamlSection;
import com.osiris.jlib.logger.AL;
import org.jetbrains.annotations.NotNull;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RestartJob implements Job {

    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {

            if (!Server.isRunning())
                throw new Exception("Server is not running. Restart not possible.");

            // Before restarting execute commands
            RestarterConfig config = new RestarterConfig();
            List<YamlSection> modules = config.restarter_commands.getChildSections();

            // Sort the stuff
            List<Integer> keysAsIntsList = new ArrayList<>();
            for (YamlSection m :
                    modules) {
                keysAsIntsList.add(Integer.parseInt(m.getLastKey()));
            }

            // Normally sorts from lowest, to highest value.
            // But we want it the other way:
            Integer[] keysAsIntsArray = keysAsIntsList.toArray(new Integer[0]);
            Arrays.sort(keysAsIntsArray, Collections.reverseOrder());

            AL.info("Executing scheduled restart in " + keysAsIntsArray[0] + "sec(s)...");
            for (int i = keysAsIntsArray[0]; i >= 0; i--) { // The first int, has the highest value, bc of the sorting
                for (YamlSection m :
                        modules) {
                    if (Integer.parseInt(m.getLastKey()) == i) {
                        for (String command : m.asStringList()) {
                            try {
                                if (command == null)
                                    AL.debug(this.getClass(), "Command for second '" + i + "' is null.");
                                else
                                    Server.submitCommand(command);
                            } catch (Exception e) {
                                AL.warn(e, "Error executing '" + command + "' command!");
                            }
                        }
                    }
                }
                Thread.sleep(1000);
            }

            //Restart the server
            Server.restart();

        } catch (@NotNull Exception e) {
            AL.warn("Error while executing restart!", e);
        }

    }

}
