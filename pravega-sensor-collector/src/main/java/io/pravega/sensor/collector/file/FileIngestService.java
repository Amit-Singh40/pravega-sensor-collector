/**
 * Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.sensor.collector.file;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.sensor.collector.DeviceDriver;
import io.pravega.sensor.collector.DeviceDriverConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Ingestion service with common implementation logic for all files.
 */
public abstract class FileIngestService extends DeviceDriver {
    private static final Logger log = LoggerFactory.getLogger(FileIngestService.class);

    private static final String FILE_SPEC_KEY = "FILE_SPEC";
    private static final String FILE_EXT= "FILE_EXTENSION";
    private static final String DELETE_COMPLETED_FILES_KEY = "DELETE_COMPLETED_FILES";
    private static final String DATABASE_FILE_KEY = "DATABASE_FILE";
    private static final String EVENT_TEMPLATE_KEY = "EVENT_TEMPLATE";
    private static final String SAMPLES_PER_EVENT_KEY = "SAMPLES_PER_EVENT";
    private static final String INTERVAL_MS_KEY = "INTERVAL_MS";

    private static final String SCOPE_KEY = "SCOPE";
    private static final String STREAM_KEY = "STREAM";
    private static final String ROUTING_KEY_KEY = "ROUTING_KEY";
    private static final String EXACTLY_ONCE_KEY = "EXACTLY_ONCE";
    private static final String TRANSACTION_TIMEOUT_MINUTES_KEY = "TRANSACTION_TIMEOUT_MINUTES";

    private final FileProcessor processor;
    private final ScheduledExecutorService executor;

    private ScheduledFuture<?> watchFiletask;
    private ScheduledFuture<?> processFileTask;

    public FileIngestService(DeviceDriverConfig config) {
        super(config);
        final FileConfig fileSequenceConfig = new FileConfig(
                getDatabaseFileName(),
                getFileSpec(),
                getFileExtension(),
                getRoutingKey(),
                getStreamName(),
                getEventTemplate(),
                getSamplesPerEvent(),
                getDeleteCompletedFiles(),
                getExactlyOnce(),
                getTransactionTimeoutMinutes(),
                config.getClassName());
        log.info("File Ingest Config: {}", fileSequenceConfig);
        final String scopeName = getScopeName();
        log.info("Scope: {}", scopeName);
        createStream(scopeName, getStreamName());
        final EventStreamClientFactory clientFactory = getEventStreamClientFactory(scopeName);
        processor =FileProcessor.create(fileSequenceConfig, clientFactory);
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat(
                FileIngestService.class.getSimpleName() + "-" + config.getInstanceName() + "-%d").build();
        executor = Executors.newScheduledThreadPool(1, namedThreadFactory);
    }

    String getFileSpec() {
        return getProperty(FILE_SPEC_KEY);
    }
    String getFileExtension() {
        return getProperty(FILE_EXT, "");
    }

    boolean getDeleteCompletedFiles() {
        return Boolean.parseBoolean(getProperty(DELETE_COMPLETED_FILES_KEY, Boolean.toString(true)));
    }

    String getDatabaseFileName() {
        return getProperty(DATABASE_FILE_KEY);
    }

    String getEventTemplate() {
        return getProperty(EVENT_TEMPLATE_KEY, "{}");
    }

    int getSamplesPerEvent() {
        return Integer.parseInt(getProperty(SAMPLES_PER_EVENT_KEY, Integer.toString(100)));
    }

    long getIntervalMs() {
        return Long.parseLong(getProperty(INTERVAL_MS_KEY, Long.toString(10000)));
    }

    String getScopeName() {
        return getProperty(SCOPE_KEY);
    }

    String getStreamName() {
        return getProperty(STREAM_KEY);
    }

    protected String getRoutingKey() {
        return getProperty(ROUTING_KEY_KEY, "");
    }

    boolean getExactlyOnce() {
        return Boolean.parseBoolean(getProperty(EXACTLY_ONCE_KEY, Boolean.toString(true)));
    }

    /**
     * This time duration must not exceed the controller property controller.transaction.maxLeaseValue (milliseconds).
     */
    double getTransactionTimeoutMinutes() {
        return Double.parseDouble(getProperty(TRANSACTION_TIMEOUT_MINUTES_KEY, Double.toString(18.0 * 60.0)));
    }

    protected void watchFiles() {
        log.info("watchFiles: BEGIN");
        try {
            processor.watchFiles();
        } catch (Exception e) {
            log.error("watchFiles: watch file error", e);
            // Continue on any errors. We will retry on the next iteration.
        }
        log.info("watchFiles: END");
    }
    protected void processFiles() {
        log.trace("processFiles: BEGIN");
        try {
            processor.processFiles();
        } catch (Exception e) {
            log.error("processFiles: Process file error", e);
            // Continue on any errors. We will retry on the next iteration.
        }
        log.trace("processFiles: END");
    }

    @Override
    protected void doStart() {
        watchFiletask = executor.scheduleAtFixedRate(
                this::watchFiles,
                0,
                getIntervalMs(),
                TimeUnit.MILLISECONDS);
        /*
        Submits a periodic action that becomes enabled immediately  for the first time,
        and subsequently with the delay of 1 milliseconds between the termination of one execution and the commencement of the next
        ie immediately after completion of first action.
         */
        processFileTask = executor.scheduleWithFixedDelay(
                this::processFiles,
                0,
                1,
                TimeUnit.MILLISECONDS);
        notifyStarted();
    }

    @Override
    protected void doStop() {
        log.info("doStop: Cancelling ingestion task and process file task");
        watchFiletask.cancel(false);
        processFileTask.cancel(false);
    }
}