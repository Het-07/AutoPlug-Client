/*
 * Copyright (c) 2022-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils.tasks;

import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BThreadPrinter;

import java.util.concurrent.atomic.AtomicReference;


public class MyBThreadManager {
    public static final AtomicReference<BThreadPrinter> lastCreatedPrinter = new AtomicReference<>();
    public BThreadManager manager;
    public BThreadPrinter printer;

    public MyBThreadManager(BThreadManager manager, BThreadPrinter printer) {
        this.manager = manager;
        this.printer = printer;
        lastCreatedPrinter.set(printer);
    }

}
