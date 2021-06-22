/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins.search;


import com.osiris.autoplug.client.tasks.updater.plugins.DetailedPlugin;
import com.osiris.autoplug.client.tasks.updater.plugins.search.bukkit.BukkitSearchById;
import com.osiris.autoplug.client.tasks.updater.plugins.search.spigot.SpigotSearchByAuthor;
import com.osiris.autoplug.client.tasks.updater.plugins.search.spigot.SpigotSearchById;
import com.osiris.autoplug.client.tasks.updater.plugins.search.spigot.SpigotSearchByName;

public class SearchMaster {

    /**
     * If the spigot/bukkit id is not given this type of search
     * based on the plugins name and author will be executed.
     */
    public synchronized SearchResult unknownSearch(DetailedPlugin plugin) {

        // Before passing over remove everything except numbers and dots
        plugin.setVersion(plugin.getVersion().replaceAll("[^0-9.]", ""));

        // Before passing over remove everything except words and numbers
        plugin.setAuthor(plugin.getAuthor().replaceAll("[^\\w]", ""));

        // Do spigot search by name
        SearchResult result_spigot = new SpigotSearchByName().search(plugin);

        if (result_spigot == null || result_spigot.getResultCode() == 2 || result_spigot.getResultCode() == 3) {
            //Couldn't find author or resource via first search
            //Do alternative search:
            return new SpigotSearchByAuthor().search(plugin);
        }

        return result_spigot;
    }

    public synchronized SearchResult searchBySpigotId(DetailedPlugin plugin) {
        return new SpigotSearchById().search(plugin);
    }

    public synchronized SearchResult searchByBukkitId(DetailedPlugin plugin) {
        return new BukkitSearchById().search(plugin);
    }

}
