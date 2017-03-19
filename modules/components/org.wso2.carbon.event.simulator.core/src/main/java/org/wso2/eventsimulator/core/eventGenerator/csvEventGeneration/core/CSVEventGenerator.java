/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.eventsimulator.core.eventGenerator.csvEventGeneration.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.eventsimulator.core.eventGenerator.EventGenerator;
import org.wso2.eventsimulator.core.eventGenerator.bean.CSVSimulationDto;
import org.wso2.eventsimulator.core.eventGenerator.bean.StreamConfigurationDto;
import org.wso2.eventsimulator.core.eventGenerator.csvEventGeneration.util.CSVReader;
import org.wso2.eventsimulator.core.eventGenerator.util.exceptions.EventGenerationException;
import org.wso2.eventsimulator.core.internal.EventSimulatorDataHolder;
import org.wso2.siddhi.core.event.Event;
import org.wso2.siddhi.query.api.definition.Attribute;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * CSVEventGenerator implements EventGenerator interface.
 * This class produces events using csv files
 */
public class CSVEventGenerator implements EventGenerator {
    private final Logger log = LoggerFactory.getLogger(CSVEventGenerator.class);
    private CSVSimulationDto csvConfiguration;
    private Long timestampStartTime;
    private Long timestampEndTime;
    private List<Attribute> streamAttributes;
    /**
     * nextEvent variable holds the next event with least timestamp
     */
    private Event nextEvent;
    private CSVReader csvReader;
    private List<Event> currentTimestampEvents = new ArrayList<Event>();
    private TreeMap<Long, ArrayList<Event>> eventsMap = new TreeMap<Long, ArrayList<Event>>();


    /**
     * constructor for CSVEventGenerator class.
     */
    public CSVEventGenerator() {
    }


    /**
     * init() method performs following actions
     * 1. Create a CSVSimulationDto object by parsing the csv simulation configuration
     * 2. Initialize a fileReader
     *
     * @param streamConfiguration stream configuration object containing configuration for csv simulation
     */
    @Override
    public void init(StreamConfigurationDto streamConfiguration) {

        csvConfiguration = (CSVSimulationDto) streamConfiguration;
        streamAttributes = EventSimulatorDataHolder.getInstance().getEventStreamService()
                .getStreamAttributes(csvConfiguration.getExecutionPlanName(), csvConfiguration.getStreamName());
        if (streamAttributes == null) {
            throw new EventGenerationException("Error occurred when generating events from CSV event generator" +
                    " for file '" + csvConfiguration.getFileName() + "' for stream '" + csvConfiguration.getStreamName()
                    + "'. Execution plan '" + csvConfiguration.getExecutionPlanName() + "' has not been deployed.");
        }
        csvReader = new CSVReader(csvConfiguration, streamAttributes, timestampStartTime, timestampEndTime);
        csvReader.initializeFileReader();

        if (log.isDebugEnabled()) {
            log.debug("Initialize CSV generator for file '" + csvConfiguration.getFileName() + "' to simulate stream " +
                    "'" + csvConfiguration.getStreamName() + "'.");
        }
    }


    /**
     * start() method begins event simulation by creating the first event
     */
    @Override
    public void start() {
        /*
        * if the CSV file is ordered by timestamp, create the first event and assign it as the nextEvent of
        * the generator.
        * else, create a treeMap of events. Retrieve the list of events with least timestamp as currentTimestampEvents
        * and assign the first event of the least timestamp as the nextEvent of the generator
        * */
        if (csvConfiguration.getIsOrdered()) {
            nextEvent = csvReader.getNextEvent();
        } else {
            eventsMap = csvReader.getEventsMap();
            if (!eventsMap.isEmpty()) {
                currentTimestampEvents = eventsMap.pollFirstEntry().getValue();
                nextEvent = currentTimestampEvents.get(0);
                currentTimestampEvents.remove(0);
            } else {
                throw new EventGenerationException("File '" + csvConfiguration.getFileName() + "' does not have data" +
                        " required to produce events.");
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Start CSV generator for file '" + csvConfiguration.getFileName() + "' for stream '" +
                    csvConfiguration.getStreamName() + "'.");
        }
    }


    /**
     * stop() method is used to release resources used to read CSV file
     */
    @Override
    public void stop() {
        csvReader.closeParser();
        if (log.isDebugEnabled()) {
            log.debug("Stop CSV generator for file '" + csvConfiguration.getFileName() + "' for stream '" +
                    csvConfiguration.getStreamName() + "'.");
        }
    }


