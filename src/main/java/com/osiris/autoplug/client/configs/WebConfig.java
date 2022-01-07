/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;
import com.osiris.dyml.exceptions.*;

import java.io.IOException;

public class WebConfig extends DreamYaml {

    public DYModule online_console;

    public DYModule send_plugins_updater_results;
    public DYModule send_server_updater_results;
    public DYModule send_self_updater_results;

    public DYModule send_public_details;
    public DYModule send_private_details;
    public DYModule send_server_status_ip;
    public DYModule file_manager;

    public WebConfig() throws NotLoadedException, DYWriterException, IOException, IllegalKeyException, DuplicateKeyException, DYReaderException, IllegalListException {
        this(ConfigPreset.DEFAULT);
    }

    public WebConfig(ConfigPreset preset) throws IOException, DuplicateKeyException, DYReaderException, IllegalListException, NotLoadedException, IllegalKeyException, DYWriterException {
        super(System.getProperty("user.dir") + "/autoplug/web-config.yml");
        lockFile();
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################",
                "    ___       __       ___  __",
                "   / _ |__ __/ /____  / _ \\/ /_ _____ _",
                "  / __ / // / __/ _ \\/ ___/ / // / _ `/",
                " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /",
                "                                /___/ Web-Config",
                "Thank you for using AutoPlug!",
                "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/",
                "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC",
                " ",
                "#######################################################################################################################",
                "Note: Changes to this file probably require you to enter '.con reload' to have affect.");

        online_console = put(name, "online-console").setDefValues("false")
                .setComments("Sends recent log messages (and future messages) to the Online-Console and can receive commands from it.",
                        "To have as little impact on your server as possible, this only happens when you are logged in.");
        if (preset.equals(ConfigPreset.FAST)) {
            online_console.setDefValues("true");
        }

        send_plugins_updater_results = put(name, "updater-results", "send-plugins-updaters-results").setDefValues("true")
                .setComments("Sends the plugins-updaters results to AutoPlug-Web.",
                        "By disabling this, you won't be able to see a summary of the updaters result online anymore.");
        send_server_updater_results = put(name, "updater-results", "send-server-updaters-results").setDefValues("true");
        send_self_updater_results = put(name, "updater-results", "send-self-updaters-results").setDefValues("true");


        send_public_details = put(name, "send-details", "public").setDefValues("true").setComments(
                "Sent information:",
                "- Server status (is it running/online or not)",
                "- Player count",
                "This connection stays always active.");
        send_private_details = put(name, "send-details", "private").setDefValues("true").setComments(
                "Sent information:",
                "- CPU maximum and current speeds",
                "- Memory maximum size and currently used size",
                "This connection is only active when logged in.");
        send_server_status_ip = put(name, "send-details", "ip").setDefValues("127.0.0.1").setComments(
                "The ip-address from where to retrieve server details, like MOTD, player count etc. The port gets automatically detected.");

        file_manager = put(name, "file-manager").setDefValues("true").setComments("Establishes the connection, once you are logged in to AutoPlug-Web.",
                "Enables you to manage this servers files from AutoPlugs' web panel.");

        save();
        unlockFile();
    }
}