    /**
     * poll() returns nextEvent of the generator and assign the event with next least timestamp as the nextEvent
     *
     * @return event with least timestamp
     */
    @Override
    public Event poll() {
        Event tempEvent = null;
//        try {
//
//        } catch (IndexOutOfBoundsException e) {
//            log.error("Error occurred when accessing next event : " + e.getMessage(), e);
//        }
        /*
            nextEvent != null implies that the generator may be able to produce more events. Hence, call getNextEvent()
            to obtain the next event.
            if nextEvent == null, return null to indicate that the generator will not be producing any more events
             */
        if (nextEvent != null) {
            tempEvent = nextEvent;
            getNextEvent();
        }
        return tempEvent;
    }


    /**
     * peek() method is used to view the nextEvent of the generator
     *
     * @return the event with least timestamp
     */
    @Override
    public Event peek() {
        return nextEvent;
    }


    /**
     * initTimestamp() is used to initialize the start time and end time for timestamps.
     * An even will be sent only if its timestamp falls within the boundaries of the timestamp start timestamp and
     * end time.
     * If we want to send all events with timestamp greater than the timestamp start time, the timestamp end time will
     * be set to null.
     */
    @Override
    public void initTimestamp(Long timestampStartTime, Long timestampEndTime) {
        this.timestampStartTime = timestampStartTime;
        this.timestampEndTime = timestampEndTime;

        if (log.isDebugEnabled()) {
            log.debug("Timestamp range initiated for random event generator for stream '" +
                    csvConfiguration.getStreamName() + "'. Timestamp start time : " + timestampStartTime + " and" +
                    " timestamp end time : " + timestampEndTime);
        }
    }


    /**
     * getStreamName() is used to obtain the name of the stream to which events are generated
     *
     * @return name of the stream
     */
    @Override
    public String getStreamName() {
        if (log.isDebugEnabled()) {
            log.debug("Get stream name from CSV generator for file '" + csvConfiguration.getFileName() + "'.");
        }
        return csvConfiguration.getStreamName();
    }


    /**
     * getExecutionPlanName() is used to obtain the name of execution plan which is being simulated
     *
     * @return name of the execution plan
     */
    @Override
    public String getExecutionPlanName() {
        return csvConfiguration.getExecutionPlanName();
    }


    /**
     * getNextEvent() is used to obtain the next event with least timestamp
     */
    @Override
    public void getNextEvent() {
        /*
         if the CSV file is ordered by timestamp, create next event and assign it as the nextEvent of generator
         else, assign the next event with current timestamp as nextEvent of generator
         */
        if (csvConfiguration.getIsOrdered()) {
            nextEvent = csvReader.getNextEvent();
        } else {
            getNextEventForCurrentTimestamp();
        }
    }


    /**
     * getEventsForNextTimestamp() is used to get list of events with the next least timestamp
     */
    private void getEventsForNextTimestamp() {
        if (log.isDebugEnabled()) {
            log.debug("Get events for next timestamp from CSV generator for file '" + csvConfiguration.getFileName()
                    + "' for stream '" + csvConfiguration.getStreamName() + "'.");
        }

        /*
        * if the events map is not empty, it implies that there are more event. Hence, retrieve the list of events with
        * the next least timestamp
        * else, there are no more events, hence list of events with current timestamp is set to null
        * */
        if (!eventsMap.isEmpty()) {
            currentTimestampEvents = eventsMap.pollFirstEntry().getValue();
        } else {
            currentTimestampEvents = null;
        }
    }


    /**
     * getNextEventForCurrentTimestamp() method is used to retrieve an event with the least timestamp
     */
    private void getNextEventForCurrentTimestamp() {
        if (log.isDebugEnabled()) {
            log.debug("Get next event for current timestamp from CSV generator for file '" +
                    csvConfiguration.getFileName() + "' for stream '" + csvConfiguration.getStreamName() + "'.");
        }

        /*
         * if currentTimestampEvents != null , it implies that more events will be created by the generator
         * if currentTimestampEvents list is not empty, get the next event in list as nextEvent and remove that even
         * from the list.
         * else, call getEventsForNextTimestamp() to retrieve a list of events with the next least timestamp.
         * if currentTimestampEvents != null after the method call, it implies that more events will be generated.
         * assign the first event in list as nextEvent and remove it from the list.
         * else if currentTimestampEvents == null, it implies that no more events will be created, hence assign null
         * to nextEvent.
         * */
        if (currentTimestampEvents != null) {
            if (!currentTimestampEvents.isEmpty()) {
                nextEvent = currentTimestampEvents.get(0);
                currentTimestampEvents.remove(0);
            } else {
                getEventsForNextTimestamp();
                if (currentTimestampEvents != null) {
                    nextEvent = currentTimestampEvents.get(0);
                    currentTimestampEvents.remove(0);
                } else {
                    nextEvent = null;
                }
            }
        }
    }
}